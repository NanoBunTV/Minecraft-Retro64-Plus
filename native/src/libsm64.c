#ifndef SM64_LIB_EXPORT
    #define SM64_LIB_EXPORT
#endif

// Retro64 Plus: 6 = upstream's 5 without native audio output, plus sm64_audio_tick; 7 = Mario-only models, bones,
// recorded and positional sounds (native/UPSTREAM.md); 10 = grabbing and holding an object (M4).
#define LIB_VER 11

#include "libsm64.h"

#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <math.h>
#include <pthread.h>

#include "decomp/include/PR/os_cont.h"
#include "decomp/engine/math_util.h"
#include "decomp/include/sm64.h"
#include "decomp/shim.h"
#include "decomp/memory.h"
#include "decomp/global_state.h"
#include "decomp/game/mario.h"
#include "decomp/game/interaction.h"
#include "decomp/game/object_stuff.h"
#include "decomp/engine/surface_collision.h"
#include "decomp/engine/graph_node.h"
#include "decomp/engine/geo_layout.h"
#include "decomp/game/rendering_graph_node.h"
#include "decomp/mario/geo.inc.h"
#include "decomp/game/platform_displacement.h"

#include "debug_print.h"
#include "load_surfaces.h"
#include "gfx_adapter.h"
#include "load_anim_data.h"
#include "load_tex_data.h"
#include "obj_pool.h"
#include "fake_interaction.h" // Retro64 Plus: was implicitly declared
#include "model_handler.h"
#include "fpsLimitHelper.h"
#include "decomp/audio/external.h"

#include "decomp/game/config.h"

#include "decomp/audio/load_dat.h"
#include "decomp/tools/convUtils.h"
#include "decomp/mario/geo.inc.h"

static struct AllocOnlyPool *s_mario_geo_pool = NULL;

static s16 lastWedges = 8;
//static struct GraphNode *s_mario_graph_node = NULL;
static int currentModel=0;

static bool s_init_global = false;
static bool s_init_one_mario = false;
struct MarioInstance
{
    struct GlobalState *globalState;
};
struct ObjPool s_mario_instance_pool = { 0, 0 };

static void update_button( bool on, u16 button )
{
    gController.buttonPressed &= ~button;

    if( on )
    {
        if(( gController.buttonDown & button ) == 0 )
            gController.buttonPressed |= button;

        gController.buttonDown |= button;
    }
    else 
    {
        gController.buttonDown &= ~button;
    }
}

static struct Area *allocate_area( void )
{
    struct Area *result = malloc( sizeof( struct Area ));
    memset( result, 0, sizeof( struct Area ));

    result->flags = 1;
    result->camera = malloc( sizeof( struct Camera ));
    memset( result->camera, 0, sizeof( struct Camera ));

    return result;
}

static void free_area( struct Area *area )
{
    free( area->camera );
    free( area );
}

// Retro64 Plus: getModel() falls back to Mario for an unknown model, but the texture tables (load_tex_data.c) return
// NULL for one, so a bad model number crashed. Every store of currentModel goes through this (native/UPSTREAM.md).
static int valid_model( int model ) { return model >= 0 && model < ModelsUsed ? model : 0; }

int getCurrentModel(){
    return currentModel;
}

SM64_LIB_FN void sm64_global_init( uint8_t *rom, uint8_t *outTexture, SM64DebugPrintFunctionPtr debugPrintFunction )
{
    uint8_t * rom2 = malloc(0x800000);
    memcpy(rom2,rom,0x800000);
    rom = rom2;
    gSoundDataADSR= parse_seqfile(rom+5748512); // CTL
    gSoundDataRaw= parse_seqfile(rom+5846368); // TBL
    gMusicData=parse_seqfile(rom+8063072); // SEQ
    gBankSetsData=rom+0x7CC621; // BIN
    // shift the next 90 elements after gBankSetsData+0x45 forward by 1
    memmove(gBankSetsData+0x45,gBankSetsData+0x45-1,0x5B);
    gBankSetsData[0x45]=0x00;
    ptrs_to_offsets(gSoundDataADSR);
    initMarioGeo(rom);
    if( s_init_global )
        sm64_global_terminate();
    s_init_global = true;
    g_debug_print_func = debugPrintFunction;

    load_mario_textures_from_rom( rom, outTexture );
    load_mario_anims_from_rom( rom );

    memory_init();

    // Retro64 Plus (D21): no audio thread or system audio output. The mod pulls samples with sm64_audio_tick, on the
    // same thread as everything else, and plays them through Minecraft's sound engine (native/UPSTREAM.md).
    audio_init();
    sound_init();
    sound_reset(0);
}

