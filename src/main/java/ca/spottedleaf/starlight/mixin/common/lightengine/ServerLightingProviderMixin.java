package ca.spottedleaf.starlight.mixin.common.lightengine;

import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.light.LightingProvider;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerLightingProvider.class)
public abstract class ServerLightingProviderMixin extends LightingProvider implements StarLightLightingProvider {

    @Final
    @Shadow
    private ThreadedAnvilChunkStorage chunkStorage;

    @Final
    @Shadow
    private static Logger LOGGER;

    @Shadow
    protected abstract void enqueue(final int x, final int z, final ServerLightingProvider.Stage stage, final Runnable task);

    public ServerLightingProviderMixin(final ChunkProvider chunkProvider, final boolean hasBlockLight, final boolean hasSkyLight) {
        super(chunkProvider, hasBlockLight, hasSkyLight);
    }

    @Unique
    private final Long2IntOpenHashMap chunksBeingWorkedOn = new Long2IntOpenHashMap();

    @Unique
    private void queueTaskForSection(final int chunkX, final int chunkY, final int chunkZ, final Runnable runnable) {
        final ServerWorld world = (ServerWorld)this.getLightEngine().getWorld();

        final Chunk center = this.getLightEngine().getAnyChunkNow(chunkX, chunkZ);
        if (center == null || !center.getStatus().isAtLeast(ChunkStatus.LIGHT) || !center.isLightOn()) {
            // do not accept updates in unlit chunks
            return;
        }

        if (!world.getChunkManager().threadedAnvilChunkStorage.mainThreadExecutor.isOnThread()) {
            // ticket logic is not safe to run off-main, re-schedule
            world.getChunkManager().threadedAnvilChunkStorage.mainThreadExecutor.execute(() -> {
                this.queueTaskForSection(chunkX, chunkY, chunkZ, runnable);
            });
            return;
        }

        final long key = CoordinateUtils.getChunkKey(chunkX, chunkZ);

        final int references = this.chunksBeingWorkedOn.get(key);
        this.chunksBeingWorkedOn.put(key, references + 1);
        if (references == 0) {
            final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            world.getChunkManager().addTicket(StarLightInterface.CHUNK_WORK_TICKET, pos, 0, pos);
        }

        // yes it's rather silly we queue post update as this means two light runTasks() need to be called for runnable
        // to actually take effect, but it's really the only way we can make chunk light calls prioritised vs
        // block update calls. This fixes chunk loading/generating breaking when the server is under high stress
        // from block updates.
        this.enqueue(chunkX, chunkZ, ServerLightingProvider.Stage.POST_UPDATE, () -> {
            runnable.run();
            this.enqueue(chunkX, chunkZ, ServerLightingProvider.Stage.POST_UPDATE, () -> {
                world.getChunkManager().threadedAnvilChunkStorage.mainThreadExecutor.execute(() -> {
                    final int newReferences = this.chunksBeingWorkedOn.get(key);
                    if (newReferences == 1) {
                        this.chunksBeingWorkedOn.remove(key);
                        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                        world.getChunkManager().removeTicket(StarLightInterface.CHUNK_WORK_TICKET, pos, 0, pos);
                    } else {
                        this.chunksBeingWorkedOn.put(key, newReferences - 1);
                    }
                });
            });
        });
    }

    /**
     * @reason Redirect scheduling call away from the vanilla light engine, as well as enforce
     * that chunk neighbours are loaded before the processing can occur
     * @author Spottedleaf
     */
    @Overwrite
    public void checkBlock(final BlockPos pos) {
        final BlockPos posCopy = pos.toImmutable();
        this.queueTaskForSection(posCopy.getX() >> 4, posCopy.getY() >> 4, posCopy.getZ() >> 4, () -> {
            super.checkBlock(posCopy);
        });
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void updateChunkStatus(final ChunkPos pos) {}

    /**
     * @reason Redirect to schedule for our own logic, as well as ensure 1 radius neighbours
     * are loaded
     * Note: Our scheduling logic will discard this call if the chunk is not lit, unloaded, or not at LIGHT stage yet.
     * @author Spottedleaf
     */
    @Overwrite
    public void setSectionStatus(final ChunkSectionPos pos, final boolean notReady) {
        this.queueTaskForSection(pos.getX(), pos.getY(), pos.getZ(), () -> {
            super.setSectionStatus(pos, notReady);
        });
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void setColumnEnabled(final ChunkPos pos, final boolean lightEnabled) {
        // light impl does not need to do this
    }

    /**
     * @reason Light data is now attached to chunks, and this means we need to hook into chunk loading logic
     * to load the data rather than rely on this call. This call also would mess with the vanilla light engine state.
     * @author Spottedleaf
     */
    @Overwrite
    public void enqueueSectionData(final LightType lightType, final ChunkSectionPos pos, final ChunkNibbleArray nibbles,
                                   final boolean bl) {
        // load hooks inside ChunkSerializer
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void setRetainData(final ChunkPos pos, final boolean retainData) {
        // light impl does not need to do this
    }

    /**
     * @reason Route to new logic to either light or just load the data
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<Chunk> light(final Chunk chunk, final boolean lit) {
        final ChunkPos chunkPos = chunk.getPos();

        return CompletableFuture.supplyAsync(() -> {
            final Boolean[] emptySections = StarLightEngine.getEmptySectionsForChunk(chunk);
            if (!lit) {
                chunk.setLightOn(false);
                this.getLightEngine().lightChunk(chunk, emptySections);
                chunk.setLightOn(true);
            } else {
                this.getLightEngine().forceLoadInChunk(chunk, emptySections);
                // can't really force the chunk to be edged checked, as we need neighbouring chunks - but we don't have
                // them, so if it's not loaded then i guess we can't do edge checks. later loads of the chunk should
                // catch what we miss here.
                this.getLightEngine().checkChunkEdges(chunkPos.x, chunkPos.z);
            }

            this.chunkStorage.releaseLightTicket(chunkPos);
            return chunk;
        }, (runnable) -> {
            this.enqueue(chunkPos.x, chunkPos.z, ServerLightingProvider.Stage.PRE_UPDATE, runnable);
        }).whenComplete((final Chunk c, final Throwable throwable) -> {
            if (throwable != null) {
                LOGGER.fatal("Failed to light chunk " + chunkPos, throwable);
            }
        });
    }
}
