package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.ReadOnlyChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ReadOnlyChunk.class)
public abstract class ReadOnlyChunkMixin /*extends ProtoChunk*/ implements Chunk, ExtendedChunk {
    // TODO fix extends later, it appears due to some bad mappings we can't extend.

    @Final
    @Shadow
    private WorldChunk wrapped;

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return ((ExtendedChunk)this.wrapped).getBlockNibbles();
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        ((ExtendedChunk)this.wrapped).setBlockNibbles(nibbles);
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return ((ExtendedChunk)this.wrapped).getSkyNibbles();
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        ((ExtendedChunk)this.wrapped).setSkyNibbles(nibbles);
    }

    @Override
    public boolean[] getSkyEmptinessMap() {
        return ((ExtendedChunk)this.wrapped).getSkyEmptinessMap();
    }

    @Override
    public void setSkyEmptinessMap(final boolean[] emptinessMap) {
        ((ExtendedChunk)this.wrapped).setSkyEmptinessMap(emptinessMap);
    }

    @Override
    public boolean[] getBlockEmptinessMap() {
        return ((ExtendedChunk)this.wrapped).getBlockEmptinessMap();
    }

    @Override
    public void setBlockEmptinessMap(final boolean[] emptinessMap) {
        ((ExtendedChunk)this.wrapped).setBlockEmptinessMap(emptinessMap);
    }
}
