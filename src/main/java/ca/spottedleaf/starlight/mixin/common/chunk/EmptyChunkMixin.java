package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EmptyChunk.class)
public abstract class EmptyChunkMixin extends Chunk implements IChunk, ExtendedChunk {

    public EmptyChunkMixin(final World world, final ChunkPrimer protoChunk) {
        super(world, protoChunk);
    }

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return StarLightEngine.getFilledEmptyLight();
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {}

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return StarLightEngine.getFilledEmptyLight();
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {}

    @Override
    public boolean[][] getEmptinessMap() {
        return new boolean[9][];
    }
}
