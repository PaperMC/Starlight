package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.blockstate.LightAccessBlockState;
import ca.spottedleaf.starlight.common.chunk.NibbledChunk;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import java.util.Arrays;
import java.util.Set;

public final class SkyStarLightEngine extends StarLightEngine {

    public SkyStarLightEngine(final boolean isClientSide) {
        super(true, isClientSide);
    }

    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(final Chunk chunk) {
        return ((NibbledChunk)chunk).getSkyNibbles();
    }

    @Override
    protected void setNibbles(final Chunk chunk, final SWMRNibbleArray[] to) {
        ((NibbledChunk)chunk).setSkyNibbles(to);
    }

    @Override
    protected boolean canUseChunk(final Chunk chunk) {
        return chunk.getStatus().isAtLeast(ChunkStatus.LIGHT) && (this.isClientSide || chunk.isLightOn());
    }

    @Override
    protected final void checkBlock(final int worldX, final int worldY, final int worldZ) {
        // blocks can change opacity
        // blocks can change direction of propagation

        // same logic applies from BlockStarLightEngine#checkBlock

        final int encodeOffset = this.coordinateOffset;

        final int currentLevel = this.getLightLevel(worldX, worldY, worldZ);

        if (currentLevel == 15) {
            // must re-propagate clobbered source
            this.increaseQueue[this.increaseQueueInitialLength++] = (worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) |
                    currentLevel << (6 + 6 + 9) |
                    ((AxisDirection.POSITIVE_X.ordinal() | 8) << (6 + 6 + 9 + 4)) |
                    (FLAG_HAS_SIDED_TRANSPARENT_BLOCKS); // don't know if the block is conditionally transparent
        } else {
            this.setLightLevel(worldX, worldY, worldZ, 0);
        }

        this.decreaseQueue[this.decreaseQueueInitialLength++] = (worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) |
                (currentLevel) << (6 + 6 + 9) |
                ((AxisDirection.POSITIVE_X.ordinal() | 8) << (6 + 6 + 9 + 4));
    }

    protected final int[] heightMap = new int[16 * 16];
    {
        Arrays.fill(this.heightMap, -1024); // clear heightmap
    }

    @Override
    protected void propagateBlockChanges(final ChunkProvider lightAccess, final Chunk atChunk,
                                         final Set<BlockPos> positions) {
        final BlockView world = lightAccess.getWorld();
        final int chunkX = atChunk.getPos().x;
        final int chunkZ = atChunk.getPos().z;
        final int heightMapOffset = chunkX * -16 + (chunkZ * (-16 * 16));

        // setup heightmap for changes
        int highestBlockY = -1024;
        for (final BlockPos pos : positions) {
            final int index = pos.getX() + (pos.getZ() << 4) + heightMapOffset;
            final int curr = this.heightMap[index];
            if (pos.getY() > curr) {
                this.heightMap[index] = pos.getY();
            }
            if (pos.getY() > highestBlockY) {
                highestBlockY = pos.getY();
            }
        }

        // now we can recalculate the sources for the changed columns
        for (int index = 0; index < (16 * 16); ++index) {
            final int maxY = this.heightMap[index];
            if (maxY == -1024) {
                // not changed
                continue;
            }
            this.heightMap[index] = -1024; // restore default for next caller

            final int columnX = (index & 15) | (chunkX << 4);
            final int columnZ = (index >>> 4) | (chunkZ << 4);

            // try and propagate from the above y
            int maxPropagationY = this.tryPropagateSkylight(world, columnX, maxY, columnZ);

            // maxPropagationY is now the highest block that could not be propagated to

            // remove all sources below that are 15
            final int propagateDirection = AxisDirection.NEGATIVE_Y.ordinal();
            final int encodeOffset = this.coordinateOffset;
            for (int currY = maxPropagationY; currY >= -15; --currY) {
                if (this.getLightLevel(columnX, currY, columnZ) != 15) {
                    break;
                }
                this.setLightLevel(columnX, currY, columnZ, 0);
                this.decreaseQueue[this.decreaseQueueInitialLength++] = (columnX + (columnZ << 6) + (currY << (6 + 6)) + encodeOffset) |
                        (15 << (6 + 6 + 9)) |
                        ((propagateDirection) << (6 + 6 + 9 + 4));
                        // do not set transparent blocks for the same reason we don't in the checkBlock method
            }
        }

        // we need to initialise nibbles up to the highest section (we don't save null nibbles)
        for (int y = -1; y <= Math.min(16, (highestBlockY >> 4)); ++y) {
            final SWMRNibbleArray nibble = this.getNibbleFromCache(chunkX, y, chunkZ);
            nibble.markNonNull();
        }

        for (final BlockPos pos : positions) {
            this.checkBlock(pos.getX(), pos.getY(), pos.getZ());
        }

        this.performLightDecrease(lightAccess);
    }

