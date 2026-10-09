#ifndef LIB_SM64_H
#define LIB_SM64_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>
#include "fake_interaction.h"

#ifdef _WIN32
    #ifdef SM64_LIB_EXPORT
        #define SM64_LIB_FN __declspec(dllexport)
    #else
        #define SM64_LIB_FN __declspec(dllimport)
    #endif
#else
    #define SM64_LIB_FN
#endif

struct SM64Surface
{
    int16_t type;
    int16_t force;
    uint16_t terrain;
    int32_t vertices[3][3];
};

struct SM64MarioInputs
{
    float camLookX, camLookZ;
    float cameraPosition[3]; // used for sound calculations
    float stickX, stickY;
    uint8_t buttonA, buttonB, buttonZ;
};

struct SM64ObjectTransform
{
    float position[3];
    float eulerRotation[3];
};

struct SM64SurfaceObject
{
    struct SM64ObjectTransform transform;
    uint32_t surfaceCount;
    struct SM64Surface *surfaces;
};

struct SM64MarioState
{
    float position[3];
    float velocity[3];
    float faceAngle;
    int16_t health;
    uint32_t flags;
    uint32_t action;
    uint32_t currentModel;
};

struct SM64MarioGeometryBuffers
{
    float *position;
    float *normal;
    float *color;
    float *uv;
    uint16_t numTrianglesUsed;
};

// Retro64 Plus (M1 checkpoint 3): what Mario's rendering reads, for drawing another player's Mario. Laid out
// without padding (48 bytes): the mod's JNA struct and network codec follow this order.
struct SM64MarioPose
{
    float offset[3];      // drawn position minus Mario's position, SM64 units
    uint32_t action;
    int16_t angle[3];
    int16_t animID;
    int16_t animYTrans;
    int16_t animFrame;
    int16_t modelState;
    int16_t torsoAngle[3];
    int16_t headAngle[3];
    int8_t capState;
    int8_t eyeState;
    int8_t handState;
    int8_t wingFlutter;
    int8_t grabPos;
    uint8_t punchState;
};

typedef void (*SM64DebugPrintFunctionPtr)( const char * );

enum
{
    SM64_TEXTURE_WIDTH = 64 * 11,
    SM64_TEXTURE_HEIGHT = 64,
    SM64_GEO_MAX_TRIANGLES = 2040,
};

extern SM64_LIB_FN void sm64_global_init( uint8_t *rom, uint8_t *outTexture, SM64DebugPrintFunctionPtr debugPrintFunction );
extern SM64_LIB_FN void sm64_global_terminate( void );

extern SM64_LIB_FN void sm64_static_surfaces_load( const struct SM64Surface *surfaceArray, uint32_t numSurfaces );

extern SM64_LIB_FN int32_t sm64_mChar_create( float x, float y, float z );
extern SM64_LIB_FN int32_t sm64_mChar_create_puppet( void );
extern SM64_LIB_FN void sm64_mChar_get_pose( int32_t marioId, struct SM64MarioPose *outPose );
extern SM64_LIB_FN void sm64_mChar_render_pose( int32_t marioId, const struct SM64MarioPose *pose, int32_t model, struct SM64MarioGeometryBuffers *outBuffers );
extern SM64_LIB_FN struct AnimInfo* sm64_get_anim_info(int32_t marioId,int16_t rot[3]);
extern SM64_LIB_FN void sm64_mChar_animTick(int32_t marioId, uint32_t stateFlags,struct AnimInfo* info,struct SM64MarioGeometryBuffers *outBuffers,int32_t model,int16_t rot[3]);
extern SM64_LIB_FN void sm64_mChar_tick( int32_t marioId, const struct SM64MarioInputs *inputs, struct SM64MarioState *outState, struct SM64MarioGeometryBuffers *outBuffers );
extern SM64_LIB_FN void sm64_mChar_delete( int32_t marioId );
extern SM64_LIB_FN void sm64_mChar_teleport(int32_t marioId, float x, float y, float z);
extern SM64_LIB_FN void sm64_mChar_set_velocity( int32_t marioId, float x, float y, float z );
extern SM64_LIB_FN void sm64_mChar_apply_damage( int32_t marioId, uint32_t damage,uint32_t interactionSubtype,float xSrc,float ySrc,float zSrc);
extern SM64_LIB_FN void sm64_mChar_set_state(int32_t marioId, uint32_t capType);
extern SM64_LIB_FN void sm64_mChar_set_action( int32_t marioId, uint32_t actionId );
extern SM64_LIB_FN void sm64_mChar_set_action_with_arg( int32_t marioId, uint32_t actionId,uint32_t actionArg );
extern SM64_LIB_FN void sm64_mChar_set_action_state( int32_t marioId, u16 actionState );
extern SM64_LIB_FN void sm64_mChar_set_water_level( int32_t marioId, signed int yLevel );
extern SM64_LIB_FN void sm64_mChar_set_gas_level( int32_t marioId, signed int yLevel );
extern SM64_LIB_FN void sm64_mChar_set_angle( int32_t marioId, float angle );
extern SM64_LIB_FN void sm64_mChar_heal( int32_t marioId, char healCounter );

