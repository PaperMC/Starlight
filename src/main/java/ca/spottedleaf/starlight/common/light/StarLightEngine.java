package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.util.IntegerUtil;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.IChunkLightProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public abstract class StarLightEngine {

    protected static final BlockState AIR_BLOCK_STATE = Blocks.AIR.getDefaultState();

    protected static final ChunkSection EMPTY_CHUNK_SECTION = new ChunkSection(0);

    protected static final AxisDirection[] DIRECTIONS = AxisDirection.values();
    protected static final AxisDirection[] AXIS_DIRECTIONS = DIRECTIONS;
    protected static final AxisDirection[] ONLY_HORIZONTAL_DIRECTIONS = new AxisDirection[] {
            AxisDirection.POSITIVE_X, AxisDirection.NEGATIVE_X,
            AxisDirection.POSITIVE_Z, AxisDirection.NEGATIVE_Z
    };

    protected static enum AxisDirection {

        // Declaration order is important and relied upon. Do not change without modifying propagation code.
        POSITIVE_X(1, 0, 0), NEGATIVE_X(-1, 0, 0),
        POSITIVE_Z(0, 0, 1), NEGATIVE_Z(0, 0, -1),
        POSITIVE_Y(0, 1, 0), NEGATIVE_Y(0, -1, 0);

        static {
            POSITIVE_X.opposite = NEGATIVE_X; NEGATIVE_X.opposite = POSITIVE_X;
            POSITIVE_Z.opposite = NEGATIVE_Z; NEGATIVE_Z.opposite = POSITIVE_Z;
            POSITIVE_Y.opposite = NEGATIVE_Y; NEGATIVE_Y.opposite = POSITIVE_Y;
        }

        protected AxisDirection opposite;

        public final int x;
        public final int y;
        public final int z;
        public final Direction nms;
        public final long everythingButThisDirection;
        public final long everythingButTheOppositeDirection;

        AxisDirection(final int x, final int y, final int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nms = Direction.byLong(x, y, z);
            this.everythingButThisDirection = (long)(ALL_DIRECTIONS_BITSET ^ (1 << this.ordinal()));
            // positive is always even, negative is always odd. Flip the 1 bit to get the negative direction.
            this.everythingButTheOppositeDirection = (long)(ALL_DIRECTIONS_BITSET ^ (1 << (this.ordinal() ^ 1)));
        }

        public AxisDirection getOpposite() {
            return this.opposite;
        }
    }

    // I'd like to thank https://www.seedofandromeda.com/blogs/29-fast-flood-fill-lighting-in-a-blocky-voxel-game-pt-1
    // for explaining how light propagates via breadth-first search

    // While the above is a good start to understanding the general idea of what the general principles are, it's not
    // exactly how the vanilla light engine should behave for minecraft.

    // similar to the above, except the chunk section indices vary from [-1, 1], or [0, 2]
    // for the y chunk section it's from [-1, 16] or [0, 17]
    // index = x + (z * 5) + (y * 25)
    // null index indicates the chunk section doesn't exist (empty or out of bounds)
    protected final ChunkSection[] sectionCache = new ChunkSection[5 * 5 * (16 + 2 + 2)]; // add two extra sections for buffer

    // the exact same as above, except for storing fast access to SWMRNibbleArray
    // for the y chunk section it's from [-1, 16] or [0, 17]
    // index = x + (z * 5) + (y * 25)
    protected final SWMRNibbleArray[] nibbleCache = new SWMRNibbleArray[5 * 5 * (16 + 2 + 2)]; // add two extra sections for buffer

    // the exact same as above, except for storing fast access to nibbles to call change callbacks for
    // for the y chunk section it's from [-1, 16] or [0, 17]
    // index = x + (z * 5) + (y * 25)
    protected final boolean[] notifyUpdateCache = new boolean[5 * 5 * (16 + 2 + 2)];

    // always initialsed during start of lighting. no index is null.
    // index = x + (z * 5)
    protected final IChunk[] chunkCache = new IChunk[5 * 5];

    // index = x + (z * 5)
    protected final boolean[][][] emptinessMapCache = new boolean[5 * 5][][];

    protected final BlockPos.Mutable mutablePos1 = new BlockPos.Mutable();
    protected final BlockPos.Mutable mutablePos2 = new BlockPos.Mutable();
    protected final BlockPos.Mutable mutablePos3 = new BlockPos.Mutable();

    protected int encodeOffsetX;
    protected int encodeOffsetY;
    protected int encodeOffsetZ;

    protected int coordinateOffset;

    protected int chunkOffsetX;
    protected int chunkOffsetY;
    protected int chunkOffsetZ;

    protected int chunkIndexOffset;
    protected int chunkSectionIndexOffset;

    protected final boolean skylightPropagator;
    protected final int emittedLightMask;
    protected final boolean isClientSide;

    protected StarLightEngine(final boolean skylightPropagator, final boolean isClientSide) {
        this.skylightPropagator = skylightPropagator;
        this.emittedLightMask = skylightPropagator ? 0 : 0xF;
        this.isClientSide = isClientSide;
    }

    protected final void setupEncodeOffset(final int centerX, final int centerY, final int centerZ) {
        // 31 = center + encodeOffset
        this.encodeOffsetX = 31 - centerX;
        this.encodeOffsetY = 31; // we want 0 to be the smallest encoded value
        this.encodeOffsetZ = 31 - centerZ;

        // coordinateIndex = x | (z << 6) | (y << 12)
        this.coordinateOffset = this.encodeOffsetX + (this.encodeOffsetZ << 6) + (this.encodeOffsetY << 12);

        // 2 = (centerX >> 4) + chunkOffset
        this.chunkOffsetX = 2 - (centerX >> 4);
        this.chunkOffsetY = 2; // lowest should be 0, not -2
        this.chunkOffsetZ = 2 - (centerZ >> 4);

        // chunk index = x + (5 * z)
        this.chunkIndexOffset = this.chunkOffsetX + (5 * this.chunkOffsetZ);

        // chunk section index = x + (5 * z) + ((5*5) * y)
        this.chunkSectionIndexOffset = this.chunkIndexOffset + ((5 * 5) * this.chunkOffsetY);
    }

    protected final void setupCaches(final IChunkLightProvider chunkProvider, final int centerX, final int centerY, final int centerZ, final boolean relaxed) {
        final int centerChunkX = centerX >> 4;
        final int centerChunkY = centerY >> 4;
        final int centerChunkZ = centerZ >> 4;

        this.setupEncodeOffset(centerChunkX * 16 + 7, centerChunkY * 16 + 7, centerChunkZ * 16 + 7);

        final int minX = centerChunkX - 1;
        final int minZ = centerChunkZ - 1;
        final int maxX = centerChunkX + 1;
        final int maxZ = centerChunkZ + 1;

        for (int cx = minX; cx <= maxX; ++cx) {
            for (int cz = minZ; cz <= maxZ; ++cz) {
                final IChunk chunk = (IChunk)chunkProvider.getChunkForLight(cx, cz); // mappings are awful here, this is the "get chunk at if at least features"

                if (chunk == null) {
                    if (relaxed) {
                        continue;
                    }
                    throw new IllegalArgumentException("Trying to propagate light update before 1 radius neighbours ready");
                }

                if (!this.canUseChunk(chunk)) {
                    continue;
                }

                this.setChunkInCache(cx, cz, chunk);
                this.setBlocksForChunkInCache(cx, cz, chunk.getSections());
                this.setNibblesForChunkInCache(cx, cz, this.getNibblesOnChunk(chunk));
                this.setEmptinessMapCache(cx, cz, this.getEmptinessMap(chunk));
            }
        }
    }

    protected final IChunk getChunkInCache(final int chunkX, final int chunkZ) {
        return this.chunkCache[chunkX + 5*chunkZ + this.chunkIndexOffset];
    }

    protected final void setChunkInCache(final int chunkX, final int chunkZ, final IChunk chunk) {
        this.chunkCache[chunkX + 5*chunkZ + this.chunkIndexOffset] = chunk;
    }

    protected final ChunkSection getChunkSection(final int chunkX, final int chunkY, final int chunkZ) {
        return this.sectionCache[chunkX + 5*chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset];
    }

    protected final void setChunkSectionInCache(final int chunkX, final int chunkY, final int chunkZ, final ChunkSection section) {
        this.sectionCache[chunkX + 5*chunkZ + 5*5*chunkY + this.chunkSectionIndexOffset] = section;
    }

    protected final void setBlocksForChunkInCache(final int chunkX, final int chunkZ, final ChunkSection[] sections) {
        for (int cy = -1; cy <= 16; ++cy) {
            this.setChunkSectionInCache(chunkX, cy, chunkZ,
                    sections == null ? null : (cy >= 0 && cy <= 15 ? (sections[cy] == null || sections[cy].isEmpty() ? EMPTY_CHUNK_SECTION : sections[cy]) : EMPTY_CHUNK_SECTION));
        }
    }

    protected final SWMRNibbleArray getNibbleFromCache(final int chunkX, final int chunkY, final int chunkZ) {
        return this.nibbleCache[chunkX + 5*chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset];
    }

    protected final SWMRNibbleArray[] getNibblesForChunkFromCache(final int chunkX, final int chunkZ) {
        final SWMRNibbleArray[] ret = getEmptyLightArray();

        for (int cy = -1; cy <= 16; ++cy) {
            ret[cy + 1] = this.nibbleCache[chunkX + 5*chunkZ + (cy * (5 * 5)) + this.chunkSectionIndexOffset];
        }

        return ret;
    }

    protected final void setNibbleInCache(final int chunkX, final int chunkY, final int chunkZ, final SWMRNibbleArray nibble) {
        this.nibbleCache[chunkX + 5*chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset] = nibble;
    }

    protected final void setNibblesForChunkInCache(final int chunkX, final int chunkZ, final SWMRNibbleArray[] nibbles) {
        for (int cy = -1; cy <= 16; ++cy) {
            this.setNibbleInCache(chunkX, cy, chunkZ, nibbles == null ? null : nibbles[cy + 1]);
        }
    }

    protected final void updateVisible(final IChunkLightProvider lightAccess) {
        for (int index = 0, max = this.nibbleCache.length; index < max; ++index) {
            final SWMRNibbleArray nibble = this.nibbleCache[index];
            if (!this.notifyUpdateCache[index] && (nibble == null || !nibble.isDirty())) {
                continue;
            }

            final int chunkX = (index % 5) - this.chunkOffsetX;
            final int chunkZ = ((index / 5) % 5) - this.chunkOffsetZ;
            final int chunkY = ((index / (5*5)) % (16 + 2 + 2)) - this.chunkOffsetY;
            if ((nibble != null && nibble.updateVisible()) || this.notifyUpdateCache[index]) {
                lightAccess.markLightChanged(this.skylightPropagator ? LightType.SKY : LightType.BLOCK, SectionPos.of(chunkX, chunkY, chunkZ));
            }
        }
    }

    protected final void destroyCaches() {
        Arrays.fill(this.sectionCache, null);
        Arrays.fill(this.nibbleCache, null);
        Arrays.fill(this.chunkCache, null);
        Arrays.fill(this.emptinessMapCache, null);
        if (this.isClientSide) {
            Arrays.fill(this.notifyUpdateCache, false);
        }
    }

    protected final BlockState getBlockState(final int worldX, final int worldY, final int worldZ) {
        final ChunkSection section = this.sectionCache[(worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset];

        if (section != null) {
            return section == EMPTY_CHUNK_SECTION ? AIR_BLOCK_STATE : section.getBlockState(worldX & 15, worldY & 15, worldZ & 15);
        }

        return null;
    }

    protected final BlockState getBlockState(final int sectionIndex, final int localIndex) {
        final ChunkSection section = this.sectionCache[sectionIndex];

        if (section != null) {
            return section == EMPTY_CHUNK_SECTION ? AIR_BLOCK_STATE : section.data.get(localIndex);
        }

        return null;
    }

    protected final int getLightLevel(final int worldX, final int worldY, final int worldZ) {
        final SWMRNibbleArray nibble = this.nibbleCache[(worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset];

        return nibble == null ? 0 : nibble.getUpdating((worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8));
    }

    protected final int getLightLevel(final int sectionIndex, final int localIndex) {
        final SWMRNibbleArray nibble = this.nibbleCache[sectionIndex];

        return nibble == null ? 0 : nibble.getUpdating(localIndex);
    }

    protected final void setLightLevel(final int worldX, final int worldY, final int worldZ, final int level) {
        final int sectionIndex = (worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset;
        final SWMRNibbleArray nibble = this.nibbleCache[sectionIndex];

        if (nibble != null) {
            nibble.set((worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8), level);
            if (this.isClientSide) {
                int cx1 = (worldX - 1) >> 4;
                int cx2 = (worldX + 1) >> 4;
                int cy1 = (worldY - 1) >> 4;
                int cy2 = (worldY + 1) >> 4;
                int cz1 = (worldZ - 1) >> 4;
                int cz2 = (worldZ + 1) >> 4;
                for (int x = cx1; x <= cx2; ++x) {
                    for (int y = cy1; y <= cy2; ++y) {
                        for (int z = cz1; z <= cz2; ++z) {
                            this.notifyUpdateCache[x + 5 * z + (5 * 5) * y + this.chunkSectionIndexOffset] = true;
                        }
                    }
                }
            }
        }
    }

    protected final void postLightUpdate(final int worldX, final int worldY, final int worldZ) {
        if (this.isClientSide) {
            int cx1 = (worldX - 1) >> 4;
            int cx2 = (worldX + 1) >> 4;
            int cy1 = (worldY - 1) >> 4;
            int cy2 = (worldY + 1) >> 4;
            int cz1 = (worldZ - 1) >> 4;
            int cz2 = (worldZ + 1) >> 4;
            for (int x = cx1; x <= cx2; ++x) {
                for (int y = cy1; y <= cy2; ++y) {
                    for (int z = cz1; z <= cz2; ++z) {
                        this.notifyUpdateCache[x + (5 * z) + (5 * 5 * y) + this.chunkSectionIndexOffset] = true;
                    }
                }
            }
        }
    }

    protected final void setLightLevel(final int sectionIndex, final int localIndex, final int worldX, final int worldY, final int worldZ, final int level) {
        final SWMRNibbleArray nibble = this.nibbleCache[sectionIndex];

        if (nibble != null) {
            nibble.set(localIndex, level);
            if (this.isClientSide) {
                int cx1 = (worldX - 1) >> 4;
                int cx2 = (worldX + 1) >> 4;
                int cy1 = (worldY - 1) >> 4;
                int cy2 = (worldY + 1) >> 4;
                int cz1 = (worldZ - 1) >> 4;
                int cz2 = (worldZ + 1) >> 4;
                for (int x = cx1; x <= cx2; ++x) {
                    for (int y = cy1; y <= cy2; ++y) {
                        for (int z = cz1; z <= cz2; ++z) {
                            this.notifyUpdateCache[x + (5 * z) + (5 * 5 * y) + this.chunkSectionIndexOffset] = true;
                        }
                    }
                }
            }
        }
    }

    protected final boolean[][] getEmptinessMap(final int chunkX, final int chunkZ) {
        return this.emptinessMapCache[chunkX + 5*chunkZ + this.chunkIndexOffset];
    }

    protected final void setEmptinessMapCache(final int chunkX, final int chunkZ, final boolean[][] emptinessMap) {
        this.emptinessMapCache[chunkX + 5*chunkZ + this.chunkIndexOffset] = emptinessMap;
    }

    protected final int getCustomLightLevel(final VariableBlockLightHandler customBlockHandler, final int worldX, final int worldY,
                                            final int worldZ, final int dfl) {
        final int ret = customBlockHandler.getLightLevel(worldX, worldY, worldZ);
        return ret == -1 ? dfl : ret;
    }

    public static SWMRNibbleArray[] getFilledEmptyLight() {
        final SWMRNibbleArray[] ret = getEmptyLightArray();

        for (int i = 0, len = ret.length; i < len; ++i) {
            ret[i] = new SWMRNibbleArray(null, true);
        }

        return ret;
    }

    public static SWMRNibbleArray[] getEmptyLightArray() {
        return new SWMRNibbleArray[16 - (-1) + 1];
    }

    protected abstract boolean[][] getEmptinessMap(final IChunk chunk);

    protected abstract SWMRNibbleArray[] getNibblesOnChunk(final IChunk chunk);

    protected abstract void setNibbles(final IChunk chunk, final SWMRNibbleArray[] to);

    protected abstract boolean canUseChunk(final IChunk chunk);

    public final void blocksChangedInChunk(final IChunkLightProvider lightAccess, final int chunkX, final int chunkZ,
                                           final Set<BlockPos> positions, final Boolean[] changedSections) {
        this.setupCaches(lightAccess, chunkX * 16 + 7, 128, chunkZ * 16 + 7, this.isClientSide);
        try {
            final IChunk chunk = this.getChunkInCache(chunkX, chunkZ);
            if (this.isClientSide && chunk == null) {
                return;
            }
            if (changedSections != null) {
                this.handleEmptySectionChanges(lightAccess, chunk, changedSections, false);
            }
            if (!positions.isEmpty()) {
                this.propagateBlockChanges(lightAccess, chunk, positions);
            }
            this.updateVisible(lightAccess);
        } finally {
            this.destroyCaches();
        }
    }

    // subclasses should not initialise caches, as this will always be done by the super call
    // subclasses should not invoke updateVisible, as this will always be done by the super call
    protected abstract void propagateBlockChanges(final IChunkLightProvider lightAccess, final IChunk atChunk, final Set<BlockPos> positions);

    protected abstract void checkBlock(final IChunkLightProvider lightAccess, final int worldX, final int worldY, final int worldZ);

    protected void checkChunkEdge(final IChunkLightProvider lightAccess, final IChunk chunk,
                                  final int chunkX, final int chunkY, final int chunkZ) {
        final SWMRNibbleArray currNibble = this.getNibbleFromCache(chunkX, chunkY, chunkZ);
        if (currNibble == null) {
            return;
        }

        for (final AxisDirection direction : ONLY_HORIZONTAL_DIRECTIONS) {
            final int neighbourOffX = direction.x;
            final int neighbourOffZ = direction.z;

            final SWMRNibbleArray neighbourNibble = this.getNibbleFromCache(chunkX + neighbourOffX,
                    chunkY, chunkZ + neighbourOffZ);

            if (neighbourNibble == null) {
                continue;
            }

            if (!currNibble.isInitialisedUpdating() && !neighbourNibble.isInitialisedUpdating()) {
                // both are zero, nothing to check.
                continue;
            }

            final int incX;
            final int incZ;
            final int startX;
            final int startZ;

            if (neighbourOffX != 0) {
                // x direction
                incX = 0;
                incZ = 1;

                if (direction.x < 0) {
                    // negative
                    startX = chunkX << 4;
                } else {
                    startX = chunkX << 4 | 15;
                }
                startZ = chunkZ << 4;
            } else {
                // z direction
                incX = 1;
                incZ = 0;

                if (neighbourOffZ < 0) {
                    // negative
                    startZ = chunkZ << 4;
                } else {
                    startZ = chunkZ << 4 | 15;
                }
                startX = chunkX << 4;
            }

            for (int currY = chunkY << 4, maxY = currY | 15; currY <= maxY; ++currY) {
                for (int i = 0, currX = startX, currZ = startZ; i < 16; ++i, currX += incX, currZ += incZ) {
                    final int neighbourX = currX + neighbourOffX;
                    final int neighbourZ = currZ + neighbourOffZ;

                    final int currentLevel = currNibble.getUpdating((currX & 15) |
                            ((currZ & 15)) << 4 |
                            ((currY & 15) << 8)
                    );
                    final int neighbourLevel = neighbourNibble.getUpdating((neighbourX & 15) |
                            ((neighbourZ & 15)) << 4 |
                            ((currY & 15) << 8)
                    );

                    if (currentLevel == neighbourLevel && (currentLevel == 0 || currentLevel == 15)) {
                        // nothing to check here
                        continue;
                    }

                    if (Math.abs(currentLevel - neighbourLevel) == 1) {
                        final BlockState currentBlock = this.getBlockState(currX, currY, currZ);
                        final BlockState neighbourBlock = this.getBlockState(neighbourX, currY, neighbourZ);

                        final int currentOpacity = ((ExtendedAbstractBlockState)currentBlock).getOpacityIfCached();
                        final int neighbourOpacity = ((ExtendedAbstractBlockState)neighbourBlock).getOpacityIfCached();
                        if (currentOpacity == 0 || currentOpacity == 1 ||
                                neighbourOpacity == 0 || neighbourOpacity == 1) {
                            // looks good
                            continue;
                        }
                    }

                    // setup queue, it looks like something could be inconsistent
                    this.checkBlock(lightAccess, currX, currY, currZ);
                    this.checkBlock(lightAccess, neighbourX, currY, neighbourZ);
                }
            }
        }
    }

    protected void checkChunkEdges(final IChunkLightProvider lightAccess, final IChunk chunk, final ShortCollection sections) {
        final ChunkPos chunkPos = chunk.getPos();
        final int chunkX = chunkPos.x;
        final int chunkZ = chunkPos.z;

        for (final ShortIterator iterator = sections.iterator(); iterator.hasNext();) {
            this.checkChunkEdge(lightAccess, chunk, chunkX, iterator.nextShort(), chunkZ);
        }

        this.performLightDecrease(lightAccess);
    }

    // subclasses should not initialise caches, as this will always be done by the super call
    // subclasses should not invoke updateVisible, as this will always be done by the super call
    // verifies that light levels on this chunks edges are consistent with this chunk's neighbours
    // edges. if they are not, they are decreased (effectively performing the logic in checkBlock).
    // This does not resolve skylight source problems.
    protected void checkChunkEdges(final IChunkLightProvider lightAccess, final IChunk chunk, final int fromSection, final int toSection) {
        final ChunkPos chunkPos = chunk.getPos();
        final int chunkX = chunkPos.x;
        final int chunkZ = chunkPos.z;

        for (int currSectionY = toSection; currSectionY >= fromSection; --currSectionY) {
            this.checkChunkEdge(lightAccess, chunk, chunkX, currSectionY, chunkZ);
        }

        this.performLightDecrease(lightAccess);
    }

    // pulls light from neighbours, and adds them into the increase queue. does not actually propagate.
    protected final void propagateNeighbourLevels(final IChunkLightProvider lightAccess, final IChunk chunk, final int fromSection, final int toSection) {
        final ChunkPos chunkPos = chunk.getPos();
        final int chunkX = chunkPos.x;
        final int chunkZ = chunkPos.z;

        for (int currSectionY = toSection; currSectionY >= fromSection; --currSectionY) {
            final SWMRNibbleArray currNibble = this.getNibbleFromCache(chunkX, currSectionY, chunkZ);
            if (currNibble == null) {
                continue;
            }
            for (final AxisDirection direction : ONLY_HORIZONTAL_DIRECTIONS) {
                final int neighbourOffX = direction.x;
                final int neighbourOffZ = direction.z;

                final SWMRNibbleArray neighbourNibble = this.getNibbleFromCache(chunkX + neighbourOffX,
                        currSectionY, chunkZ + neighbourOffZ);

                if (neighbourNibble == null || !neighbourNibble.isInitialisedUpdating()) {
                    // can't pull from 0
                    continue;
                }

                final int incX;
                final int incZ;
                final int startX;
                final int startZ;

                if (neighbourOffX != 0) {
                    // x direction
                    incX = 0;
                    incZ = 1;

                    if (direction.x < 0) {
                        // negative
                        startX = (chunkX << 4) - 1;
                    } else {
                        startX = (chunkX << 4) + 16;
                    }
                    startZ = chunkZ << 4;
                } else {
                    // z direction
                    incX = 1;
                    incZ = 0;

                    if (neighbourOffZ < 0) {
                        // negative
                        startZ = (chunkZ << 4) - 1;
                    } else {
                        startZ = (chunkZ << 4) + 16;
                    }
                    startX = chunkX << 4;
                }

                final long propagateDirection = 1L << direction.getOpposite().ordinal(); // we only want to check in this direction towards this chunk
                final int encodeOffset = this.coordinateOffset;

                for (int currY = currSectionY << 4, maxY = currY | 15; currY <= maxY; ++currY) {
                    for (int i = 0, currX = startX, currZ = startZ; i < 16; ++i, currX += incX, currZ += incZ) {
                        final int level = neighbourNibble.getUpdating(
                                (currX & 15)
                                        | ((currZ & 15) << 4)
                                        | ((currY & 15) << 8)
                        );

                        if (level <= 1) {
                            // nothing to propagate
                            continue;
                        }

                        this.increaseQueue[this.increaseQueueInitialLength++] =
                                ((currX + (currZ << 6) + (currY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                        | ((level & 0xFL) << (6 + 6 + 16))
                                        | (propagateDirection << (6 + 6 + 16 + 4))
                                        | FLAG_HAS_SIDED_TRANSPARENT_BLOCKS; // don't know if the current block is transparent, must check.
                    }
                }
            }
        }
    }

    public static Boolean[] getEmptySectionsForChunk(final IChunk chunk) {
        final Boolean[] ret = new Boolean[16];

        final ChunkSection[] sections = chunk.getSections();

        for (int i = 0; i < sections.length; ++i) {
            if (sections[i] == null || sections[i].isEmpty()) {
                ret[i] = Boolean.TRUE;
            } else {
                ret[i] = Boolean.FALSE;
            }
        }

        return ret;
    }

    public final void handleEmptySectionChanges(final IChunkLightProvider lightAccess, final int chunkX, final int chunkZ,
                                                final Boolean[] emptinessChanges) {
        this.setupCaches(lightAccess, chunkX * 16 + 7, 128, chunkZ * 16 + 7, this.isClientSide);
        if (this.isClientSide) {
            // force current chunk into cache
            final IChunk chunk =  (IChunk)lightAccess.getChunkForLight(chunkX, chunkZ);
            if (chunk == null) {
                // unloaded this frame (or last), and we were still queued
                return;
            }
            this.setChunkInCache(chunkX, chunkZ, chunk);
            this.setBlocksForChunkInCache(chunkX, chunkZ, chunk.getSections());
            this.setNibblesForChunkInCache(chunkX, chunkZ, this.getNibblesOnChunk(chunk));
            this.setEmptinessMapCache(chunkX, chunkZ, this.getEmptinessMap(chunk));
        }
        try {
            final IChunk chunk = this.getChunkInCache(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }
            this.handleEmptySectionChanges(lightAccess, chunk, emptinessChanges, false);
            this.updateVisible(lightAccess);
        } finally {
            this.destroyCaches();
        }
    }

    // subclasses should not initialise caches, as this will always be done by the super call
    // subclasses should not invoke updateVisible, as this will always be done by the super call
    // subclasses are guaranteed that this is always called before a changed block set
    // newChunk specifies whether the changes describe a "first load" of a chunk or changes to existing, already loaded chunks
    protected abstract void handleEmptySectionChanges(final IChunkLightProvider lightAccess, final IChunk chunk,
                                                      final Boolean[] emptinessChanges, final boolean unlit);

    public final void checkChunkEdges(final IChunkLightProvider lightAccess, final int chunkX, final int chunkZ) {
        this.setupCaches(lightAccess, chunkX * 16 + 7, 128, chunkZ * 16 + 7, true);
        try {
            final IChunk chunk = this.getChunkInCache(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }
            this.checkChunkEdges(lightAccess, chunk, -1, 16);
            this.updateVisible(lightAccess);
        } finally {
            this.destroyCaches();
        }
    }

    public final void checkChunkEdges(final IChunkLightProvider lightAccess, final int chunkX, final int chunkZ, final ShortCollection sections) {
        this.setupCaches(lightAccess, chunkX * 16 + 7, 128, chunkZ * 16 + 7, true);
        try {
            final IChunk chunk = this.getChunkInCache(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }
            this.checkChunkEdges(lightAccess, chunk, sections);
            this.updateVisible(lightAccess);
        } finally {
            this.destroyCaches();
        }
    }

    // subclasses should not initialise caches, as this will always be done by the super call
    // subclasses should not invoke updateVisible, as this will always be done by the super call
    // needsEdgeChecks applies when possibly loading vanilla data, which means we need to validate the current
    // chunks light values with respect to neighbours
    // subclasses should note that the emptiness changes are propagated BEFORE this is called, so this function
    // does not need to detect empty chunks itself (and it should do no handling for them either!)
    protected abstract void lightChunk(final IChunkLightProvider lightAccess, final IChunk chunk, final boolean needsEdgeChecks);

    public final void light(final IChunkLightProvider lightAccess, final int chunkX, final int chunkZ, final Boolean[] emptySections) {
        this.setupCaches(lightAccess, chunkX * 16 + 7, 128, chunkZ * 16 + 7, false);
        // force current chunk into cache
        final IChunk chunk =  (IChunk)lightAccess.getChunkForLight(chunkX, chunkZ);
        this.setChunkInCache(chunkX, chunkZ, chunk);
        this.setBlocksForChunkInCache(chunkX, chunkZ, chunk.getSections());
        this.setNibblesForChunkInCache(chunkX, chunkZ, this.getNibblesOnChunk(chunk));
        this.setEmptinessMapCache(chunkX, chunkZ, this.getEmptinessMap(chunk));

        try {
            this.handleEmptySectionChanges(lightAccess, chunk, emptySections, true);
            this.lightChunk(lightAccess, chunk, false);
            this.updateVisible(lightAccess);
        } finally {
            this.destroyCaches();
        }
    }

    public final void relight(final IChunkLightProvider lightAccess, final int chunkX, final int chunkZ) {
        final IChunk chunk = (IChunk)lightAccess.getChunkForLight(chunkX, chunkZ);
        this.relightChunk(lightAccess, chunk);
    }

    protected final void relightChunk(final IChunkLightProvider lightAccess, final IChunk chunk) {
        final ChunkPos chunkPos = chunk.getPos();

        // ensure the emptiness map will be correct for the chunk
        this.handleEmptySectionChanges(lightAccess, chunkPos.x, chunkPos.z, getEmptySectionsForChunk(chunk));

        this.setupEncodeOffset(chunkPos.x * 16 + 7, 128, chunkPos.z * 16 + 7);

        try {
            final SWMRNibbleArray[][] chunkNibbles = new SWMRNibbleArray[(2 * 1 + 1) * (2 * 1 + 1)][];
            for (int i = 0; i < chunkNibbles.length; ++i) {
                chunkNibbles[i] = getFilledEmptyLight();
            }

            this.setChunkInCache(chunkPos.x, chunkPos.z, chunk);
            this.setBlocksForChunkInCache(chunkPos.x, chunkPos.z, chunk.getSections());
            this.setNibblesForChunkInCache(chunkPos.x, chunkPos.z, chunkNibbles[ExtendedChunk.getEmptinessMapIndex(0, 0)]);
            this.setEmptinessMapCache(chunkPos.x, chunkPos.z, new boolean[9][]);

            this.handleEmptySectionChanges(lightAccess, chunk, getEmptySectionsForChunk(chunk), true);
            this.lightChunk(lightAccess, chunk, false);

            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    if ((dx | dz) == 0) {
                        continue;
                    }

                    final int cx = dx + chunkPos.x;
                    final int cz = dz + chunkPos.z;
                    final IChunk neighbourChunk = (IChunk)lightAccess.getChunkForLight(cx, cz);

                    if (neighbourChunk == null || !this.canUseChunk(neighbourChunk)) {
                        continue;
                    }

                    for (int dz2 = -1; dz2 <= 1; ++dz2) {
                        for (int dx2 = -1; dx2 <= 1; ++dx2) {
                            final IChunk neighbour = this.getChunkInCache(dx2 + chunkPos.x, dz2 + chunkPos.z);
                            if (neighbour == null) {
                                continue;
                            }

                            // re-insert nibbles for chunk, they might have been removed due to the emptiness map
                            this.setNibblesForChunkInCache(dx2 + chunkPos.x, dz2 + chunkPos.z, chunkNibbles[ExtendedChunk.getEmptinessMapIndex(dx2, dz2)]);
                        }
                    }

                    this.setChunkInCache(cx, cz, neighbourChunk);
                    this.setBlocksForChunkInCache(cx, cz, neighbourChunk.getSections());
                    this.setNibblesForChunkInCache(cx, cz, chunkNibbles[ExtendedChunk.getEmptinessMapIndex(dx, dz)]);
                    this.setEmptinessMapCache(cx, cz, new boolean[9][]);

                    this.handleEmptySectionChanges(lightAccess, neighbourChunk, getEmptySectionsForChunk(neighbourChunk), true);
                    this.lightChunk(lightAccess, neighbourChunk, false);
                }
            }

            for (final SWMRNibbleArray nibble : chunkNibbles[ExtendedChunk.getEmptinessMapIndex(0, 0)]) {
                nibble.updateVisible();
            }

            this.setNibbles(chunk, chunkNibbles[ExtendedChunk.getEmptinessMapIndex(0, 0)]);

            for (int y = -1; y <= 16; ++y) {
                lightAccess.markLightChanged(this.skylightPropagator ? LightType.SKY : LightType.BLOCK, SectionPos.of(chunkPos.x, y, chunkPos.z));
            }
        } finally {
            this.destroyCaches();
        }
    }

    public final void relightChunks(final IChunkLightProvider lightAccess, final Set<ChunkPos> chunks,
                                    final Consumer<ChunkPos> chunkLightCallback) {
        // it's recommended for maximum performance that the set is ordered according to a BFS from the center of
        // the region of chunks to relight
        // it's required that tickets are added for each chunk to keep them loaded
        final Long2ObjectOpenHashMap<SWMRNibbleArray[]> nibblesByChunk = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<boolean[][]> emptinessMapByChunk = new Long2ObjectOpenHashMap<>();

        final int[] neighbourLightOrder = new int[] {
                // d = 0
                0, 0,
                // d = 1
                -1, 0,
                0, -1,
                1, 0,
                0, 1,
                // d = 2
                -1, 1,
                1, 1,
                -1, -1,
                1, -1,
        };

        for (final ChunkPos chunkPos : chunks) {
            final int chunkX = chunkPos.x;
            final int chunkZ = chunkPos.z;
            final IChunk chunk = (IChunk)lightAccess.getChunkForLight(chunkX, chunkZ);
            if (chunk == null || !this.canUseChunk(chunk)) {
                throw new IllegalStateException();
            }

            // force update emptiness map so we can guarantee it's correct after we're done
            this.handleEmptySectionChanges(lightAccess, chunkX, chunkZ, getEmptySectionsForChunk(chunk));

            for (int i = 0, len = neighbourLightOrder.length; i < len; i += 2) {
                final int dx = neighbourLightOrder[i];
                final int dz = neighbourLightOrder[i + 1];
                final int neighbourX = dx + chunkX;
                final int neighbourZ = dz + chunkZ;

                final IChunk neighbour = (IChunk)lightAccess.getChunkForLight(neighbourX, neighbourZ);
                if (neighbour == null || !this.canUseChunk(neighbour)) {
                    continue;
                }

                if (nibblesByChunk.get(CoordinateUtils.getChunkKey(neighbourX, neighbourZ)) != null) {
                    // lit already called for neighbour, no need to light it now
                    continue;
                }

                // light neighbour chunk
                this.setupEncodeOffset(neighbourX * 16 + 7, 128, neighbourZ * 16 + 7);
                try {
                    // insert all neighbouring chunks for this neighbour that we have data for
                    for (int dz2 = -1; dz2 <= 1; ++dz2) {
                        for (int dx2 = -1; dx2 <= 1; ++dx2) {
                            final int neighbourX2 = neighbourX + dx2;
                            final int neighbourZ2 = neighbourZ + dz2;
                            final long key = CoordinateUtils.getChunkKey(neighbourX2, neighbourZ2);
                            final IChunk neighbour2 = (IChunk)lightAccess.getChunkForLight(neighbourX2, neighbourZ2);
                            if (neighbour2 == null || !this.canUseChunk(neighbour2)) {
                                continue;
                            }

                            final SWMRNibbleArray[] nibbles = nibblesByChunk.get(key);
                            if (nibbles == null) {
                                // we haven't lit this chunk
                                continue;
                            }

                            this.setChunkInCache(neighbourX2, neighbourZ2, neighbour2);
                            this.setBlocksForChunkInCache(neighbourX2, neighbourZ2, neighbour2.getSections());
                            this.setNibblesForChunkInCache(neighbourX2, neighbourZ2, nibbles);
                            this.setEmptinessMapCache(neighbourX2, neighbourZ2, emptinessMapByChunk.get(key));
                        }
                    }

                    final long key = CoordinateUtils.getChunkKey(neighbourX, neighbourZ);

                    // now insert the neighbour chunk and light it
                    final SWMRNibbleArray[] nibbles = getFilledEmptyLight();
                    final boolean[][] emptinessMap = new boolean[(2 * 1 + 1) * (2 * 1 + 1)][];
                    nibblesByChunk.put(key, nibbles);
                    emptinessMapByChunk.put(key, emptinessMap);

                    this.setChunkInCache(neighbourX, neighbourZ, neighbour);
                    this.setBlocksForChunkInCache(neighbourX, neighbourZ, neighbour.getSections());
                    this.setNibblesForChunkInCache(neighbourX, neighbourZ, nibbles);
                    this.setEmptinessMapCache(neighbourX, neighbourZ, emptinessMap);

                    this.handleEmptySectionChanges(lightAccess, neighbour, getEmptySectionsForChunk(neighbour), true);
                    this.lightChunk(lightAccess, neighbour, false);
                } finally {
                    this.destroyCaches();
                }
            }

            // done lighting all neighbours, so the chunk is now fully lit

            // make sure nibbles are fully updated before calling back
            final SWMRNibbleArray[] nibbles = nibblesByChunk.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));
            for (final SWMRNibbleArray nibble : nibbles) {
                nibble.updateVisible();
            }

            this.setNibbles(chunk, nibbles);

            for (int y = -1; y <= 16; ++y) {
                lightAccess.markLightChanged(this.skylightPropagator ? LightType.SKY : LightType.BLOCK, SectionPos.of(chunkX, y, chunkX));
            }

            // now do callback
            chunkLightCallback.accept(chunkPos);
        }
    }

    // old algorithm for propagating
    // this is also the basic algorithm, the optimised algorithm is always going to be tested against this one
    // and this one is always tested against vanilla
    // contains:
    // lower (6 + 6 + 16) = 28 bits: encoded coordinate position (x | (z << 6) | (y << (6 + 6))))
    // next 4 bits: propagated light level (0, 15]
    // next 6 bits: propagation direction bitset
    // next 24 bits: unused
    // last 4 bits: state flags
    // state flags:
    // whether the propagation must set the current position's light value (0 if decrease, propagated light level if increase)
    // whether the propagation needs to check if its current level is equal to the expected level
    // used only in increase propagation
    protected static final long FLAG_RECHECK_LEVEL = Long.MIN_VALUE >>> 1;
    // whether the propagation needs to consider if its block is conditionally transparent
    protected static final long FLAG_HAS_SIDED_TRANSPARENT_BLOCKS = Long.MIN_VALUE;

    protected final long[] increaseQueue = new long[16 * 16 * (16 * (16 + 2)) * 9 + 1];
    protected int increaseQueueInitialLength;
    protected final long[] decreaseQueue = new long[16 * 16 * (16 * (16 + 2)) * 9 + 1];
    protected int decreaseQueueInitialLength;

    protected static final AxisDirection[][] OLD_CHECK_DIRECTIONS = new AxisDirection[1 << 6][];
    protected static final int ALL_DIRECTIONS_BITSET = (1 << 6) - 1;
    static {
        for (int i = 0; i < OLD_CHECK_DIRECTIONS.length; ++i) {
            final List<AxisDirection> directions = new ArrayList<>();
            for (int bitset = i, len = Integer.bitCount(i), index = 0; index < len; ++index, bitset ^= IntegerUtil.getTrailingBit(bitset)) {
                directions.add(AXIS_DIRECTIONS[IntegerUtil.trailingZeros(bitset)]);
            }
            OLD_CHECK_DIRECTIONS[i] = directions.toArray(new AxisDirection[0]);
        }
    }

    protected final void performLightIncrease(final IChunkLightProvider lightAccess) {
        final IBlockReader world = lightAccess.getWorld();
        final long[] queue = this.increaseQueue;
        int queueReadIndex = 0;
        int queueLength = this.increaseQueueInitialLength;
        this.increaseQueueInitialLength = 0;
        final int decodeOffsetX = -this.encodeOffsetX;
        final int decodeOffsetY = -this.encodeOffsetY;
        final int decodeOffsetZ = -this.encodeOffsetZ;
        final int encodeOffset = this.coordinateOffset;
        final int sectionOffset = this.chunkSectionIndexOffset;

        while (queueReadIndex < queueLength) {
            final long queueValue = queue[queueReadIndex++];

            final int posX = ((int)queueValue & 63) + decodeOffsetX;
            final int posZ = (((int)queueValue >>> 6) & 63) + decodeOffsetZ;
            final int posY = (((int)queueValue >>> 12) & ((1 << 16) - 1)) + decodeOffsetY;
            final int propagatedLightLevel = (int)((queueValue >>> (6 + 6 + 16)) & 0xFL);
            final AxisDirection[] checkDirections = OLD_CHECK_DIRECTIONS[(int)((queueValue >>> (6 + 6 + 16 + 4)) & 63L)];

            if ((queueValue & FLAG_RECHECK_LEVEL) != 0L) {
                if (this.getLightLevel(posX, posY, posZ) != propagatedLightLevel) {
                    // not at the level we expect, so something changed.
                    continue;
                }
            }

            if ((queueValue & FLAG_HAS_SIDED_TRANSPARENT_BLOCKS) == 0L) {
                // we don't need to worry about our state here.
                for (final AxisDirection propagate : checkDirections) {
                    final int offX = posX + propagate.x;
                    final int offY = posY + propagate.y;
                    final int offZ = posZ + propagate.z;

                    final int sectionIndex = (offX >> 4) + 5 * (offZ >> 4) + (5 * 5) * (offY >> 4) + sectionOffset;
                    final int localIndex = (offX & 15) | ((offZ & 15) << 4) | ((offY & 15) << 8);

                    final SWMRNibbleArray currentNibble = this.nibbleCache[sectionIndex];
                    final int currentLevel;
                    if (currentNibble == null || (currentLevel = currentNibble.getUpdating(localIndex)) >= (propagatedLightLevel - 1)) {
                        continue; // already at the level we want or unloaded
                    }

                    final BlockState blockState = this.getBlockState(sectionIndex, localIndex);
                    if (blockState == null) {
                        continue;
                    }
                    final int opacityCached = ((ExtendedAbstractBlockState)blockState).getOpacityIfCached();
                    if (opacityCached != -1) {
                        final int targetLevel = propagatedLightLevel - Math.max(1, opacityCached);
                        if (targetLevel > currentLevel) {

                            currentNibble.set(localIndex, targetLevel);
                            this.postLightUpdate(offX, offY, offZ);

                            if (targetLevel > 1) {
                                queue[queueLength++] =
                                        ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                                | ((targetLevel & 0xFL) << (6 + 6 + 16))
                                                | (propagate.everythingButTheOppositeDirection << (6 + 6 + 16 + 4));
                                continue;
                            }
                        }
                        continue;
                    } else {
                        this.mutablePos1.setPos(offX, offY, offZ);
                        long flags = 0;
                        if (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque()) {
                            final VoxelShape cullingFace = blockState.getFaceOcclusionShape(world, this.mutablePos1, propagate.getOpposite().nms);

                            if (VoxelShapes.faceShapeCovers(VoxelShapes.empty(), cullingFace)) {
                                continue;
                            }
                            flags |= FLAG_HAS_SIDED_TRANSPARENT_BLOCKS;
                        }

                        final int opacity = blockState.getOpacity(world, this.mutablePos1);
                        final int targetLevel = propagatedLightLevel - Math.max(1, opacity);
                        if (targetLevel <= currentLevel) {
                            continue;
                        }

                        currentNibble.set(localIndex, targetLevel);
                        this.postLightUpdate(offX, offY, offZ);

                        if (targetLevel > 1) {
                            queue[queueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((targetLevel & 0xFL) << (6 + 6 + 16))
                                            | (propagate.everythingButTheOppositeDirection << (6 + 6 + 16 + 4))
                                            | (flags);
                        }
                        continue;
                    }
                }
            } else {
                // we actually need to worry about our state here
                final BlockState fromBlock = this.getBlockState(posX, posY, posZ);
                this.mutablePos2.setPos(posX, posY, posZ);
                for (final AxisDirection propagate : checkDirections) {
                    final int offX = posX + propagate.x;
                    final int offY = posY + propagate.y;
                    final int offZ = posZ + propagate.z;

                    final VoxelShape fromShape = (((ExtendedAbstractBlockState)fromBlock).isConditionallyFullOpaque()) ? fromBlock.getFaceOcclusionShape(world, this.mutablePos2, propagate.nms) : VoxelShapes.empty();

                    if (fromShape != VoxelShapes.empty() && VoxelShapes.faceShapeCovers(VoxelShapes.empty(), fromShape)) {
                        continue;
                    }

                    final int sectionIndex = (offX >> 4) + 5 * (offZ >> 4) + (5 * 5) * (offY >> 4) + sectionOffset;
                    final int localIndex = (offX & 15) | ((offZ & 15) << 4) | ((offY & 15) << 8);

                    final SWMRNibbleArray currentNibble = this.nibbleCache[sectionIndex];
                    final int currentLevel;

                    if (currentNibble == null || (currentLevel = currentNibble.getUpdating(localIndex)) >= (propagatedLightLevel - 1)) {
                        continue; // already at the level we want
                    }

                    final BlockState blockState = this.getBlockState(sectionIndex, localIndex);
                    if (blockState == null) {
                        continue;
                    }
                    final int opacityCached = ((ExtendedAbstractBlockState)blockState).getOpacityIfCached();
                    if (opacityCached != -1) {
                        final int targetLevel = propagatedLightLevel - Math.max(1, opacityCached);
                        if (targetLevel > currentLevel) {

                            currentNibble.set(localIndex, targetLevel);
                            this.postLightUpdate(offX, offY, offZ);

                            if (targetLevel > 1) {
                                queue[queueLength++] =
                                        ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                                | ((targetLevel & 0xFL) << (6 + 6 + 16))
                                                | (propagate.everythingButTheOppositeDirection << (6 + 6 + 16 + 4));
                                continue;
                            }
                        }
                        continue;
                    } else {
                        this.mutablePos1.setPos(offX, offY, offZ);
                        long flags = 0;
                        if (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque()) {
                            final VoxelShape cullingFace = blockState.getFaceOcclusionShape(world, this.mutablePos1, propagate.getOpposite().nms);

                            if (VoxelShapes.faceShapeCovers(fromShape, cullingFace)) {
                                continue;
                            }
                            flags |= FLAG_HAS_SIDED_TRANSPARENT_BLOCKS;
                        }

                        final int opacity = blockState.getOpacity(world, this.mutablePos1);
                        final int targetLevel = propagatedLightLevel - Math.max(1, opacity);
                        if (targetLevel <= currentLevel) {
                            continue;
                        }

                        currentNibble.set(localIndex, targetLevel);
                        this.postLightUpdate(offX, offY, offZ);

                        if (targetLevel > 1) {
                            queue[queueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((targetLevel & 0xFL) << (6 + 6 + 16))
                                            | (propagate.everythingButTheOppositeDirection << (6 + 6 + 16 + 4))
                                            | (flags);
                        }
                        continue;
                    }
                }
            }
        }
    }

    protected final void performLightDecrease(final IChunkLightProvider lightAccess) {
        final IBlockReader world = lightAccess.getWorld();
        final long[] queue = this.decreaseQueue;
        final long[] increaseQueue = this.increaseQueue;
        int queueReadIndex = 0;
        int queueLength = this.decreaseQueueInitialLength;
        this.decreaseQueueInitialLength = 0;
        int increaseQueueLength = this.increaseQueueInitialLength;
        final int decodeOffsetX = -this.encodeOffsetX;
        final int decodeOffsetY = -this.encodeOffsetY;
        final int decodeOffsetZ = -this.encodeOffsetZ;
        final int encodeOffset = this.coordinateOffset;
        final int sectionOffset = this.chunkSectionIndexOffset;
        final int emittedMask = this.emittedLightMask;
        final VariableBlockLightHandler customLightHandler = this.skylightPropagator ? null : ((ExtendedWorld)world).getCustomLightHandler();

        while (queueReadIndex < queueLength) {
            final long queueValue = queue[queueReadIndex++];

            final int posX = ((int)queueValue & 63) + decodeOffsetX;
            final int posZ = (((int)queueValue >>> 6) & 63) + decodeOffsetZ;
            final int posY = (((int)queueValue >>> 12) & ((1 << 16) - 1)) + decodeOffsetY;
            final int propagatedLightLevel = (int)((queueValue >>> (6 + 6 + 16)) & 0xF);
            final AxisDirection[] checkDirections = OLD_CHECK_DIRECTIONS[(int)((queueValue >>> (6 + 6 + 16 + 4)) & 63)];

            if ((queueValue & FLAG_HAS_SIDED_TRANSPARENT_BLOCKS) == 0L) {
                // we don't need to worry about our state here.
                for (final AxisDirection propagate : checkDirections) {
                    final int offX = posX + propagate.x;
                    final int offY = posY + propagate.y;
                    final int offZ = posZ + propagate.z;

                    final int sectionIndex = (offX >> 4) + 5 * (offZ >> 4) + (5 * 5) * (offY >> 4) + sectionOffset;
                    final int localIndex = (offX & 15) | ((offZ & 15) << 4) | ((offY & 15) << 8);

                    final SWMRNibbleArray currentNibble = this.nibbleCache[sectionIndex];
                    final int lightLevel;

                    if (currentNibble == null || (lightLevel = currentNibble.getUpdating(localIndex)) == 0) {
                        // already at lowest (or unloaded), nothing we can do
                        continue;
                    }

                    final BlockState blockState = this.getBlockState(sectionIndex, localIndex);
                    if (blockState == null) {
                        continue;
                    }
                    final int opacityCached = ((ExtendedAbstractBlockState)blockState).getOpacityIfCached();
                    if (opacityCached != -1) {
                        final int targetLevel = Math.max(0, propagatedLightLevel - Math.max(1, opacityCached));
                        if (lightLevel > targetLevel) {
                            // it looks like another source propagated here, so re-propagate it
                            increaseQueue[increaseQueueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((lightLevel & 0xFL) << (6 + 6 + 16))
                                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                            | FLAG_RECHECK_LEVEL;
                            continue;
                        }
                        final int emittedLight = (customLightHandler != null ? this.getCustomLightLevel(customLightHandler, offX, offY, offZ, blockState.getLightValue()) : blockState.getLightValue()) & emittedMask;
                        if (emittedLight != 0) {
                            // re-propagate source
                            increaseQueue[increaseQueueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((emittedLight & 0xFL) << (6 + 6 + 16))
                                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                            | (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0L);
                        }

                        currentNibble.set(localIndex, emittedLight);
                        this.postLightUpdate(offX, offY, offZ);

                        if (targetLevel > 0) { // we actually need to propagate 0 just in case we find a neighbour...
                            queue[queueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((targetLevel & 0xFL) << (6 + 6 + 16))
                                            | ((propagate.everythingButTheOppositeDirection) << (6 + 6 + 16 + 4));
                            continue;
                        }
                        continue;
                    } else {
                        this.mutablePos1.setPos(offX, offY, offZ);
                        long flags = 0;
                        if (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque()) {
                            final VoxelShape cullingFace = blockState.getFaceOcclusionShape(world, this.mutablePos1, propagate.getOpposite().nms);

                            if (VoxelShapes.faceShapeCovers(VoxelShapes.empty(), cullingFace)) {
                                continue;
                            }
                            flags |= FLAG_HAS_SIDED_TRANSPARENT_BLOCKS;
                        }

                        final int opacity = blockState.getOpacity(world, this.mutablePos1);
                        final int targetLevel = Math.max(0, propagatedLightLevel - Math.max(1, opacity));
                        if (lightLevel > targetLevel) {
                            // it looks like another source propagated here, so re-propagate it
                            increaseQueue[increaseQueueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((lightLevel & 0xFL) << (6 + 6 + 16))
                                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                            | (FLAG_RECHECK_LEVEL | flags);
                            continue;
                        }
                        final int emittedLight = (customLightHandler != null ? this.getCustomLightLevel(customLightHandler, offX, offY, offZ, blockState.getLightValue()) : blockState.getLightValue()) & emittedMask;
                        if (emittedLight != 0) {
                            // re-propagate source
                            increaseQueue[increaseQueueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((emittedLight & 0xFL) << (6 + 6 + 16))
                                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                            | flags;
                        }

                        currentNibble.set(localIndex, emittedLight);
                        this.postLightUpdate(offX, offY, offZ);

                        if (targetLevel > 0) {
                            queue[queueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((targetLevel & 0xFL) << (6 + 6 + 16))
                                            | ((propagate.everythingButTheOppositeDirection) << (6 + 6 + 16 + 4))
                                            | flags;
                        }
                        continue;
                    }
                }
            } else {
                // we actually need to worry about our state here
                final BlockState fromBlock = this.getBlockState(posX, posY, posZ);
                this.mutablePos2.setPos(posX, posY, posZ);
                for (final AxisDirection propagate : checkDirections) {
                    final int offX = posX + propagate.x;
                    final int offY = posY + propagate.y;
                    final int offZ = posZ + propagate.z;

                    final int sectionIndex = (offX >> 4) + 5 * (offZ >> 4) + (5 * 5) * (offY >> 4) + sectionOffset;
                    final int localIndex = (offX & 15) | ((offZ & 15) << 4) | ((offY & 15) << 8);

                    final VoxelShape fromShape = (((ExtendedAbstractBlockState)fromBlock).isConditionallyFullOpaque()) ? fromBlock.getFaceOcclusionShape(world, this.mutablePos2, propagate.nms) : VoxelShapes.empty();

                    if (fromShape != VoxelShapes.empty() && VoxelShapes.faceShapeCovers(VoxelShapes.empty(), fromShape)) {
                        continue;
                    }

                    final SWMRNibbleArray currentNibble = this.nibbleCache[sectionIndex];
                    final int lightLevel;

                    if (currentNibble == null || (lightLevel = currentNibble.getUpdating(localIndex)) == 0) {
                        // already at lowest (or unloaded), nothing we can do
                        continue;
                    }

                    final BlockState blockState = this.getBlockState(sectionIndex, localIndex);
                    if (blockState == null) {
                        continue;
                    }
                    final int opacityCached = ((ExtendedAbstractBlockState)blockState).getOpacityIfCached();
                    if (opacityCached != -1) {
                        final int targetLevel = Math.max(0, propagatedLightLevel - Math.max(1, opacityCached));
                        if (lightLevel > targetLevel) {
                            // it looks like another source propagated here, so re-propagate it
                            increaseQueue[increaseQueueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((lightLevel & 0xFL) << (6 + 6 + 16))
                                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                            | FLAG_RECHECK_LEVEL;
                            continue;
                        }
                        final int emittedLight = (customLightHandler != null ? this.getCustomLightLevel(customLightHandler, offX, offY, offZ, blockState.getLightValue()) : blockState.getLightValue()) & emittedMask;
                        if (emittedLight != 0) {
                            // re-propagate source
                            increaseQueue[increaseQueueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((emittedLight & 0xFL) << (6 + 6 + 16))
                                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                            | (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0L);
                        }

                        currentNibble.set(localIndex, emittedLight);
                        this.postLightUpdate(offX, offY, offZ);

                        if (targetLevel > 0) { // we actually need to propagate 0 just in case we find a neighbour...
                            queue[queueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((targetLevel & 0xFL) << (6 + 6 + 16))
                                            | ((propagate.everythingButTheOppositeDirection) << (6 + 6 + 16 + 4));
                            continue;
                        }
                        continue;
                    } else {
                        this.mutablePos1.setPos(offX, offY, offZ);
                        long flags = 0;
                        if (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque()) {
                            final VoxelShape cullingFace = blockState.getFaceOcclusionShape(world, this.mutablePos1, propagate.getOpposite().nms);

                            if (VoxelShapes.faceShapeCovers(fromShape, cullingFace)) {
                                continue;
                            }
                            flags |= FLAG_HAS_SIDED_TRANSPARENT_BLOCKS;
                        }

                        final int opacity = blockState.getOpacity(world, this.mutablePos1);
                        final int targetLevel = Math.max(0, propagatedLightLevel - Math.max(1, opacity));
                        if (lightLevel > targetLevel) {
                            // it looks like another source propagated here, so re-propagate it
                            increaseQueue[increaseQueueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((lightLevel & 0xFL) << (6 + 6 + 16))
                                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                            | (FLAG_RECHECK_LEVEL | flags);
                            continue;
                        }
                        final int emittedLight = (customLightHandler != null ? this.getCustomLightLevel(customLightHandler, offX, offY, offZ, blockState.getLightValue()) : blockState.getLightValue()) & emittedMask;
                        if (emittedLight != 0) {
                            // re-propagate source
                            increaseQueue[increaseQueueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((emittedLight & 0xFL) << (6 + 6 + 16))
                                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                            | flags;
                        }

                        currentNibble.set(localIndex, emittedLight);
                        this.postLightUpdate(offX, offY, offZ);

                        if (targetLevel > 0) { // we actually need to propagate 0 just in case we find a neighbour...
                            queue[queueLength++] =
                                    ((offX + (offZ << 6) + (offY << 12) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                            | ((targetLevel & 0xFL) << (6 + 6 + 16))
                                            | ((propagate.everythingButTheOppositeDirection) << (6 + 6 + 16 + 4))
                                            | flags;
                        }
                        continue;
                    }
                }
            }
        }

        // propagate sources we clobbered
        this.increaseQueueInitialLength = increaseQueueLength;
        this.performLightIncrease(lightAccess);
    }
}
