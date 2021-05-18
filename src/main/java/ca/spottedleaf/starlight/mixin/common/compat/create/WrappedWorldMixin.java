package ca.spottedleaf.starlight.mixin.common.compat.create;

import ca.spottedleaf.starlight.common.light.VariableBlockLightHandler;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import com.simibubi.create.foundation.utility.worldWrappers.WrappedWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * @author AeiouEnigma
 */
@Mixin(value = WrappedWorld.class, remap = false)
public class WrappedWorldMixin implements ExtendedWorld {

    @Unique
    private VariableBlockLightHandler customBlockLightHandler;

    @Override
    public VariableBlockLightHandler getCustomLightHandler() {
        return this.customBlockLightHandler;
    }

    @Override
    public void setCustomLightHandler(final VariableBlockLightHandler handler) {
        this.customBlockLightHandler = handler;
    }

    @Override
    public final Chunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        return null;
    }

    @Override
    public final IChunk getAnyChunkImmediately(final int chunkX, final int chunkZ) {
        return null;
    }
}
