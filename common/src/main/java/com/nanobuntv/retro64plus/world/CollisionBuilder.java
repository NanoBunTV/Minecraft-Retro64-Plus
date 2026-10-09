package com.nanobuntv.retro64plus.world;

import com.nanobuntv.retro64plus.sm64.Surfaces;
import com.nanobuntv.retro64plus.sm64.Units;
import com.nanobuntv.retro64plus.sm64.lib.SM64Surface;
import java.util.Arrays;
import com.nanobuntv.retro64plus.caps.CapTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Turns the blocks in a box around Mario into SM64 collision triangles (upstream rebuilt a 3×5×3 box every frame and
 * re-uploaded it; here the box is larger and only rebuilt when needed, see the caller). Every collision-shape box
 * becomes up to six faces; a full block's face against another full block is skipped, since nothing can touch it.
 * Lava's surface is a burning floor. A safety floor lies below the world, because libsm64 crashes when Mario has no
 * floor at all.
 */
public final class CollisionBuilder {
    /**
     * Boxes no taller than this (blocks) in a partial block (slabs, stairs' halves, carpets, snow layers) get no side
     * walls, so Mario steps up onto them. SM64 checks for walls 30 and 60 units above his feet, so a half-block side
     * would stop him dead (real SM64 levels use ramps there), while its floor search accepts floors up to 78 units
     * (0.71 blocks) above him. A hanging top slab still stops him: its underside is a ceiling.
     */
    static final double MAX_STEP = 0.5625;

    /** Triangles as 9 ints each (SM64 units), with their packed type/terrain. Reused between builds. */
    private int[] vertices = new int[9 * 4096];
    private int[] kinds = new int[4096];
    private int count;

    /** Builds the box {@code center ± (radius, below..above)}; {@code minY} is the world's bottom. */
    public void build(BlockGetter level, BlockPos center, int radius, int below, int above, int minY) {
        count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = Math.max(minY, center.getY() - below); y <= center.getY() + above; y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    FluidState fluid = state.getFluidState();
                    if (fluid.is(FluidTags.LAVA)) {
                        float top = y + fluid.getHeight(level, pos);
                        face(x, top, z, x + 1, top, z + 1, Direction.UP,
                                SurfaceTypes.pack(Surfaces.BURNING, Surfaces.TERRAIN_STONE));
                    }
                    if (state.isAir()) continue;
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    int kind = SurfaceTypes.of(state, level, pos);
                    if (state.isCollisionShapeFullBlock(level, pos)) {
                        boolean vanishable = state.is(CapTags.VANISH_PASSABLE);
                        for (Direction side : Direction.values()) {
                            neighbor.setWithOffset(pos, side);
                            BlockState next = level.getBlockState(neighbor);
                            // A face against a full block is hidden, unless that block is one the Vanish cap walks
                            // into (D43): inside a glass wall Mario still needs the floor below and the walls around,
                            // also where the wall stands on more glass.
                            boolean intoVanishable = next.is(CapTags.VANISH_PASSABLE)
                                    && (!vanishable || side == Direction.UP);
                            if (next.isCollisionShapeFullBlock(level, neighbor) && !intoVanishable) continue;
                            face(x, y, z, x + 1, y + 1, z + 1, side, kind);
                        }
                    } else {
                        for (AABB box : shape.toAabbs()) {
                            box = box.move(x, y, z);
                            boolean step = box.getYsize() <= MAX_STEP;
                            for (Direction side : Direction.values()) {
                                if (step && side.getAxis().isHorizontal()) continue;
                                face(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, side, kind);
                            }
                        }
                    }
                }
            }
        }
        // The safety floor: 64 blocks around Mario, 70 below the world's bottom (Minecraft's void damage starts at 64).
        float floorY = minY - 70;
        face(center.getX() - 64, floorY, center.getZ() - 64, center.getX() + 64, floorY, center.getZ() + 64,
                Direction.UP, SurfaceTypes.pack(Surfaces.DEFAULT, Surfaces.TERRAIN_STONE));
    }

    public int triangleCount() {
        return count;
    }

    /** The built triangles as a contiguous JNA array for {@code sm64_static_surfaces_load}. */
    public SM64Surface[] toSurfaces() {
        SM64Surface[] surfaces = (SM64Surface[]) new SM64Surface().toArray(Math.max(1, count));
        for (int i = 0; i < count; i++) {
            SM64Surface s = surfaces[i];
            s.type = SurfaceTypes.type(kinds[i]);
            s.terrain = SurfaceTypes.terrain(kinds[i]);
            s.force = 0;
            System.arraycopy(vertices, i * 9, s.vertices, 0, 9);
        }
        return surfaces;
    }

    /** One face of the box (x0..x1, y0..y1, z0..z1), as two triangles facing {@code side}. */
    private void face(double x0, double y0, double z0, double x1, double y1, double z1, Direction side, int kind) {
        int ax = units(x0), ay = units(y0), az = units(z0), bx = units(x1), by = units(y1), bz = units(z1);
        switch (side) {
            case UP -> quad(ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, 0, 1, 0, kind);
            case DOWN -> quad(ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz, 0, -1, 0, kind);
            case NORTH -> quad(ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, 0, 0, -1, kind);
            case SOUTH -> quad(ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz, 0, 0, 1, kind);
            case WEST -> quad(ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az, -1, 0, 0, kind);
            case EAST -> quad(bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, 1, 0, 0, kind);
        }
    }

    /** Corners a b c d of a planar quad, split into (a b c) and (a c d), each wound to face (nx, ny, nz). */
    private void quad(int ax, int ay, int az, int bx, int by, int bz, int cx, int cy, int cz, int dx, int dy, int dz,
            int nx, int ny, int nz, int kind) {
        triangle(ax, ay, az, bx, by, bz, cx, cy, cz, nx, ny, nz, kind);
        triangle(ax, ay, az, cx, cy, cz, dx, dy, dz, nx, ny, nz, kind);
    }

    private void triangle(int ax, int ay, int az, int bx, int by, int bz, int cx, int cy, int cz, int nx, int ny,
            int nz, int kind) {
        // SM64 takes the normal as (b - a) × (c - a); swap b and c when that points the wrong way.
        long ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        long crossX = uy * vz - uz * vy, crossY = uz * vx - ux * vz, crossZ = ux * vy - uy * vx;
        if (crossX == 0 && crossY == 0 && crossZ == 0) return; // degenerate (a zero-thickness box side)
        boolean flip = crossX * nx + crossY * ny + crossZ * nz < 0;
        ensureCapacity();
        int o = count * 9;
        vertices[o] = ax;
        vertices[o + 1] = ay;
        vertices[o + 2] = az;
        vertices[o + 3] = flip ? cx : bx;
        vertices[o + 4] = flip ? cy : by;
        vertices[o + 5] = flip ? cz : bz;
        vertices[o + 6] = flip ? bx : cx;
        vertices[o + 7] = flip ? by : cy;
        vertices[o + 8] = flip ? bz : cz;
        kinds[count++] = kind;
    }

    private void ensureCapacity() {
        if (count < kinds.length) return;
        kinds = Arrays.copyOf(kinds, kinds.length * 2);
        vertices = Arrays.copyOf(vertices, vertices.length * 2);
    }

    private static int units(double blocks) {
        return Math.round(Units.toSm64(blocks));
    }

    /** Test access: triangle {@code i}'s nine coordinates. */
    int[] triangle(int i) {
        return Arrays.copyOfRange(vertices, i * 9, i * 9 + 9);
    }
}