SM64_LIB_FN void sm64_global_terminate( void )
{
    if( !s_init_global ) return;

    global_state_bind( NULL );
    
    if( s_init_one_mario )
    {
        for( int i = 0; i < s_mario_instance_pool.size; ++i )
            if( s_mario_instance_pool.objects[i] != NULL )
                sm64_mChar_delete( i );

        obj_pool_free_all( &s_mario_instance_pool );
    }

    s_init_global = false;
    s_init_one_mario = false;
       
	ctl_free();
    alloc_only_pool_free( s_mario_geo_pool );
    surfaces_unload_all();
    unload_mario_anims();
    memory_terminate();
}

SM64_LIB_FN void sm64_static_surfaces_load( const struct SM64Surface *surfaceArray, uint32_t numSurfaces )
{
    surfaces_load_static( surfaceArray, numSurfaces );

    // Retro64 Plus: loading frees the previous surface list, which every character's floor, ceiling and wall
    // pointers still point into (a use-after-free that crashed the next tick). Look them up again in the new list.
    for( uint32_t i = 0; i < s_mario_instance_pool.size; i++ )
    {
        if( s_mario_instance_pool.objects[i] == NULL ) continue;
        global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[i])->globalState );
        gMarioState->wall = NULL;
        gMarioState->floorHeight = find_floor( gMarioState->pos[0], gMarioState->pos[1], gMarioState->pos[2], &gMarioState->floor );
        gMarioState->ceilHeight = vec3f_find_ceil( gMarioState->pos, gMarioState->floorHeight, &gMarioState->ceil );
    }
}

static int32_t create_character( float x, float y, float z, bool requireFloor );

SM64_LIB_FN int32_t sm64_mChar_create( float x, float y, float z )
{
    return create_character( x, y, z, true );
}

// Retro64 Plus: a character that is only ever drawn with sm64_mChar_render_pose (another player's Mario, M1
// checkpoint 3). It never runs physics, so it doesn't need a floor under it (native/UPSTREAM.md).
SM64_LIB_FN int32_t sm64_mChar_create_puppet( void )
{
    return create_character( 0, 0, 0, false );
}

static int32_t create_character( float x, float y, float z, bool requireFloor )
{
    int32_t marioIndex = obj_pool_alloc_index( &s_mario_instance_pool, sizeof( struct MarioInstance ));
    struct MarioInstance *newInstance = s_mario_instance_pool.objects[marioIndex];

    newInstance->globalState = global_state_create();
    global_state_bind( newInstance->globalState );

    if( !s_init_one_mario )
    {
        s_init_one_mario = true;
        s_mario_geo_pool = alloc_only_pool_init();
        initModels(s_mario_geo_pool);
    }

    gCurrSaveFileNum = 1;
    gMarioObject = hack_allocate_mario();
    gCurrentArea = allocate_area();
    gCurrentObject = gMarioObject;

    gMarioSpawnInfoVal.startPos[0] = x;
    gMarioSpawnInfoVal.startPos[1] = y;
    gMarioSpawnInfoVal.startPos[2] = z;

    gMarioSpawnInfoVal.startAngle[0] = 0;
    gMarioSpawnInfoVal.startAngle[1] = 0;
    gMarioSpawnInfoVal.startAngle[2] = 0;

    gMarioSpawnInfoVal.areaIndex = 0;
    gMarioSpawnInfoVal.activeAreaIndex = 0;
    gMarioSpawnInfoVal.behaviorArg = 0;
    gMarioSpawnInfoVal.behaviorScript = NULL;
    gMarioSpawnInfoVal.unk18 = NULL;
    gMarioSpawnInfoVal.next = NULL;

    init_mario_from_save_file();

    // init_mario fails only for want of a floor; a puppet is fully set up regardless.
    if( init_mario() < 0 && requireFloor )
    {
        sm64_mChar_delete( marioIndex );
        return -1;
    }

    set_mario_action( gMarioState, ACT_SPAWN_SPIN_AIRBORNE, 0);
    find_floor( x, y, z, &gMarioState->floor );

    return marioIndex;
}

// Retro64 Plus (M1 checkpoint 3): everything Mario's rendering reads, so another client can draw him in the same
// pose with sm64_mChar_render_pose. The position is the drawn position relative to Mario's own, so a remote client
// can draw him at its own idea of where the player is.
SM64_LIB_FN void sm64_mChar_get_pose( int32_t marioId, struct SM64MarioPose *outPose )
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL ) return;
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    struct GraphNodeObject *gfx = &gMarioState->marioObj->header.gfx;
    struct MarioBodyState *body = gMarioState->marioBodyState;
    for( int i = 0; i < 3; i++ )
    {
        outPose->offset[i] = gfx->pos[i] - gMarioState->pos[i];
        outPose->angle[i] = gfx->angle[i];
        outPose->torsoAngle[i] = body->torsoAngle[i];
        outPose->headAngle[i] = body->headAngle[i];
    }
    outPose->action = body->action;
    outPose->animID = gfx->animInfo.animID;
    outPose->animYTrans = gfx->animInfo.animYTrans;
    outPose->animFrame = gfx->animInfo.animFrame;
    outPose->modelState = body->modelState;
    outPose->capState = body->capState;
    outPose->eyeState = body->eyeState;
    outPose->handState = body->handState;
    outPose->wingFlutter = body->wingFlutter;
    outPose->grabPos = body->grabPos;
    outPose->punchState = body->punchState;
}

