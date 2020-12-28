package ca.spottedleaf.starlight.mixin.client.world;

import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.storage.ISpawnWorldInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.function.Supplier;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin extends World implements ExtendedWorld {

    @Shadow
    public abstract ClientChunkProvider getChunkProvider();

    protected ClientWorldMixin(final ISpawnWorldInfo worldInfo, final RegistryKey<World> dimension, final DimensionType dimensionType,
                               final Supplier<IProfiler> profiler, final boolean isRemote, final boolean isDebug, final long seed) {
        super(worldInfo, dimension, dimensionType, profiler, isRemote, isDebug, seed);
    }

    @Override
    public final Chunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        return this.getChunkProvider().getChunk(chunkX, chunkZ, false);
    }

    @Override
    public final IChunk getAnyChunkImmediately(int chunkX, int chunkZ) {
        return this.getChunkProvider().getChunk(chunkX, chunkZ, false);
    }
}
