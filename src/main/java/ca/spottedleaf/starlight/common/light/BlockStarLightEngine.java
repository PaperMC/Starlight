package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.palette.PalettedContainer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimerWrapper;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.IChunkLightProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class BlockStarLightEngine extends StarLightEngine {

    public BlockStarLightEngine(final World world) {
        super(false, world);
    }

    @Override
    protected boolean[] getEmptinessMap(final IChunk chunk) {
        return null;
    }

    @Override
    protected void setEmptinessMap(final IChunk chunk, final boolean[] to) {}

    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(final IChunk chunk) {
        return ((ExtendedChunk)chunk).getBlockNibbles();
    }

    @Override
    protected void setNibbles(final IChunk chunk, final SWMRNibbleArray[] to) {
        ((ExtendedChunk)chunk).setBlockNibbles(to);
    }

    @Override
    protected boolean canUseChunk(final IChunk chunk) {
        return chunk.getStatus().isAtLeast(ChunkStatus.LIGHT) && (this.isClientSide || chunk.hasLight());
    }

    @Override
    protected boolean[] handleEmptySectionChanges(final IChunkLightProvider lightAccess, final IChunk chunk,
                                                  final Boolean[] emptinessChanges, final boolean unlit) {
        return null;
    }

    @Override
    protected final void checkBlock(final IChunkLightProvider lightAccess, final int worldX, final int worldY, final int worldZ) {
        // blocks can change opacity
        // blocks can change emitted light
        // blocks can change direction of propagation

        final int encodeOffset = this.coordinateOffset;
        final int emittedMask = this.emittedLightMask;

        final VariableBlockLightHandler customBlockHandler = ((ExtendedWorld)lightAccess.getWorld()).getCustomLightHandler();
        final int currentLevel = this.getLightLevel(worldX, worldY, worldZ);
        final BlockState blockState = this.getBlockState(worldX, worldY, worldZ);
        final int emittedLevel = (customBlockHandler != null ? this.getCustomLightLevel(customBlockHandler, worldX, worldY, worldZ, blockState.getLightValue()) : blockState.getLightValue()) & emittedMask;

        this.setLightLevel(worldX, worldY, worldZ, emittedLevel);
        // this accounts for change in emitted light that would cause an increase
        if (emittedLevel != 0) {
            this.appendToIncreaseQueue(
                    ((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                            | (emittedLevel & 0xFL) << (6 + 6 + 16)
                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                            | (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0)
            );
        }
        // this also accounts for a change in emitted light that would cause a decrease
        // this also accounts for the change of direction of propagation (i.e old block was full transparent, new block is full opaque or vice versa)
        // as it checks all neighbours (even if current level is 0)
        this.appendToDecreaseQueue(
                ((worldX + (worldZ << 6) + (worldY << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                        | (currentLevel & 0xFL) << (6 + 6 + 16)
                        | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                        // always keep sided transparent false here, new block might be conditionally transparent which would
                        // prevent us from decreasing sources in the directions where the new block is opaque
                        // if it turns out we were wrong to de-propagate the source, the re-propagate logic WILL always
                        // catch that and fix it.
        );
        // re-propagating neighbours (done by the decrease queue) will also account for opacity changes in this block
    }

    @Override
    protected void propagateBlockChanges(final IChunkLightProvider lightAccess, final IChunk atChunk, final Set<BlockPos> positions) {
        for (final BlockPos pos : positions) {
            this.checkBlock(lightAccess, pos.getX(), pos.getY(), pos.getZ());
        }

        this.performLightDecrease(lightAccess);
    }

    protected Iterator<BlockPos> getSources(final IChunkLightProvider lightAccess, final IChunk chunk) {
        if (chunk instanceof ChunkPrimerWrapper || chunk instanceof Chunk) {
            // implementation on Chunk is pretty awful, so write our own here. The big optimisation is
            // skipping empty sections, and the far more optimised reading of types.
            List<BlockPos> sources = new ArrayList<>();

            int offX = chunk.getPos().x << 4;
            int offZ = chunk.getPos().z << 4;

            final ChunkSection[] sections = chunk.getSections();
            for (int sectionY = 0; sectionY <= 15; ++sectionY) {
                if (sections[sectionY] == null || sections[sectionY].isEmpty()) {
                    // no sources in empty sections
                    continue;
                }
                final PalettedContainer<BlockState> section = sections[sectionY].data;
                final int offY = sectionY << 4;

                for (int index = 0; index < (16 * 16 * 16); ++index) {
                    final BlockState state = section.get(index);
                    if (state.getLightValue() <= 0) {
                        continue;
                    }

                    // index = x | (z << 4) | (y << 8)
                    sources.add(new BlockPos(offX | (index & 15), offY | (index >>> 8), offZ | ((index >>> 4) & 15)));
                }
            }

            final VariableBlockLightHandler customBlockHandler = ((ExtendedWorld)lightAccess.getWorld()).getCustomLightHandler();
            if (customBlockHandler == null) {
                return sources.iterator();
            }

            final Set<BlockPos> ret = new HashSet<>(sources);
            ret.addAll(customBlockHandler.getCustomLightPositions(chunk.getPos().x, chunk.getPos().z));

            return ret.iterator();
        } else {
            return chunk.getLightSources().iterator();
        }
    }

    @Override
    public void lightChunk(final IChunkLightProvider lightAccess, final IChunk chunk, final boolean needsEdgeChecks) {
        // setup sources
        final int emittedMask = this.emittedLightMask;
        final VariableBlockLightHandler customBlockHandler = ((ExtendedWorld)lightAccess.getWorld()).getCustomLightHandler();
        for (final Iterator<BlockPos> positions = this.getSources(lightAccess, chunk); positions.hasNext();) {
            final BlockPos pos = positions.next();
            final BlockState blockState = this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
            final int emittedLight = (customBlockHandler != null ? this.getCustomLightLevel(customBlockHandler, pos.getX(), pos.getY(), pos.getZ(), blockState.getLightValue()) : blockState.getLightValue()) & emittedMask;

            if (emittedLight <= this.getLightLevel(pos.getX(), pos.getY(), pos.getZ())) {
                // some other source is brighter
                continue;
            }

            this.appendToIncreaseQueue(
                    ((pos.getX() + (pos.getZ() << 6) + (pos.getY() << (6 + 6)) + this.coordinateOffset) & ((1L << (6 + 6 + 16)) - 1))
                            | (emittedLight & 0xFL) << (6 + 6 + 16)
                            | (((long)ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                            | (((ExtendedAbstractBlockState)blockState).isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0)
            );


            // propagation wont set this for us
            this.setLightLevel(pos.getX(), pos.getY(), pos.getZ(), emittedLight);
        }

        if (needsEdgeChecks) {
            // not required to propagate here, but this will reduce the hit of the edge checks
            this.performLightIncrease(lightAccess);

            // verify neighbour edges
            this.checkChunkEdges(lightAccess, chunk, this.minLightSection, this.maxLightSection);
        } else {
            this.propagateNeighbourLevels(lightAccess, chunk, this.minLightSection, this.maxLightSection);

            this.performLightIncrease(lightAccess);
        }
    }
}