static int16_t clamp_s16( int32_t v, int32_t lo, int32_t hi ) { return (int16_t)( v < lo ? lo : v > hi ? hi : v ); }
static int8_t clamp_s8( int32_t v, int32_t lo, int32_t hi ) { return (int8_t)( v < lo ? lo : v > hi ? hi : v ); }

// Retro64 Plus (M1 checkpoint 3): draws a puppet (sm64_mChar_create_puppet) in a pose from sm64_mChar_get_pose,
// holding exactly that animation frame, around the origin plus the pose's offset. The pose comes from the network,
// so every index into the game's data is clamped first: a bad pose must never crash the client drawing it.
SM64_LIB_FN void sm64_mChar_render_pose( int32_t marioId, const struct SM64MarioPose *pose, int32_t model, struct SM64MarioGeometryBuffers *outBuffers )
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL ) return;
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    struct GraphNodeObject *gfx = &gMarioState->marioObj->header.gfx;
    struct MarioBodyState *body = gMarioState->marioBodyState;
    for( int i = 0; i < 3; i++ )
    {
        float offset = pose->offset[i];
        gfx->pos[i] = offset == offset && offset > -500.0f && offset < 500.0f ? offset : 0.0f; // NaN or absurd: 0
        gfx->angle[i] = pose->angle[i];
        body->torsoAngle[i] = pose->torsoAngle[i];
        body->headAngle[i] = pose->headAngle[i];
    }
    body->action = pose->action;
    // No SM64 transparency (MODEL_STATE_ALPHA and its alpha byte) for puppets: that path moves Mario's display lists
    // to another layer, which the libsm64 master-list hack in rendering_graph_node.c mishandles (it then draws an
    // uninitialized entry). The mod draws the vanish cap's see-through look itself (native/UPSTREAM.md).
    body->modelState = pose->modelState & ~0x1FF;
    body->capState = clamp_s8( pose->capState, 0, 3 );
    body->eyeState = clamp_s8( pose->eyeState, 0, 8 );
    body->handState = clamp_s8( pose->handState, 0, 5 );
    body->wingFlutter = clamp_s8( pose->wingFlutter, 0, 1 );
    body->grabPos = clamp_s8( pose->grabPos, 0, 2 );
    // Type in the top two bits, timer below; the hand/foot scaler indexes a 3 × 6 table with the timer (0..5).
    uint8_t punchTimer = pose->punchState & 0x3F;
    body->punchState = (uint8_t)( ( pose->punchState & 0xC0 ) | ( punchTimer > 5 ? 5 : punchTimer ) );

    // An unknown animation falls back to the first: skipping it would leave a fresh puppet with no animation at all.
    int32_t animCount = (int32_t)mario_anim_count();
    int32_t animID = pose->animID >= 0 && pose->animID < animCount ? pose->animID : 0;
    if( animCount > 0 )
    {
        set_mario_animation( gMarioState, animID );
        struct Animation *anim = gfx->animInfo.curAnim;
        int16_t frame = clamp_s16( pose->animFrame, 0, anim != NULL && anim->loopEnd > 0 ? anim->loopEnd - 1 : 0 );
        gfx->animInfo.animFrame = frame;
        gfx->animInfo.animFrameAccelAssist = (int32_t)frame << 16;
        gfx->animInfo.animAccel = 0;
        gfx->animInfo.animYTrans = pose->animYTrans;
        gfx->animInfo.animTimer = gAreaUpdateCounter; // hold this frame: geo_update_animation_frame won't advance it
    }

    currentModel = valid_model( model );
    gfx_adapter_bind_output_buffers( outBuffers );
    geo_process_root_hack_single_node( getModel( currentModel ) );
    gAreaUpdateCounter++;
}

SM64_LIB_FN struct AnimInfo* sm64_get_anim_info(int32_t marioId,int16_t rot[3]){
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to get anim for non-existant Mario with ID: %u", marioId);
        return NULL;
    }
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );
    (rot)[0]=gMarioState->marioObj->header.gfx.angle[0];
    (rot)[1]=gMarioState->marioObj->header.gfx.angle[1];
    (rot)[2]=gMarioState->marioObj->header.gfx.angle[2];
    return &gMarioState->marioObj->header.gfx.animInfo;
}

