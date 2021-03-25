package ca.spottedleaf.starlight.mixin.common.world;

import ca.spottedleaf.starlight.common.light.VariableBlockLightHandler;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import com.mojang.datafixers.util.Either;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin extends Level implements WorldGenLevel, ExtendedWorld {

    @Shadow
    @Final
    private ServerChunkCache chunkSource;

    @Unique
    private VariableBlockLightHandler customBlockLightHandler;

    protected ServerWorldMixin(final WritableLevelData writableLevelData, final ResourceKey<Level> resourceKey,
                               final DimensionType dimensionType, final Supplier<ProfilerFiller> supplier, final boolean bl,
                               final boolean bl2, final long l) {
        super(writableLevelData, resourceKey, dimensionType, supplier, bl, bl2, l);
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
    public final LevelChunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        final ChunkMap storage = this.chunkSource.chunkMap;
        final ChunkHolder holder = storage.getUpdatingChunkIfPresent(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (holder == null) {
            return null;
        }

        final Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> either = holder.getFutureIfPresentUnchecked(ChunkStatus.FULL).getNow(null);

        return either == null ? null : (LevelChunk)either.left().orElse(null);
    }

    @Override
    public final ChunkAccess getAnyChunkImmediately(final int chunkX, final int chunkZ) {
        final ChunkMap storage = this.chunkSource.chunkMap;
        final ChunkHolder holder = storage.getUpdatingChunkIfPresent(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        return holder == null ? null : holder.getLastAvailable();
    }
}