    protected void initLightNeighbours(final int chunkX, final int chunkZ) {
        // vanilla requires that written nibble data has initialised nibble data in 1 radius
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                Chunk chunk = this.getChunkInCache(dx + chunkX, dz + chunkZ);
                if (chunk == null) {
                    continue;
                }
                // find lowest section
                int lowest = 15;
                ChunkSection[] sections = chunk.getSectionArray();
                for (;lowest > 0 && (sections[lowest] == null || sections[lowest].isEmpty()); --lowest) {}

                if (lowest == -1) {
                    continue;
                }

                for (int y = lowest; y >= -1; --y) {
                    SWMRNibbleArray nibble = this.getNibbleFromCache(dx + chunkX, y, dz + chunkZ);
                    if (nibble != null && !nibble.isDirty() && nibble.isInitialisedUpdating()) {
                        for (int dy = -1; dy <= 1; ++dy) {
                            SWMRNibbleArray ours = this.getNibbleFromCache(chunkX, dy + y, chunkZ);
                            if (ours != null && !ours.isDirty() && ours.isNullNibbleUpdating()) {
                                ours.initialiseWorking();
                                ours.updateVisible();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void lightChunk(final ChunkProvider lightAccess, final Chunk chunk, final boolean needsEdgeChecks) {
        final BlockView world = lightAccess.getWorld();
        final ChunkPos chunkPos = chunk.getPos();
        final int chunkX = chunkPos.x;
        final int chunkZ = chunkPos.z;

        final ChunkSection[] sections = chunk.getSectionArray();
        final SWMRNibbleArray[] originalNibbles = this.getNibblesForChunkFromCache(chunkX, chunkZ);

        int highestNonEmptySection = 16;
        while (highestNonEmptySection == -1 || highestNonEmptySection == 16 ||
                sections[highestNonEmptySection] == null || sections[highestNonEmptySection].isEmpty()) {
            // try propagate FULL to neighbours

            // check neighbours to see if we need to propagate into them
            for (final AxisDirection direction : ONLY_HORIZONTAL_DIRECTIONS) {
                final int neighbourX = chunkX + direction.x;
                final int neighbourZ = chunkZ + direction.z;
                final SWMRNibbleArray neighbourNibble = this.getNibbleFromCache(neighbourX, highestNonEmptySection, neighbourZ);
                if (neighbourNibble == null) {
                    // unloaded neighbour
                    continue;
                }
                if (neighbourNibble.isNullNibbleUpdating()) {
                    // most of the time we fall here
                    // no point of propagating full light into full light
                    continue;
                }

                // it looks like we need to propagate into the neighbour

                final int incX;
                final int incZ;
                final int startX;
                final int startZ;

                if (direction.x != 0) {
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

                    if (direction.z < 0) {
                        // negative
                        startZ = chunkZ << 4;
                    } else {
                        startZ = chunkZ << 4 | 15;
                    }
                    startX = chunkX << 4;
                }

                final int encodeOffset = this.coordinateOffset;
                final int propagateDirection = direction.ordinal() | 16; // we only want to check in this direction

                for (int currY = highestNonEmptySection << 4, maxY = currY | 15; currY <= maxY; ++currY) {
                    for (int i = 0, currX = startX, currZ = startZ; i < 16; ++i, currX += incX, currZ += incZ) {
                        this.increaseQueue[this.increaseQueueInitialLength++] = (currX + (currZ << 6) + (currY << (6 + 6)) + encodeOffset) |
                                (15 << (6 + 6 + 9)) | // we know we're at full lit here
                                ((propagateDirection) << (6 + 6 + 9 + 4));
                        // no transparent flag, we know for a fact there are no blocks here that could be directionally transparent (as the section is EMPTY)
                    }
                }
            }

            if (highestNonEmptySection-- == -1) {
                break;
            }
        }

        if (highestNonEmptySection >= 0) {
            // mark the rest of our nibbles as 0
            for (int currY = highestNonEmptySection; currY >= -1; --currY) {
                this.getNibbleFromCache(chunkX, currY, chunkZ).markNonNull();
            }

            // fill out our other sources
            final int minX = chunkPos.x << 4;
            final int maxX = chunkPos.x << 4 | 15;
            final int minZ = chunkPos.z << 4;
            final int maxZ = chunkPos.z << 4 | 15;
            final int startY = highestNonEmptySection << 4 | 15;
            for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                for (int currX = minX; currX <= maxX; ++currX) {
                    this.tryPropagateSkylight(world, currX, startY, currZ);
                }
            }
        } // else: apparently the chunk is empty

        if (needsEdgeChecks) {
            // not required to propagate here, but this will reduce the hit of the edge checks
            this.performLightIncrease(lightAccess);

            this.checkChunkEdges(lightAccess, chunk);
        } else {
            this.propagateNeighbourLevels(lightAccess, chunk, -1, highestNonEmptySection);

            this.performLightIncrease(lightAccess);
        }

        this.initLightNeighbours(chunkPos.x, chunkPos.z);
    }

    protected final int tryPropagateSkylight(final BlockView world, final int worldX, final int startY, final int worldZ) {
        final BlockPos.Mutable mutablePos = this.mutablePos3;
        final int encodeOffset = this.coordinateOffset;
        final int propagateDirection = AxisDirection.NEGATIVE_Y.ordinal(); // just don't check upwards.

        if (this.getLightLevel(worldX, startY + 1, worldZ) != 15) {
            return startY;
        }

        BlockState above = this.getBlockState(worldX, startY + 1, worldZ);
        if (above == null) {
            above = AIR_BLOCK_STATE;
        }

        int maxPropagationY;
        for (maxPropagationY = startY; maxPropagationY >= -15; --maxPropagationY) {
            BlockState current = this.getBlockState(worldX, maxPropagationY, worldZ);
            if (current == null) {
                current = AIR_BLOCK_STATE;
            }

            final VoxelShape fromShape;
            if (((LightAccessBlockState)above).isConditionallyFullOpaque()) {
                this.mutablePos2.set(worldX, maxPropagationY + 1, worldZ);
                fromShape = above.getCullingFace(world, this.mutablePos2, AxisDirection.NEGATIVE_Y.nms);
                if (VoxelShapes.unionCoversFullCube(VoxelShapes.empty(), fromShape)) {
                    // above wont let us propagate
                    break;
                }
            } else {
                fromShape = VoxelShapes.empty();
            }

            final int opacityIfCached = ((LightAccessBlockState)current).getOpacityIfCached();
            // does light propagate from the top down?
            if (opacityIfCached != -1) {
                if (opacityIfCached != 0) {
                    // we cannot propagate 15 through this
                    break;
                }
                // most of the time it falls here.
                this.setLightLevel(worldX, maxPropagationY, worldZ, 15);
                // add to propagate
                this.increaseQueue[this.increaseQueueInitialLength++] = (worldX + (worldZ << 6) + (maxPropagationY << (6 + 6)) + encodeOffset) |
                        (15 << (6 + 6 + 9)) | // we know we're at full lit here
                        ((propagateDirection) << (6 + 6 + 9 + 4));
            } else {
                mutablePos.set(worldX, maxPropagationY, worldZ);
                int flags = 0;
                if (((LightAccessBlockState)current).isConditionallyFullOpaque()) {
                    final VoxelShape cullingFace = current.getCullingFace(world, mutablePos, AxisDirection.POSITIVE_Y.nms);

                    if (VoxelShapes.unionCoversFullCube(fromShape, cullingFace)) {
                        // can't propagate here, we're done on this column.
                        break;
                    }
                    flags |= FLAG_HAS_SIDED_TRANSPARENT_BLOCKS;
                }

                final int opacity = current.getOpacity(world, mutablePos);
                if (opacity > 0) {
                    // let the queued value (if any) handle it from here.
                    break;
                }

                this.setLightLevel(worldX, maxPropagationY, worldZ, 15);
                this.increaseQueue[this.increaseQueueInitialLength++] = (worldX + (worldZ << 6) + (maxPropagationY << (6 + 6)) + encodeOffset) |
                        (15 << (6 + 6 + 9)) | // we know we're at full lit here
                        ((propagateDirection) << (6 + 6 + 9 + 4)) |
                        flags;
            }

            above = current;
        }

        return maxPropagationY;
    }
}