SM64_LIB_FN void sm64_mChar_animTick(int32_t marioId, uint32_t stateFlags,struct AnimInfo* info,struct SM64MarioGeometryBuffers *outBuffers,int32_t model,int16_t rot[3]){
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to tick non-existant Mario with ID: %u", marioId);
        return;
    }
    
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    gMarioState->marioObj->header.gfx.angle[0]=rot[0];
    gMarioState->marioObj->header.gfx.angle[1]=rot[1];
    gMarioState->marioObj->header.gfx.angle[2]=rot[2];

    // Retro64 Plus: no wall-clock frame limiter here; the mod calls this at a fixed 30 Hz (native/UPSTREAM.md).

    gMarioState->flags = stateFlags;
    if (gMarioState->marioObj->header.gfx.animInfo.animFrame!=info->animID && info->animID!=-1)
        set_mario_anim_with_accel( gMarioState, info->animID,info->animAccel );
    gMarioState->marioObj->header.gfx.animInfo.animAccel=info->animAccel;

    currentModel = valid_model( model );
    gfx_adapter_bind_output_buffers( outBuffers );
    geo_process_root_hack_single_node( getModel(currentModel) );
    gAreaUpdateCounter++;
}

// Retro64 Plus (D32): the sounds Mario's own code played during the last sm64_mChar_tick (not global ones such as the
// power meter's), so the mod can send them to other players, who replay them with sm64_sound_source_play.
#define SM64_MAX_TICK_SOUNDS 16
static int32_t s_tickSounds[SM64_MAX_TICK_SOUNDS];
static uint32_t s_tickSoundCount = 0;

static void record_tick_sound( s32 soundBits, f32 *pos )
{
    if( pos != gGlobalSoundSource && s_tickSoundCount < SM64_MAX_TICK_SOUNDS )
        s_tickSounds[s_tickSoundCount++] = soundBits;
}

SM64_LIB_FN uint32_t sm64_mChar_last_tick_sounds( int32_t *outSounds, uint32_t maxSounds )
{
    uint32_t n = s_tickSoundCount < maxSounds ? s_tickSoundCount : maxSounds;
    memcpy( outSounds, s_tickSounds, n * sizeof( int32_t ));
    s_tickSoundCount = 0; // Retro64 Plus (M2): read once, so sounds of sm64_mChar_knockback join the next frame's
    return n;
}

// Retro64 Plus (M4): Mario picks up and carries an object Minecraft owns (a Bob-omb). SM64's hold and throw actions
// only need *some* object in usedObj / heldObj (they write its position when it's let go), so this one stands in;
// Minecraft moves the real entity. Only on the ground and out of water, not while already holding.
static struct Object s_grabbedObject;

static struct MarioState *bind_mario( int32_t marioId )
{
    if( marioId < 0 || marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
        return NULL;
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );
    return gMarioState;
}

SM64_LIB_FN int32_t sm64_mChar_grab( int32_t marioId )
{
    struct MarioState *m = bind_mario( marioId );
    if( m == NULL || m->heldObj != NULL || m->action == ACT_PICKING_UP ) return 0;
    // Retro64 Plus (M6 review): not under water with the Metal cap either: picking up there plunges him to the surface.
    if( m->action & (ACT_FLAG_AIR | ACT_FLAG_SWIMMING | ACT_FLAG_METAL_WATER) ) return 0;
    memset( &s_grabbedObject, 0, sizeof( s_grabbedObject ));
    m->interactObj = &s_grabbedObject;
    m->usedObj = &s_grabbedObject;
    set_mario_action( m, ACT_PICKING_UP, 0 );
    return 1;
}

// 1 while Mario holds the object (or is picking it up), 0 once it's thrown or dropped.
SM64_LIB_FN int32_t sm64_mChar_is_holding( int32_t marioId )
{
    struct MarioState *m = bind_mario( marioId );
    return m != NULL && (m->heldObj != NULL || m->action == ACT_PICKING_UP);
}

static int is_hold_action( u32 action )
{
    switch( action )
    {
        case ACT_PICKING_UP: case ACT_HOLD_PANTING_UNUSED: case ACT_HOLD_IDLE: case ACT_HOLD_HEAVY_IDLE:
        case ACT_HOLD_JUMP_LAND_STOP: case ACT_HOLD_FREEFALL_LAND_STOP: case ACT_HOLD_BUTT_SLIDE_STOP:
        case ACT_HOLD_WALKING: case ACT_HOLD_HEAVY_WALKING: case ACT_HOLD_DECELERATING: case ACT_HOLD_BEGIN_SLIDING:
        case ACT_HOLD_BUTT_SLIDE: case ACT_HOLD_STOMACH_SLIDE: case ACT_HOLD_JUMP_LAND: case ACT_HOLD_FREEFALL_LAND:
        case ACT_HOLD_QUICKSAND_JUMP_LAND: case ACT_HOLD_JUMP: case ACT_HOLD_FREEFALL: case ACT_HOLD_BUTT_SLIDE_AIR:
        case ACT_HOLD_WATER_JUMP: case ACT_HOLD_WATER_IDLE: case ACT_HOLD_WATER_ACTION_END: case ACT_HOLD_BREASTSTROKE:
        case ACT_HOLD_SWIMMING_END: case ACT_HOLD_FLUTTER_KICK: case ACT_HOLD_METAL_WATER_STANDING:
        case ACT_HOLD_METAL_WATER_WALKING: case ACT_HOLD_METAL_WATER_FALLING: case ACT_HOLD_METAL_WATER_FALL_LAND:
        case ACT_HOLD_METAL_WATER_JUMP: case ACT_HOLD_METAL_WATER_JUMP_LAND:
            return 1;
        default:
            return 0;
    }
}

