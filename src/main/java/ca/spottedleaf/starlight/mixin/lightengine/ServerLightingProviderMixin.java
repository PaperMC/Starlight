package ca.spottedleaf.starlight.mixin.lightengine;

import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import net.minecraft.server.world.ChunkTaskPrioritySystem;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.thread.MessageListener;
import net.minecraft.util.thread.TaskExecutor;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

@Mixin(ServerLightingProvider.class)
public abstract class ServerLightingProviderMixin extends LightingProvider implements StarLightLightingProvider {

    @Final
    @Shadow
    private ThreadedAnvilChunkStorage chunkStorage;

    @Final
    @Shadow
    private MessageListener<ChunkTaskPrioritySystem.Task<Runnable>> executor;

    @Final
    @Shadow
    private AtomicBoolean ticking;

    @Final
    @Shadow
    private TaskExecutor<Runnable> processor;

    @Final
    @Shadow
    private static Logger LOGGER;

    @Unique
    protected final ConcurrentLinkedQueue<Runnable> preTasks = new ConcurrentLinkedQueue<>();

    @Unique
    protected final ConcurrentLinkedQueue<Runnable> postTasks = new ConcurrentLinkedQueue<>();

    @Unique
    private void enqueue(final int x, final int z, final Runnable task) {
        this.enqueue(x, z, task, false);
    }

    @Unique
    private void enqueue(final int x, final int z, final Runnable task, final boolean postTask) {
        this.enqueue(x, z, this.chunkStorage.getCompletedLevelSupplier(ChunkPos.toLong(x, z)), postTask, task);
    }

    @Unique
    private void enqueue(final int x, final int z, final IntSupplier completedLevelSupplier, final boolean postTask,
                         final Runnable task) {
        this.executor.send(ChunkTaskPrioritySystem.createMessage(() -> {
            if (postTask) {
                this.postTasks.add(task);
            } else {
                this.preTasks.add(task);
            }
        }, ChunkPos.toLong(x, z), completedLevelSupplier));
    }

    public ServerLightingProviderMixin(final ChunkProvider chunkProvider, final boolean hasBlockLight, final boolean hasSkyLight) {
        super(chunkProvider, hasBlockLight, hasSkyLight);
    }

    private long workTicketCounts = 0L;

    @Unique
    private void queueTaskForSection(final int chunkX, final int chunkY, final int chunkZ, final Runnable runnable) {
        // TODO this impl is actually fucking awful for checking neighbours and keeping neighbours, for the love of god rewrite it

        final ServerWorld world = (ServerWorld)this.getLightEngine().getWorld();

        Chunk center = this.getLightEngine().getAnyChunkNow(chunkX, chunkZ);
        if (center == null || !center.getStatus().isAtLeast(ChunkStatus.LIGHT) || !center.isLightOn()) {
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

        world.getChunkManager().addTicket(StarLightInterface.CHUNK_WORK_TICKET, new ChunkPos(chunkX, chunkZ), 0, ticketId);

        this.enqueue(chunkX, chunkZ, () -> {
            runnable.run();
            this.enqueue(chunkX, chunkZ, () -> {
                world.getChunkManager().threadedAnvilChunkStorage.mainThreadExecutor.execute(() -> {
                    world.getChunkManager().removeTicket(StarLightInterface.CHUNK_WORK_TICKET, new ChunkPos(chunkX, chunkZ), 0, ticketId);
                });
            }, true);
        });
    }

    /**
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
     * @author Spottedleaf
     */
    @Overwrite
    public void updateChunkStatus(final ChunkPos pos) {
        // Do nothing, we don't care
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void setSectionStatus(final ChunkSectionPos pos, final boolean notReady) {
        this.queueTaskForSection(pos.getX(), pos.getY(), pos.getZ(), () -> {
            super.setSectionStatus(pos, notReady);
        });
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void setColumnEnabled(final ChunkPos pos, final boolean lightEnabled) {
        // light impl does not need to do this
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void enqueueSectionData(final LightType lightType, final ChunkSectionPos pos, final ChunkNibbleArray nibbles,
                                   final boolean bl) {
        // hook for loading light data is changed, as chunk is no longer loaded at this stage
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void setRetainData(final ChunkPos pos, final boolean retainData) {
        // light impl does not need to do this
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void tick() {
        if ((!this.preTasks.isEmpty() || !this.postTasks.isEmpty() || super.hasUpdates()) && this.ticking.compareAndSet(false, true)) {
            this.processor.send(() -> {
                this.runTasks();
                this.ticking.set(false);
                this.tick();
            });
        }
    }
    /**
     * @author Spottedleaf
     */
    @Overwrite
    private void runTasks() {
        Runnable task;
        while ((task = this.preTasks.poll()) != null) {
            task.run();
        }
        this.getLightEngine().propagateChanges();
        while ((task = this.postTasks.poll()) != null) {
            task.run();
        }
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<Chunk> light(final Chunk chunk, final boolean lit) {
        final ChunkPos chunkPos = chunk.getPos();

        return CompletableFuture.supplyAsync(() -> {
            final Boolean[] emptySections = StarLightEngine.getEmptySectionsForChunk(chunk);
            if (!lit) {
                this.getLightEngine().lightChunk(chunkPos.x, chunkPos.z, emptySections);
                chunk.setLightOn(true);
            } else {
                this.getLightEngine().loadInChunk(chunkPos.x, chunkPos.z, emptySections);
            }

            this.chunkStorage.releaseLightTicket(chunkPos);
            return chunk;
        }, (runnable) -> {
            this.enqueue(chunkPos.x, chunkPos.z, runnable);
        }).whenComplete((final Chunk c, final Throwable throwable) -> {
            if (throwable != null) {
                LOGGER.fatal("Failed to light chunk " + chunkPos, throwable);
            }
        });
    }
}
