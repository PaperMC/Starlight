package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.blockstate.LightAccessBlockState;
import ca.spottedleaf.starlight.common.chunk.NibbledChunk;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ReadOnlyChunk;
import net.minecraft.world.chunk.WorldChunk;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class BlockStarLightEngine extends StarLightEngine {

    public BlockStarLightEngine(final boolean isClientSide) {
        super(false, isClientSide);
    }

    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(final Chunk chunk) {
        return ((NibbledChunk)chunk).getBlockNibbles();
    }

    @Override
    protected void setNibbles(final Chunk chunk, final SWMRNibbleArray[] to) {
        ((NibbledChunk)chunk).setBlockNibbles(to);
    }

    @Override
    protected boolean canUseChunk(final Chunk chunk) {
        return chunk.getStatus().isAtLeast(ChunkStatus.LIGHT) && (this.isClientSide || chunk.isLightOn());
    }

    @Override
    protected final void checkBlock(final int worldX, final int worldY, final int worldZ) {
        // blocks can change opacity
        // blocks can change emitted light
        // blocks can change direction of propagation

        final int encodeOffset = this.coordinateOffset;
        final int emittedMask = this.emittedLightMask;

        final int currentLevel = this.getLightLevel(worldX, worldY, worldZ);
        final BlockState blockState = this.getBlockState(worldX, worldY, worldZ);
        final int emittedLevel = blockState.getLuminance() & emittedMask;

        this.setLightLevel(worldX, worldY, worldZ, emittedLevel);
        // this accounts for change in emitted light that would cause an increase
        if (emittedLevel != 0) {
            this.increaseQueue[this.increaseQueueInitialLength++] = (worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) |
                    emittedLevel << (6 + 6 + 9) |
                    ((AxisDirection.POSITIVE_X.ordinal() | 8) << (6 + 6 + 9 + 4)) |
                    (((LightAccessBlockState)blockState).isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0);
        }
        // this also accounts for a change in emitted light that would cause a decrease
        // this also accounts for the change of direction of propagation (i.e old block was full transparent, new block is full opaque or vice versa)
        // as it checks all neighbours (even if current level is 0)
        this.decreaseQueue[this.decreaseQueueInitialLength++] = (worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) |
                currentLevel << (6 + 6 + 9) |
                ((AxisDirection.POSITIVE_X.ordinal() | 8) << (6 + 6 + 9 + 4));
                // always keep sided transparent false here, new block might be conditionally transparent which would
                // prevent us from decreasing sources in the directions where the new block is opaque
                // if it turns out we were wrong to de-propagate the source, the re-propagate logic WILL always
                // catch that and fix it.
        // re-propagating neighbours (done by the decrease queue) will also account for opacity changes in this block
    }

    @Override
    protected void propagateBlockChanges(final ChunkProvider lightAccess, final Chunk atChunk,
                                         final Set<BlockPos> positions) {
        for (final BlockPos pos : positions) {
            this.checkBlock(pos.getX(), pos.getY(), pos.getZ());
        }

        this.performLightDecrease(lightAccess);
    }

    protected Iterator<BlockPos> getSources(final Chunk chunk) {
        if (chunk instanceof ReadOnlyChunk || chunk instanceof WorldChunk) {
            // implementation on Chunk is pretty awful, so write our own here. The big optimisation is
            // skipping empty sections, and the far more optimised reading of types.
            List<BlockPos> sources = new ArrayList<>();

            int offX = chunk.getPos().x << 4;
            int offZ = chunk.getPos().z << 4;

            final ChunkSection[] sections = chunk.getSectionArray();
            for (int sectionY = 0; sectionY <= 15; ++sectionY) {
                if (sections[sectionY] == null || sections[sectionY].isEmpty()) {
                    // no sources in empty sections
                    continue;
                }
                final ChunkSection section = sections[sectionY];

                for (int localY = 0; localY <= 15; ++localY) {
                    final int realY = localY | (sectionY << 4);
                    for (int localZ = 0; localZ <= 15; ++localZ) {
                        for (int localX = 0; localX <= 15; ++localX) {
                            final BlockState blockState = section.getBlockState(localX, localY, localZ);
                            if (blockState.getLuminance() <= 0) {
                                continue;
                            }

                            sources.add(new BlockPos(offX + localX, realY, offZ + localZ));
                        }
                    }
                }
            }

            return sources.iterator();
        } else {
            return chunk.getLightSourcesStream().iterator();
        }
    }

    @Override
    public void lightChunk(final ChunkProvider lightAccess, final Chunk chunk, final boolean needsEdgeChecks) {
        // setup sources
        final int emittedMask = this.emittedLightMask;
        for (final Iterator<BlockPos> positions = this.getSources(chunk); positions.hasNext();) {
            final BlockPos pos = positions.next();
            final BlockState blockState = this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
            final int emittedLight = blockState.getLuminance() & emittedMask;

            if (emittedLight <= this.getLightLevel(pos.getX(), pos.getY(), pos.getZ())) {
                // some other source is brighter
                continue;
            }

            this.increaseQueue[this.increaseQueueInitialLength++] = (pos.getX() + (pos.getZ() << 6) + (pos.getY() << (6 + 6)) + this.coordinateOffset) |
                    (emittedLight) << (6 + 6 + 9) |
                    ((AxisDirection.POSITIVE_X.ordinal() | 8) << (6 + 6 + 9 + 4)) |
                    (((LightAccessBlockState)blockState).isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0);


            // propagation wont set this for us
            this.setLightLevel(pos.getX(), pos.getY(), pos.getZ(), emittedLight);
        }

        if (needsEdgeChecks) {
            // not required to propagate here, but this will reduce the hit of the edge checks
            this.performLightIncrease(lightAccess);

            // verify neighbour edges
            this.checkChunkEdges(lightAccess, chunk);
        } else {
            this.propagateNeighbourLevels(lightAccess, chunk, -1, 16);

            this.performLightIncrease(lightAccess);
        }
    }
}
