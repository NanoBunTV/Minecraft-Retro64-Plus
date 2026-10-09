#pragma once
#include "decomp/memory.h"
// Retro64 Plus: Mario only. Upstream's Luigi, Steve, Alex and fan models are gone; the mod draws Luigi and the
// player's skin itself (D15, D28, D33; native/UPSTREAM.md).
enum MarioModels{
    MODEL_MARIO
};

#define ModelsUsed 1

void initModels(struct AllocOnlyPool *pool);
struct GraphNode* getModel(int model);
