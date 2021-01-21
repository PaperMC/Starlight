package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.palette.UpgradeData;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.ChunkPrimerWrapper;
import net.minecraft.world.chunk.IChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkPrimerWrapper.class)
public abstract class ChunkPrimerWrapperMixin extends ChunkPrimer implements IChunk, ExtendedChunk {

    @Final
    @Shadow
    private Chunk chunk;

    public ChunkPrimerWrapperMixin(final ChunkPos pos, final UpgradeData data) {
        super(pos, data);
    }

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return ((ExtendedChunk)this.chunk).getBlockNibbles();
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        ((ExtendedChunk)this.chunk).setBlockNibbles(nibbles);
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return ((ExtendedChunk)this.chunk).getSkyNibbles();
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        ((ExtendedChunk)this.chunk).setSkyNibbles(nibbles);
    }

    @Override
    public boolean[] getSkyEmptinessMap() {
        return ((ExtendedChunk)this.chunk).getSkyEmptinessMap();
    }

    @Override
    public void setSkyEmptinessMap(final boolean[] emptinessMap) {
        ((ExtendedChunk)this.chunk).setSkyEmptinessMap(emptinessMap);
    }

    @Override
    public boolean[] getBlockEmptinessMap() {
        return ((ExtendedChunk)this.chunk).getBlockEmptinessMap();
    }

    @Override
    public void setBlockEmptinessMap(final boolean[] emptinessMap) {
        ((ExtendedChunk)this.chunk).setBlockEmptinessMap(emptinessMap);
    }
}