// Lets go of the object without throwing it (it exploded, or the server said no): out of a hold action into the
// matching empty-handed one.
SM64_LIB_FN void sm64_mChar_drop( int32_t marioId )
{
    struct MarioState *m = bind_mario( marioId );
    if( m == NULL ) return;
    int holding = is_hold_action( m->action );
    mario_drop_held_object( m );
    if( !holding ) return;
    if( m->action & ACT_FLAG_SWIMMING ) set_mario_action( m, ACT_WATER_IDLE, 0 );
    else if( m->action & ACT_FLAG_AIR ) set_mario_action( m, ACT_FREEFALL, 0 );
    // Retro64 Plus (M6 review): ACT_IDLE under water would plunge Metal Mario up to the surface, through blocks.
    else if( m->action & ACT_FLAG_METAL_WATER ) set_mario_action( m, ACT_METAL_WATER_STANDING, 0 );
    else set_mario_action( m, ACT_IDLE, 0 );
}

// Retro64 Plus (M4 checkpoint 3): Mario gets on a Koopa shell Minecraft owns, as interact_koopa_shell does (it drops
// whatever he holds). The ride's actions only need *some* object in riddenObj (mario_stop_riding_object marks it when
// the ride ends), so this one stands in. Not while swimming (SM64's water shell is a different thing).
static struct Object s_riddenObject;

SM64_LIB_FN int32_t sm64_mChar_ride_shell( int32_t marioId )
{
    struct MarioState *m = bind_mario( marioId );
    if( m == NULL || (m->action & (ACT_FLAG_RIDING_SHELL | ACT_FLAG_SWIMMING)) ) return 0;
    memset( &s_riddenObject, 0, sizeof( s_riddenObject ));
    m->interactObj = &s_riddenObject;
    m->usedObj = &s_riddenObject;
    m->riddenObj = &s_riddenObject;
    mario_drop_held_object( m );
    set_mario_action( m, ACT_RIDING_SHELL_GROUND, 0 );
    return 1;
}

// 1 while Mario rides the shell. A ride ended by any other way into a non-riding action (the caller setting one, a
// hit) ends it here too, so the next ride starts clean.
SM64_LIB_FN int32_t sm64_mChar_is_riding( int32_t marioId )
{
    struct MarioState *m = bind_mario( marioId );
    if( m == NULL ) return 0;
    if( m->riddenObj != NULL && !(m->action & ACT_FLAG_RIDING_SHELL) ) m->riddenObj = NULL;
    return m->riddenObj != NULL;
}

// Retro64 Plus (D28): the bones of the character drawn last (by sm64_mChar_tick or sm64_mChar_render_pose), as
// SM64 4x4 matrices (16 floats: rows 0-2 the axes, row 3 the origin) in drawing order, in the same space as the
// triangles. Returns how many were written.
SM64_LIB_FN uint32_t sm64_last_render_bones( float *outMatrices, uint32_t maxBones )
{
    return gfx_adapter_get_bones( outMatrices, maxBones );
}

// Retro64 Plus (D32): positions SM64's sound engine reads sounds from. It keeps a pointer to the position for as long
// as a sound plays, so these live here rather than in the caller's memory. Positions are relative to the listener,
// in its view space (x right, y up, -z forward), in SM64 units.
#define SM64_SOUND_SOURCES 64
static f32 s_soundSources[SM64_SOUND_SOURCES][3];

static f32 sane_sound_coord( float v ) { return v == v && v > -100000.0f && v < 100000.0f ? v : 100000.0f; }

SM64_LIB_FN void sm64_sound_source_move( uint32_t source, float x, float y, float z )
{
    if( source >= SM64_SOUND_SOURCES ) return;
    s_soundSources[source][0] = sane_sound_coord( x );
    s_soundSources[source][1] = sane_sound_coord( y );
    s_soundSources[source][2] = sane_sound_coord( z );
}

