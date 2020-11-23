package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.chunk.NibbledChunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ReadOnlyChunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ReadOnlyChunk.class)
public abstract class ReadOnlyChunkMixin implements NibbledChunk, Chunk {

    @Shadow @Final private WorldChunk wrapped;

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return ((NibbledChunk)this.wrapped).getBlockNibbles();
    }

    @Override
    public void setBlockNibbles(SWMRNibbleArray[] nibbles) {
        ((NibbledChunk)this.wrapped).setBlockNibbles(nibbles);
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return ((NibbledChunk)this.wrapped).getSkyNibbles();
    }

    @Override
    public void setSkyNibbles(SWMRNibbleArray[] nibbles) {
        ((NibbledChunk)this.wrapped).setSkyNibbles(nibbles);
    }
}
