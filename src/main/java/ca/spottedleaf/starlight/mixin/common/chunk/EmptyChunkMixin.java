package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.util.WorldUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import java.util.function.Consumer;

@Mixin(EmptyChunk.class)
public abstract class EmptyChunkMixin extends WorldChunk implements Chunk, ExtendedChunk {

    public EmptyChunkMixin(final ServerWorld serverWorld, final ProtoChunk protoChunk, final Consumer<WorldChunk> consumer) {
        super(serverWorld, protoChunk, consumer);
    }

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return StarLightEngine.getFilledEmptyLight(this.getWorld());
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {}

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return StarLightEngine.getFilledEmptyLight(this.getWorld());
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {}

    @Override
    public boolean[] getEmptinessMap() {
        return null;
    }

    @Override
    public void setEmptinessMap(final boolean[] emptinessMap) {}
}