// Sound bits come from the network: the bank indexes 10-entry tables unchecked in the sound engine, so it's checked
// here (the sound id is checked by the engine itself).
SM64_LIB_FN void sm64_sound_source_play( uint32_t source, int32_t soundBits )
{
    if( !s_init_global || source >= SM64_SOUND_SOURCES ) return;
    if( (((uint32_t)soundBits >> 28) & 0xF) >= 10 ) return;
    play_sound( soundBits, s_soundSources[source] );
}

SM64_LIB_FN void sm64_mChar_tick( int32_t marioId, const struct SM64MarioInputs *inputs, struct SM64MarioState *outState, struct SM64MarioGeometryBuffers *outBuffers )
{
    
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to tick non-existant Mario with ID: %u", marioId);
        return;
    }

    currentModel = valid_model( outState->currentModel );
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );
    gMarioState->marioObj->header.gfx.cameraToObject[0]=0;
    gMarioState->marioObj->header.gfx.cameraToObject[1]=0;
    gMarioState->marioObj->header.gfx.cameraToObject[2]=0;
    // Retro64 Plus: no wall-clock frame limiter here; the mod calls this at a fixed 30 Hz (native/UPSTREAM.md).

    update_button( inputs->buttonA, A_BUTTON );
    update_button( inputs->buttonB, B_BUTTON );
    update_button( inputs->buttonZ, Z_TRIG );

    gMarioState->area->camera->yaw = atan2s( inputs->camLookZ, inputs->camLookX );

    gController.stickX = -64.0f * inputs->stickX;
    gController.stickY = 64.0f * inputs->stickY;
    gController.stickMag = sqrtf( gController.stickX*gController.stickX + gController.stickY*gController.stickY );

    gPlaySoundHook = record_tick_sound;
    apply_mario_platform_displacement();
    bhv_mario_update();
    update_mario_platform(); // TODO platform grabbed here and used next tick could be a use-after-free
    gPlaySoundHook = NULL;

    gfx_adapter_bind_output_buffers( outBuffers );

    geo_process_root_hack_single_node( getModel(currentModel) );

    gAreaUpdateCounter++;

    outState->health = gMarioState->health;
    vec3f_copy( outState->position, gMarioState->pos );
    vec3f_copy( outState->velocity, gMarioState->vel );
    outState->faceAngle = (float)gMarioState->faceAngle[1] / 32768.0f * 3.14159f;
    outState->flags = gMarioState->flags;
    outState->action = gMarioState->action;

    s16 numHealthWedges = gMarioState->health > 0 ? gMarioState->health >> 8 : 0;
    if (numHealthWedges > lastWedges) {
            play_sound(SOUND_MENU_POWER_METER, gGlobalSoundSource);
        }
        lastWedges = numHealthWedges;

}

SM64_LIB_FN void sm64_mChar_delete( int32_t marioId )
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to delete non-existant Mario with ID: %u", marioId);
        return;
    }

    struct GlobalState *globalState = ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState;
    global_state_bind( globalState );

    // Retro64 Plus (M6 review): his sounds play from his position, and the sound engine keeps that pointer (playing,
    // and queued) until they end: stop and drop them before it's freed, or the next audio tick reads freed memory.
    stop_sounds_from_source( gMarioObject->header.gfx.cameraToObject );
    forget_sound_requests_from_source( gMarioObject->header.gfx.cameraToObject );

    free( gMarioObject );
    free_area( gCurrentArea );

    global_state_delete( globalState );
    obj_pool_free_index( &s_mario_instance_pool, marioId );
}

SM64_LIB_FN void sm64_mChar_teleport( int32_t marioId, float x, float y, float z )
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to change position of non-existant Mario with ID: %u", marioId);
        return;
    }
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    gMarioState->pos[0]=x;
    gMarioState->pos[1]=y;
    gMarioState->pos[2]=z;
    gMarioState->marioObj->header.gfx.pos[0]=x;
    gMarioState->marioObj->header.gfx.pos[1]=y;
    gMarioState->marioObj->header.gfx.pos[2]=z;
}

SM64_LIB_FN void sm64_mChar_set_velocity( int32_t marioId, float x, float y, float z )
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to change position of non-existant Mario with ID: %u", marioId);
        return;
    }
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    gMarioState->vel[0]=x;
    gMarioState->vel[1]=y;
    gMarioState->vel[2]=z;
}

SM64_LIB_FN void sm64_mChar_apply_damage( int32_t marioId, uint32_t damage,uint32_t interactionSubtype,float xSrc,float ySrc,float zSrc)
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to apply damage to non-existant Mario with ID: %u", marioId);
        return;
    }
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    fake_damage_knock_back(gMarioState,damage,interactionSubtype,xSrc,ySrc,zSrc);
}

