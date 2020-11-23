package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.chunk.NibbledChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EmptyChunk.class)
public abstract class EmptyChunkMixin implements NibbledChunk, Chunk {

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return StarLightEngine.getFilledEmptyLight(false);
    }

    @Override
    public void setBlockNibbles(SWMRNibbleArray[] nibbles) {}

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return StarLightEngine.getFilledEmptyLight(true);
    }

    @Override
    public void setSkyNibbles(SWMRNibbleArray[] nibbles) {}
}