extern SM64_LIB_FN uint32_t sm64_surface_object_create( const struct SM64SurfaceObject *surfaceObject );
extern SM64_LIB_FN void sm64_surface_object_move( uint32_t objectId, const struct SM64ObjectTransform *transform );
extern SM64_LIB_FN void sm64_surface_object_delete( uint32_t objectId );

extern SM64_LIB_FN void sm64_seq_player_play_sequence(u8 player, u8 seqId, u16 arg2);
extern SM64_LIB_FN void sm64_play_music(u8 player, u16 seqArgs, u16 fadeTimer);
extern SM64_LIB_FN void sm64_stop_background_music(u16 seqId);
extern SM64_LIB_FN void sm64_fadeout_background_music(u16 arg0, u16 fadeOut);
extern SM64_LIB_FN u16 sm64_get_current_background_music();
extern SM64_LIB_FN void sm64_play_sound(s32 soundBits, f32 *pos);
extern SM64_LIB_FN void sm64_play_sound_global(s32 soundBits);
extern SM64_LIB_FN int sm64_set_volume(float vol);
extern SM64_LIB_FN int sm64_get_version();
extern SM64_LIB_FN void sm64_set_redive(u8 val);
extern SM64_LIB_FN void sm64_set_ground_pound_jump(u8 val);
// Retro64 Plus (D21): pull-style audio, replacing upstream's audio thread. 2 × 544 stereo frames at most.
#define SM64_AUDIO_BUFFER_SAMPLES (544 * 2 * 2)
extern SM64_LIB_FN uint32_t sm64_audio_tick( uint32_t numQueuedSamples, uint32_t numDesiredSamples, int16_t *audio_buffer );
// Retro64 Plus (D28, D32): bones of the last drawn character; sounds of the last tick; positional sound sources.
#define SM64_MAX_BONES 32
extern SM64_LIB_FN uint32_t sm64_last_render_bones( float *outMatrices, uint32_t maxBones );
extern SM64_LIB_FN uint32_t sm64_mChar_last_tick_sounds( int32_t *outSounds, uint32_t maxSounds );
extern SM64_LIB_FN void sm64_sound_source_move( uint32_t source, float x, float y, float z );
extern SM64_LIB_FN void sm64_sound_source_play( uint32_t source, int32_t soundBits );
extern SM64_LIB_FN int32_t sm64_mChar_set_health( int32_t marioId, int16_t health );
extern SM64_LIB_FN void sm64_mChar_set_caps( int32_t marioId, uint32_t caps, uint32_t hiddenCaps );
extern SM64_LIB_FN int32_t sm64_mChar_grab( int32_t marioId );
extern SM64_LIB_FN int32_t sm64_mChar_is_holding( int32_t marioId );
extern SM64_LIB_FN void sm64_mChar_drop( int32_t marioId );
extern SM64_LIB_FN int32_t sm64_mChar_ride_shell( int32_t marioId );
extern SM64_LIB_FN int32_t sm64_mChar_is_riding( int32_t marioId );
extern SM64_LIB_FN void sm64_mChar_knockback( int32_t marioId, uint32_t damage, int32_t hasSource, float xSrc, float ySrc, float zSrc );
extern bool hasAudio;
extern int getCurrentModel();
#endif//LIB_SM64_H