// Retro64 Plus (D17, D44): the mod owns the caps, each with its own timer (the server's). Sets which special caps
// (MARIO_VANISH_CAP | MARIO_METAL_CAP | MARIO_WING_CAP) Mario wears, and which of them to leave out of his drawing this
// frame (the end-of-timer flicker). SM64's single capTimer is kept at 0 so it never takes a cap off itself.
extern u32 gRetro64HiddenCaps;

SM64_LIB_FN void sm64_mChar_set_caps( int32_t marioId, uint32_t caps, uint32_t hiddenCaps )
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to set caps of non-existant Mario with ID: %u", marioId);
        return;
    }
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    caps &= MARIO_SPECIAL_CAPS;
    gMarioState->flags = ( gMarioState->flags & ~MARIO_SPECIAL_CAPS ) | caps;
    if( caps ) gMarioState->flags |= MARIO_CAP_ON_HEAD;
    gMarioState->capTimer = 0;
    gRetro64HiddenCaps = hiddenCaps & MARIO_SPECIAL_CAPS;
}

// Retro64 Plus (D36): Minecraft owns Mario's health. Sets it (0x880 full, 0x100 per power meter segment; below
// 0x100 Mario dies) and returns the damage SM64's own code dealt since the last call (falls, lava), in the same units,
// which the mod hands to the server. update_mario_health no longer applies hurtCounter itself.
SM64_LIB_FN int32_t sm64_mChar_set_health( int32_t marioId, int16_t health )
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to set health of non-existant Mario with ID: %u", marioId);
        return 0;
    }
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    int32_t hurt = gMarioState->hurtCounter * 0x40;
    gMarioState->hurtCounter = 0;
    gMarioState->healCounter = 0;
    gMarioState->health = health < 0 ? 0 : (health > 0x880 ? 0x880 : health);
    return hurt;
}

// Retro64 Plus (D36): Minecraft damaged Mario. With a source position, SM64's knockback (soft, normal or hard by
// damage in segments) away from it, with its voice (neither when the hit does nothing, as with the Metal or Vanish
// cap); without one, just his "attacked" voice. Never touches health (Minecraft owns it); its sounds are recorded for
// other players like a frame's.
SM64_LIB_FN void sm64_mChar_knockback( int32_t marioId, uint32_t damage, int32_t hasSource, float xSrc, float ySrc, float zSrc )
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to knock back non-existant Mario with ID: %u", marioId);
        return;
    }
    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    gPlaySoundHook = record_tick_sound;
    if( hasSource )
    {
        u8 hurt = gMarioState->hurtCounter;
        // Retro64 Plus (M2 review): Minecraft's own hurt cooldown already let this hit through, so SM64's (after a
        // knockback, about a second; Minecraft's is half that) mustn't swallow it. Recomputed every frame.
        g_state->msInvulnerable = FALSE;
        fake_damage_knock_back( gMarioState, damage > 8 ? 8 : damage, 0, xSrc, ySrc, zSrc );
        gMarioState->hurtCounter = hurt;
    }
    else
    {
        play_sound( SOUND_MARIO_ATTACKED, gMarioState->marioObj->header.gfx.cameraToObject );
    }
    gPlaySoundHook = NULL;
}

SM64_LIB_FN void sm64_mChar_set_state( int32_t marioId, uint32_t stateFlags )
{
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to set state of non-existant Mario with ID: %u", marioId);
        return;
    }

    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    gMarioState->flags = stateFlags;
}

SM64_LIB_FN void sm64_mChar_set_action( int32_t marioId, uint32_t actionId ){
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to set action of non-existant Mario with ID: %u", marioId);
        return;
    }

    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    set_mario_action( gMarioState, actionId, 0);
}

SM64_LIB_FN void sm64_mChar_set_action_with_arg( int32_t marioId, uint32_t actionId,uint32_t actionArg ){
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to set action of non-existant Mario with ID: %u", marioId);
        return;
    }

    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    set_mario_action( gMarioState, actionId, actionArg);
}

SM64_LIB_FN void sm64_mChar_set_action_state( int32_t marioId, u16 actionState ){
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to set action-state of non-existant Mario with ID: %u", marioId);
        return;
    }

    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );

    gMarioState->actionState = actionState;
}

SM64_LIB_FN void sm64_mChar_set_water_level( int32_t marioId, signed int yLevel ){
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to set water level of non-existant Mario with ID: %u", marioId);
        return;
    }

    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );
    gMarioState->waterLevel = yLevel;
}

SM64_LIB_FN void sm64_mChar_set_gas_level( int32_t marioId, signed int yLevel ){
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to set gas level of non-existant Mario with ID: %u", marioId);
        return;
    }

    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );
    gMarioState->gasLevel = yLevel;
}

SM64_LIB_FN void sm64_mChar_heal( int32_t marioId, char healCounter ){
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to heal non-existant Mario with ID: %u", marioId);
        return;
    }

    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );
    gMarioState->healCounter += healCounter;
}

