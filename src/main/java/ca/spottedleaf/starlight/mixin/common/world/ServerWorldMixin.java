package ca.spottedleaf.starlight.mixin.common.world;

import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.function.Supplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin extends World implements StructureWorldAccess, ExtendedWorld {

    @Final
    @Shadow
    private ServerChunkManager serverChunkManager;

    protected ServerWorldMixin(final MutableWorldProperties properties, final RegistryKey<World> registryRef, final DimensionType dimensionType,
                               final Supplier<Profiler> profiler, final boolean isClient, final boolean debugWorld, final long seed) {
        super(properties, registryRef, dimensionType, profiler, isClient, debugWorld, seed);
    }

    @Override
    public final WorldChunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        final ThreadedAnvilChunkStorage storage = this.serverChunkManager.threadedAnvilChunkStorage;
        final ChunkHolder holder = storage.getChunkHolder(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (holder == null) {
            return null;
        }

        final Either<Chunk, ChunkHolder.Unloaded> either = holder.getFutureFor(ChunkStatus.FULL).getNow(null);

        return either == null ? null : (WorldChunk)either.left().orElse(null);
    }

    @Override
    public final Chunk getAnyChunkImmediately(final int chunkX, final int chunkZ) {
        final ThreadedAnvilChunkStorage storage = this.serverChunkManager.threadedAnvilChunkStorage;
        final ChunkHolder holder = storage.getChunkHolder(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        return holder == null ? null : holder.getCurrentChunk();
    }
}
