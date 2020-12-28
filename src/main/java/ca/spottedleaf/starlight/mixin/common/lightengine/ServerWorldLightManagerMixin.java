package ca.spottedleaf.starlight.mixin.common.lightengine;

import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.IChunkLightProvider;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.ServerWorldLightManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerWorldLightManager.class)
public abstract class ServerWorldLightManagerMixin extends WorldLightManager implements StarLightLightingProvider {

    @Final
    @Shadow
    private ChunkManager chunkManager;

    @Final
    @Shadow
    private static Logger LOGGER;

    @Shadow
    protected abstract void func_215586_a(final int x, final int z, final ServerWorldLightManager.Phase stage, final Runnable task); // enqueue

    public ServerWorldLightManagerMixin(final IChunkLightProvider chunkProvider, final boolean hasBlockLight, final boolean hasSkyLight) {
        super(chunkProvider, hasBlockLight, hasSkyLight);
    }

    @Unique
    private long workTicketCounts = 0L;

    @Unique
    private void queueTaskForSection(final int chunkX, final int chunkY, final int chunkZ, final Runnable runnable) {
        // TODO this impl is actually fucking awful for checking neighbours and keeping neighbours, for the love of god rewrite it

        final ServerWorld world = (ServerWorld)this.getLightEngine().getWorld();

        if (!world.getChunkProvider().chunkManager.mainThread.isOnExecutionThread()) {
            // this is not safe to run off-main, re-schedule
            world.getChunkProvider().chunkManager.mainThread.execute(() -> {
                this.queueTaskForSection(chunkX, chunkY, chunkZ, runnable);
            });
            return;
        }

        IChunk center = this.getLightEngine().getAnyChunkNow(chunkX, chunkZ);
        if (center == null || !center.getStatus().isAtLeast(ChunkStatus.LIGHT) || !center.hasLight()) {
            // do not accept updates in unlit chunks
            return;
        }

        final Long ticketId = Long.valueOf(this.workTicketCounts++);

        // ensure 1 radius features is loaded (yes they can UNLOAD)
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                world.getChunk(dx + chunkX, dz + chunkZ, (dx | dz) == 0 ? ChunkStatus.LIGHT : ChunkStatus.FEATURES, true);
            }
        }

        world.getChunkProvider().registerTicket(StarLightInterface.CHUNK_WORK_TICKET, new ChunkPos(chunkX, chunkZ), 0, ticketId);

        this.func_215586_a(chunkX, chunkZ, ServerWorldLightManager.Phase.PRE_UPDATE, () -> { // enqueue
            runnable.run();
            this.func_215586_a(chunkX, chunkZ, ServerWorldLightManager.Phase.POST_UPDATE, () -> { // enqueue
                world.getChunkProvider().chunkManager.mainThread.execute(() -> {
                    world.getChunkProvider().releaseTicket(StarLightInterface.CHUNK_WORK_TICKET, new ChunkPos(chunkX, chunkZ), 0, ticketId);
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
    public void updateSectionStatus(final SectionPos pos, final boolean notReady) {
        this.queueTaskForSection(pos.getX(), pos.getY(), pos.getZ(), () -> {
            super.updateSectionStatus(pos, notReady);
        });
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void enableLightSources(final ChunkPos pos, final boolean lightEnabled) {
        // light impl does not need to do this
    }

    /**
     * @reason Light data is now attached to chunks, and this means we need to hook into chunk loading logic
     * to load the data rather than rely on this call. This call also would mess with the vanilla light engine state.
     * @author Spottedleaf
     */
    @Overwrite
    public void setData(final LightType lightType, final SectionPos pos, final NibbleArray nibbles,
                        final boolean trustEdges) {
        // load hooks inside ChunkSerializer
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void retainData(final ChunkPos pos, final boolean retainData) {
        // light impl does not need to do this
    }

    /**
     * @reason Route to new logic to either light or just load the data
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<IChunk> lightChunk(final IChunk chunk, final boolean lit) {
        final ChunkPos chunkPos = chunk.getPos();

        return CompletableFuture.supplyAsync(() -> {
            final Boolean[] emptySections = StarLightEngine.getEmptySectionsForChunk(chunk);
            if (!lit) {
                this.getLightEngine().lightChunk(chunkPos.x, chunkPos.z, emptySections);
                chunk.setLight(true);
            } else {
                this.getLightEngine().loadInChunk(chunkPos.x, chunkPos.z, emptySections);
            }

            this.chunkManager.func_219209_c(chunkPos); // releaseLightTicket
            return chunk;
        }, (runnable) -> {
            this.func_215586_a(chunkPos.x, chunkPos.z, ServerWorldLightManager.Phase.PRE_UPDATE, runnable); // enqueue
        }).whenComplete((final IChunk c, final Throwable throwable) -> {
            if (throwable != null) {
                LOGGER.fatal("Failed to light chunk " + chunkPos, throwable);
            }
        });
    }
}