SM64_LIB_FN void sm64_mChar_set_angle( int32_t marioId, float angle ){
    if( marioId >= s_mario_instance_pool.size || s_mario_instance_pool.objects[marioId] == NULL )
    {
        DEBUG_PRINT("Tried to set angle of non-existant Mario with ID: %u", marioId);
        return;
    }

    global_state_bind( ((struct MarioInstance *)s_mario_instance_pool.objects[ marioId ])->globalState );
    gMarioState->faceAngle[1] = (short)(angle * (32768.0f * 3.14159f));
    gMarioState->marioObj->header.gfx.angle[1]=(short)(angle * (32768.0f * 3.14159f));
    //gMarioState->usedObj->rawData.asS32[(0x0F+1)]=gMarioState->faceAngle[1]; // oMoveAngleYaw = faceAngle[1];
}

SM64_LIB_FN uint32_t sm64_surface_object_create( const struct SM64SurfaceObject *surfaceObject )
{
    uint32_t id = surfaces_load_object( surfaceObject );
    return id;
}

SM64_LIB_FN void sm64_surface_object_move( uint32_t objectId, const struct SM64ObjectTransform *transform )
{
    surface_object_update_transform( objectId, transform );
}

SM64_LIB_FN void sm64_surface_object_delete( uint32_t objectId )
{
    // A mario standing on the platform that is being destroyed will have a pointer to freed memory if we don't clear it.
    for( int i = 0; i < s_mario_instance_pool.size; ++i )
    {
        if( s_mario_instance_pool.objects[ i ] == NULL ) continue; // Retro64 Plus (M6 review): a freed slot
        struct GlobalState *state = ((struct MarioInstance *)s_mario_instance_pool.objects[ i ])->globalState;
        if( state->mgMarioObject->platform == surfaces_object_get_transform_ptr( objectId ))
            state->mgMarioObject->platform = NULL;
    }

    surfaces_unload_object( objectId );
}

SM64_LIB_FN void sm64_seq_player_play_sequence(u8 player, u8 seqId, u16 arg2){
    seq_player_play_sequence(player,seqId,arg2);
}

SM64_LIB_FN void sm64_play_music(u8 player, u16 seqArgs, u16 fadeTimer){
    play_music(player,seqArgs,fadeTimer);
}

SM64_LIB_FN void sm64_stop_background_music(u16 seqId){
    stop_background_music(seqId);
}

SM64_LIB_FN void sm64_fadeout_background_music(u16 arg0, u16 fadeOut){
    fadeout_background_music(arg0,fadeOut);
}

SM64_LIB_FN u16 sm64_get_current_background_music(){
    return get_current_background_music();
}

SM64_LIB_FN void sm64_play_sound(s32 soundBits, f32 *pos){
    play_sound(soundBits,pos);
}

SM64_LIB_FN void sm64_play_sound_global(s32 soundBits){
    play_sound(soundBits,gGlobalSoundSource);
}

SM64_LIB_FN int sm64_set_volume(float vol){
    if (vol>1)
        vol=1;
    if (vol<0)
        vol=0;
    globalVolume=vol;
    return 0; // Retro64 Plus (M6 review): declared int, returned nothing
}

SM64_LIB_FN int sm64_get_version(){
    return LIB_VER; // used for compability checking with Retro64
}

SM64_LIB_FN void sm64_set_redive(u8 val) {
    configRedive = val;
}
SM64_LIB_FN void sm64_set_ground_pound_jump(u8 val) {
    configGroundPoundJump = val;
}

#ifdef VERSION_EU
#define SAMPLES_HIGH 656
#define SAMPLES_LOW 640
#else
#define SAMPLES_HIGH 544
#define SAMPLES_LOW 528
#endif

// Retro64 Plus (D21): replaces upstream's audio_thread/audio_tick. One SM64 audio frame (call at 30 Hz, on the same
// thread as the character ticks): advances the sound engine and writes 32 kHz stereo s16 samples to audio_buffer,
// which must hold SM64_AUDIO_BUFFER_SAMPLES values. Like original libsm64's sm64_audio_tick, it writes a little more
// when fewer than numDesiredSamples are still queued, so the caller's queue stays near that size. Returns the number
// of stereo frames written.
SM64_LIB_FN uint32_t sm64_audio_tick( uint32_t numQueuedSamples, uint32_t numDesiredSamples, int16_t *audio_buffer )
{
    if( !s_init_global ) return 0;
    audio_signal_game_loop_tick();
    u32 num_audio_samples = numQueuedSamples < numDesiredSamples ? SAMPLES_HIGH : SAMPLES_LOW;
    for (int i = 0; i < 2; i++) {
        create_next_audio_buffer(audio_buffer + i * (num_audio_samples * 2), num_audio_samples);
    }
    return 2 * num_audio_samples;
}
