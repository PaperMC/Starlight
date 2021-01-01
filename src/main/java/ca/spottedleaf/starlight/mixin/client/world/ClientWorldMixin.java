package ca.spottedleaf.starlight.mixin.client.world;

import ca.spottedleaf.starlight.common.light.VariableBlockLightHandler;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.function.Supplier;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin extends World implements ExtendedWorld {

    @Unique
    private VariableBlockLightHandler customBlockLightHandler;

    protected ClientWorldMixin(final MutableWorldProperties properties, final RegistryKey<World> registryRef, final DimensionType dimensionType,
                               final Supplier<Profiler> profiler, final boolean isClient, final boolean debugWorld, final long seed) {
        super(properties, registryRef, dimensionType, profiler, isClient, debugWorld, seed);
    }

    @Override
    public final VariableBlockLightHandler getCustomLightHandler() {
        return this.customBlockLightHandler;
    }

    @Override
    public final void setCustomLightHandler(final VariableBlockLightHandler handler) {
        this.customBlockLightHandler = handler;
    }

    @Override
    public final WorldChunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        return this.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
    }

    @Override
    public final Chunk getAnyChunkImmediately(int chunkX, int chunkZ) {
        return this.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
    }
}
