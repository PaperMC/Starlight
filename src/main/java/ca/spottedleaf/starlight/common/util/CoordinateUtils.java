package ca.spottedleaf.starlight.common.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.SectionPos;

public final class CoordinateUtils {

    // dx, dz are relative to the target chunk
    // dx, dz in [-radius, radius]
    public static int getNeighbourMappedIndex(final int dx, final int dz, final int radius) {
        return (dx + radius) + (2 * radius + 1)*(dz + radius);
    }

    // the chunk keys are compatible with vanilla

    public static long getChunkKey(final BlockPos pos) {
        return ((long)(pos.getZ() >> 4) << 32) | ((pos.getX() >> 4) & 0xFFFFFFFFL);
    }

    public static long getChunkKey(final Entity entity) {
        return ((long)(MathHelper.floor(entity.getPosZ()) >> 4) << 32) | ((MathHelper.floor(entity.getPosX()) >> 4) & 0xFFFFFFFFL);
    }

    public static long getChunkKey(final ChunkPos pos) {
        return ((long)pos.z << 32) | (pos.x & 0xFFFFFFFFL);
    }

    public static long getChunkKey(final SectionPos pos) {
        return ((long)pos.getZ() << 32) | (pos.getX() & 0xFFFFFFFFL);
    }

    public static long getChunkKey(final int x, final int z) {
        return ((long)z << 32) | (x & 0xFFFFFFFFL);
    }

    public static int getChunkX(final long chunkKey) {
        return (int)chunkKey;
    }

    public static int getChunkZ(final long chunkKey) {
        return (int)(chunkKey >>> 32);
    }

    public static int getChunkCoordinate(final double blockCoordinate) {
        return MathHelper.floor(blockCoordinate) >> 4;
    }

    // the section keys are compatible with vanilla's

    static final int SECTION_X_BITS = 22;
    static final long SECTION_X_MASK = (1L << SECTION_X_BITS) - 1;
    static final int SECTION_Y_BITS = 20;
    static final long SECTION_Y_MASK = (1L << SECTION_Y_BITS) - 1;
    static final int SECTION_Z_BITS = 22;
    static final long SECTION_Z_MASK = (1L << SECTION_Z_BITS) - 1;
    // format is y,z,x (in order of LSB to MSB)
    static final int SECTION_Y_SHIFT = 0;
    static final int SECTION_Z_SHIFT = SECTION_Y_SHIFT + SECTION_Y_BITS;
    static final int SECTION_X_SHIFT = SECTION_Z_SHIFT + SECTION_X_BITS;
    static final int SECTION_TO_BLOCK_SHIFT = 4;

    public static long getChunkSectionKey(final int x, final int y, final int z) {
        return ((x & SECTION_X_MASK) << SECTION_X_SHIFT)
                | ((y & SECTION_Y_MASK) << SECTION_Y_SHIFT)
                | ((z & SECTION_Z_MASK) << SECTION_Z_SHIFT);
    }

    public static long getChunkSectionKey(final SectionPos pos) {
        return ((pos.getX() & SECTION_X_MASK) << SECTION_X_SHIFT)
                | ((pos.getY() & SECTION_Y_MASK) << SECTION_Y_SHIFT)
                | ((pos.getZ() & SECTION_Z_MASK) << SECTION_Z_SHIFT);
    }

    public static long getChunkSectionKey(final ChunkPos pos, final int y) {
        return ((pos.x & SECTION_X_MASK) << SECTION_X_SHIFT)
                | ((y & SECTION_Y_MASK) << SECTION_Y_SHIFT)
                | ((pos.z & SECTION_Z_MASK) << SECTION_Z_SHIFT);
    }

    public static long getChunkSectionKey(final BlockPos pos) {
        return (((long)pos.getX() << (SECTION_X_SHIFT - SECTION_TO_BLOCK_SHIFT)) & (SECTION_X_MASK << SECTION_X_SHIFT)) |
                ((pos.getY() >> SECTION_TO_BLOCK_SHIFT) & (SECTION_Y_MASK << SECTION_Y_SHIFT)) |
                (((long)pos.getZ() << (SECTION_Z_SHIFT - SECTION_TO_BLOCK_SHIFT)) & (SECTION_Z_MASK << SECTION_Z_SHIFT));
    }

    public static long getChunkSectionKey(final Entity entity) {
        return ((MathHelper.lfloor(entity.getPosX()) << (SECTION_X_SHIFT - SECTION_TO_BLOCK_SHIFT)) & (SECTION_X_MASK << SECTION_X_SHIFT)) |
                ((MathHelper.lfloor(entity.getPosY()) >> SECTION_TO_BLOCK_SHIFT) & (SECTION_Y_MASK << SECTION_Y_SHIFT)) |
                ((MathHelper.lfloor(entity.getPosZ()) << (SECTION_Z_SHIFT - SECTION_TO_BLOCK_SHIFT)) & (SECTION_Z_MASK << SECTION_Z_SHIFT));
    }

    public static int getChunkSectionX(final long key) {
        return (int)(key << (Long.SIZE - (SECTION_X_SHIFT + SECTION_X_BITS)) >> (Long.SIZE - SECTION_X_BITS));
    }

    public static int getChunkSectionY(final long key) {
        return (int)(key << (Long.SIZE - (SECTION_Y_SHIFT + SECTION_Y_BITS)) >> (Long.SIZE - SECTION_Y_BITS));
    }

    public static int getChunkSectionZ(final long key) {
        return (int)(key << (Long.SIZE - (SECTION_Z_SHIFT + SECTION_Z_BITS)) >> (Long.SIZE - SECTION_Z_BITS));
    }

    // the block coordinates are not necessarily compatible with vanilla's

    public static int getBlockCoordinate(final double blockCoordinate) {
        return MathHelper.floor(blockCoordinate);
    }

    public static long getBlockKey(final int x, final int y, final int z) {
        return ((long)x & 0x7FFFFFF) | (((long)z & 0x7FFFFFF) << 27) | ((long)y << 54);
    }

    public static long getBlockKey(final BlockPos pos) {
        return ((long)pos.getX() & 0x7FFFFFF) | (((long)pos.getZ() & 0x7FFFFFF) << 27) | ((long)pos.getY() << 54);
    }

    public static long getBlockKey(final Entity entity) {
        return ((long)entity.getPosX() & 0x7FFFFFF) | (((long)entity.getPosZ() & 0x7FFFFFF) << 27) | ((long)entity.getPosY() << 54);
    }

    private CoordinateUtils() {
        throw new RuntimeException();
    }
}
