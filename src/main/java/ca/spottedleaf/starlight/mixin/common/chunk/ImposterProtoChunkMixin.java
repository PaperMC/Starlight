package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImposterProtoChunk.class)
public abstract class ImposterProtoChunkMixin extends ProtoChunk implements ChunkAccess, ExtendedChunk {

    @Final
    @Shadow
    private LevelChunk wrapped;

    public ImposterProtoChunkMixin(final ChunkPos chunkPos, final UpgradeData upgradeData) {
        super(chunkPos, upgradeData);
    }

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
