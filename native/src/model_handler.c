#include "decomp/mario/geo.inc.h"
#include "decomp/engine/geo_layout.h"
#include "model_handler.h"
#include "load_tex_data.h"

// Retro64 Plus: Mario only (native/UPSTREAM.md).
static struct GraphNode* model_nodes[ModelsUsed]={0x0};

void initModels(struct AllocOnlyPool *pool){
    model_nodes[MODEL_MARIO] = process_geo_layout( pool, (void*)mario_geo_ptr );
}

struct GraphNode* getModel(int model){
    return model_nodes[MODEL_MARIO];
}
