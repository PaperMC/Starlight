package ca.spottedleaf.starlight.mixin.common.world;

import ca.spottedleaf.starlight.common.light.VariableBlockLightHandler;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import com.mojang.datafixers.util.Either;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.DimensionType;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.ISpawnWorldInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.function.Supplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin extends World implements ISeedReader, ExtendedWorld {

    @Unique
    private VariableBlockLightHandler customBlockLightHandler;

    protected ServerWorldMixin(final ISpawnWorldInfo worldInfo, final RegistryKey<World> dimension, final DimensionType dimensionType,
                               final Supplier<IProfiler> profiler, final boolean isRemote, final boolean isDebug, final long seed) {
        super(worldInfo, dimension, dimensionType, profiler, isRemote, isDebug, seed);
    }

    @Shadow
    public abstract ServerChunkProvider getChunkProvider();

    @Override
    public final VariableBlockLightHandler getCustomLightHandler() {
        return this.customBlockLightHandler;
    }

    @Override
    public final void setCustomLightHandler(final VariableBlockLightHandler handler) {
        this.customBlockLightHandler = handler;
    }

    @Override
    public final Chunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        final ChunkManager storage = this.getChunkProvider().chunkManager;
        final ChunkHolder holder = storage.func_219219_b(CoordinateUtils.getChunkKey(chunkX, chunkZ)); // getChunkHolderOffThread

        if (holder == null) {
            return null;
        }

        final Either<IChunk, ChunkHolder.IChunkLoadingError> either = holder.func_219301_a(ChunkStatus.FULL).getNow(null); // getChunkFuture

        return either == null ? null : (Chunk)either.left().orElse(null);
    }

    @Override
    public final IChunk getAnyChunkImmediately(final int chunkX, final int chunkZ) {
        final ChunkManager storage = this.getChunkProvider().chunkManager;
        final ChunkHolder holder = storage.func_219219_b(CoordinateUtils.getChunkKey(chunkX, chunkZ)); // getChunkHolderOffThread

        return holder == null ? null : holder.func_219287_e(); // getCurrentChunk
    }
}
