#pragma once

#include "decomp/engine/graph_node.h"
#include "libsm64.h"

// Commented out in gbi.h - Replaced here 
#define gDPPipeSync(pkt) ({})
#define gSPSetGeometryMode(pkt, word) ({})
#define gSPClearGeometryMode(pkt, word) ({})
#define gDPFillRectangle(pkt, ulx, uly, lrx, lry) ({})
#define gSPViewport(pkt, v) ({})
extern void gSPMatrix( void *pkt, Mtx *m, uint8_t flags );
extern void gSPDisplayList( void *pkt, struct DisplayListNode *dl );

extern void gfx_adapter_bind_output_buffers( struct SM64MarioGeometryBuffers *outBuffers );
// Retro64 Plus (D28): the matrix of every animated part (bone) of the last character drawn, in drawing order.
extern void gfx_adapter_record_bone( Mat4 m );
extern uint32_t gfx_adapter_get_bones( float *out, uint32_t maxBones );
