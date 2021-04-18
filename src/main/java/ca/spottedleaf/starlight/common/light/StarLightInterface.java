package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.util.WorldUtil;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.IChunkLightProvider;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.IWorldLightListener;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.TicketType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class StarLightInterface {

    public static final TicketType<ChunkPos> CHUNK_WORK_TICKET = TicketType.create("starlight_chunk_work_ticket", (p1, p2) -> Long.compare(p1.asLong(), p2.asLong()));

    /**
     * Can be {@code null}, indicating the light is all empty.
     */
    protected final World world;
    protected final IChunkLightProvider lightAccess;

    protected final ArrayDeque<SkyStarLightEngine> cachedSkyPropagators;
    protected final ArrayDeque<BlockStarLightEngine> cachedBlockPropagators;

    protected final LightQueue lightQueue = new LightQueue(this);

    protected final IWorldLightListener skyReader;
    protected final IWorldLightListener blockReader;
    protected final boolean isClientSide;

    protected final int minSection;
    protected final int maxSection;
    protected final int minLightSection;
    protected final int maxLightSection;

    public StarLightInterface(final IChunkLightProvider lightAccess, final boolean hasSkyLight, final boolean hasBlockLight) {
        this.lightAccess = lightAccess;
        this.world = lightAccess == null ? null : (World)lightAccess.getWorld();
        this.cachedSkyPropagators = hasSkyLight && lightAccess != null ? new ArrayDeque<>() : null;
        this.cachedBlockPropagators = hasBlockLight && lightAccess != null ? new ArrayDeque<>() : null;
        this.isClientSide = !(this.world instanceof ServerWorld);
        if (this.world == null) {
            this.minSection = 0;
            this.maxSection = 15;
            this.minLightSection = -1;
            this.maxLightSection = 16;
        } else {
            this.minSection = WorldUtil.getMinSection(this.world);
            this.maxSection = WorldUtil.getMaxSection(this.world);
            this.minLightSection = WorldUtil.getMinLightSection(this.world);
            this.maxLightSection = WorldUtil.getMaxLightSection(this.world);
        }
        this.skyReader = !hasSkyLight ? IWorldLightListener.Dummy.INSTANCE : new IWorldLightListener() {
            @Override
            public NibbleArray getData(final SectionPos pos) {
                final IChunk chunk = StarLightInterface.this.getAnyChunkNow(pos.getX(), pos.getZ());
                if (chunk == null || (!StarLightInterface.this.isClientSide && !chunk.hasLight()) || !chunk.getStatus().isAtLeast(ChunkStatus.LIGHT)) {
                    return null;
                }

                final int sectionY = pos.getY();

                if (sectionY > StarLightInterface.this.maxLightSection || sectionY < StarLightInterface.this.minLightSection) {
                    return null;
                }

                if (((ExtendedChunk)chunk).getSkyEmptinessMap() == null) {
                    return null;
                }

                return ((ExtendedChunk)chunk).getSkyNibbles()[sectionY - StarLightInterface.this.minLightSection].toVanillaNibble();
            }

            @Override
            public int getLightFor(final BlockPos blockPos) {
                final int x = blockPos.getX();
                int y = blockPos.getY();
                final int z = blockPos.getZ();

                final IChunk chunk = StarLightInterface.this.getAnyChunkNow(x >> 4, z >> 4);
                if (chunk == null || (!StarLightInterface.this.isClientSide && !chunk.hasLight()) || !chunk.getStatus().isAtLeast(ChunkStatus.LIGHT)) {
                    return 15;
                }

                int sectionY = y >> 4;

                if (sectionY > StarLightInterface.this.maxLightSection) {
                    return 15;
                }

                if (sectionY < StarLightInterface.this.minLightSection) {
                    sectionY = StarLightInterface.this.minLightSection;
                    y = sectionY << 4;
                }

                final SWMRNibbleArray[] nibbles = ((ExtendedChunk)chunk).getSkyNibbles();
                final SWMRNibbleArray immediate = nibbles[sectionY - StarLightInterface.this.minLightSection];

                if (StarLightInterface.this.isClientSide) {
                    if (!immediate.isNullNibbleUpdating()) {
                        return immediate.getUpdating(x, y, z);
                    }
                } else {
                    if (!immediate.isNullNibbleVisible()) {
                        return immediate.getVisible(x, y, z);
                    }
                }

                final boolean[] emptinessMap = ((ExtendedChunk)chunk).getSkyEmptinessMap();

                if (emptinessMap == null) {
                    return 15;
                }

                // are we above this chunk's lowest empty section?
                int lowestY = StarLightInterface.this.minLightSection - 1;
                for (int currY = StarLightInterface.this.maxSection; currY >= StarLightInterface.this.minSection; --currY) {
                    if (emptinessMap[currY - StarLightInterface.this.minSection]) {
                        continue;
                    }

                    // should always be full lit here
                    lowestY = currY;
                    break;
                }

                if (sectionY > lowestY) {
                    return 15;
                }

                // this nibble is going to depend solely on the skylight data above it
                // find first non-null data above (there does exist one, as we just found it above)
                for (int currY = sectionY + 1; currY <= StarLightInterface.this.maxLightSection; ++currY) {
                    final SWMRNibbleArray nibble = nibbles[currY - StarLightInterface.this.minLightSection];
                    if (StarLightInterface.this.isClientSide) {
                        if (!nibble.isNullNibbleUpdating()) {
                            return nibble.getUpdating(x, 0, z);
                        }
                    } else {
                        if (!nibble.isNullNibbleVisible()) {
                            return nibble.getVisible(x, 0, z);
                        }
                    }
                }

                // should never reach here
                return 15;
            }

            @Override
            public void updateSectionStatus(final SectionPos pos, final boolean notReady) {
                StarLightInterface.this.sectionChange(pos, notReady);
            }
        };
        this.blockReader = !hasBlockLight ? IWorldLightListener.Dummy.INSTANCE : new IWorldLightListener() {
            @Override
            public NibbleArray getData(final SectionPos pos) {
                final IChunk chunk = StarLightInterface.this.getAnyChunkNow(pos.getX(), pos.getZ());

                if (chunk == null || pos.getY() < StarLightInterface.this.minLightSection || pos.getY() > StarLightInterface.this.maxLightSection) {
                    return null;
                }

                return ((ExtendedChunk)chunk).getBlockNibbles()[pos.getY() - StarLightInterface.this.minLightSection].toVanillaNibble();
            }

            @Override
            public int getLightFor(final BlockPos blockPos) {
                final int cx = blockPos.getX() >> 4;
                final int cy = blockPos.getY() >> 4;
                final int cz = blockPos.getZ() >> 4;

                if (cy < StarLightInterface.this.minLightSection || cy > StarLightInterface.this.maxLightSection) {
                    return 0;
                }

                final IChunk chunk = StarLightInterface.this.getAnyChunkNow(cx, cz);

                if (chunk == null) {
                    return 0;
                }

                final SWMRNibbleArray nibble = ((ExtendedChunk)chunk).getBlockNibbles()[cy - StarLightInterface.this.minLightSection];
                if (StarLightInterface.this.isClientSide) {
                    return nibble.getUpdating(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                } else {
                    return nibble.getVisible(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                }
            }

            @Override
            public void updateSectionStatus(final SectionPos pos, final boolean notReady) {
                return; // block engine doesn't care
            }
        };
    }

    public IWorldLightListener getSkyReader() {
        return this.skyReader;
    }

    public IWorldLightListener getBlockReader() {
        return this.blockReader;
    }

    public boolean isClientSide() {
        return this.isClientSide;
    }

    public IChunk getAnyChunkNow(final int chunkX, final int chunkZ) {
        if (this.world == null) {
            // empty world
            return null;
        }
        return ((ExtendedWorld)this.world).getAnyChunkImmediately(chunkX, chunkZ);
    }

    public boolean hasUpdates() {
        return !this.lightQueue.isEmpty();
    }

    public World getWorld() {
        return this.world;
    }

    public IChunkLightProvider getLightAccess() {
        return this.lightAccess;
    }

    protected final SkyStarLightEngine getSkyLightEngine() {
        if (this.cachedSkyPropagators == null) {
            return null;
        }
        final SkyStarLightEngine ret;
        synchronized (this.cachedSkyPropagators) {
            ret = this.cachedSkyPropagators.pollFirst();
        }

        if (ret == null) {
            return new SkyStarLightEngine(this.world);
        }
        return ret;
    }

    protected final void releaseSkyLightEngine(final SkyStarLightEngine engine) {
        if (this.cachedSkyPropagators == null) {
            return;
        }
        synchronized (this.cachedSkyPropagators) {
            this.cachedSkyPropagators.addFirst(engine);
        }
    }

    protected final BlockStarLightEngine getBlockLightEngine() {
        if (this.cachedBlockPropagators == null) {
            return null;
        }
        final BlockStarLightEngine ret;
        synchronized (this.cachedBlockPropagators) {
            ret = this.cachedBlockPropagators.pollFirst();
        }

        if (ret == null) {
            return new BlockStarLightEngine(this.world);
        }
        return ret;
    }

    protected final void releaseBlockLightEngine(final BlockStarLightEngine engine) {
        if (this.cachedBlockPropagators == null) {
            return;
        }
        synchronized (this.cachedBlockPropagators) {
            this.cachedBlockPropagators.addFirst(engine);
        }
    }

    public CompletableFuture<Void> blockChange(final BlockPos pos) {
        if (this.world == null || pos.getY() < WorldUtil.getMinBlockY(this.world) || pos.getY() > WorldUtil.getMaxBlockY(this.world)) { // empty world
            return null;
        }

        return this.lightQueue.queueBlockChange(pos);
    }

    public CompletableFuture<Void> sectionChange(final SectionPos pos, final boolean newEmptyValue) {
        if (this.world == null) { // empty world
            return null;
        }

        return this.lightQueue.queueSectionChange(pos, newEmptyValue);
    }

    public void forceLoadInChunk(final IChunk chunk, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.forceHandleEmptySectionChanges(this.lightAccess, chunk, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.forceHandleEmptySectionChanges(this.lightAccess, chunk, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void loadInChunk(final int chunkX, final int chunkZ, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.handleEmptySectionChanges(this.lightAccess, chunkX, chunkZ, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.handleEmptySectionChanges(this.lightAccess, chunkX, chunkZ, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void lightChunk(final IChunk chunk, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.light(this.lightAccess, chunk, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.light(this.lightAccess, chunk, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void relightChunks(final Set<ChunkPos> chunks, final Consumer<ChunkPos> chunkLightCallback,
                              final IntConsumer onComplete) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.relightChunks(this.lightAccess, chunks, blockEngine == null ? chunkLightCallback : null,
                        blockEngine == null ? onComplete : null);
            }
            if (blockEngine != null) {
                blockEngine.relightChunks(this.lightAccess, chunks, chunkLightCallback, onComplete);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void checkChunkEdges(final int chunkX, final int chunkZ) {
        this.checkSkyEdges(chunkX, chunkZ);
        this.checkBlockEdges(chunkX, chunkZ);
    }

    public void checkSkyEdges(final int chunkX, final int chunkZ) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
        }
    }

    public void checkBlockEdges(final int chunkX, final int chunkZ) {
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();
        try {
            if (blockEngine != null) {
                blockEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ);
            }
        } finally {
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void checkSkyEdges(final int chunkX, final int chunkZ, final ShortCollection sections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ, sections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
        }
    }

    public void checkBlockEdges(final int chunkX, final int chunkZ, final ShortCollection sections) {
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();
        try {
            if (blockEngine != null) {
                blockEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ, sections);
            }
        } finally {
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void scheduleChunkLight(final ChunkPos pos, final Runnable run) {
        this.lightQueue.queueChunkLighting(pos, run);
    }

    public void removeChunkTasks(final ChunkPos pos) {
        this.lightQueue.removeChunk(pos);
    }

    public void propagateChanges() {
        if (this.lightQueue.isEmpty()) {
            return;
        }

        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            LightQueue.ChunkTasks task;
            while ((task = this.lightQueue.removeFirstTask()) != null) {
                if (task.lightTasks != null) {
                    for (final Runnable run : task.lightTasks) {
                        run.run();
                    }
                }

                final long coordinate = task.chunkCoordinate;
                final int chunkX = CoordinateUtils.getChunkX(coordinate);
                final int chunkZ = CoordinateUtils.getChunkZ(coordinate);

                final Set<BlockPos> positions = task.changedPositions;
                final Boolean[] sectionChanges = task.changedSectionSet;

                if (skyEngine != null && (!positions.isEmpty() || sectionChanges != null)) {
                    skyEngine.blocksChangedInChunk(this.lightAccess, chunkX, chunkZ, positions, sectionChanges);
                }
                if (blockEngine != null && (!positions.isEmpty() || sectionChanges != null)) {
                    blockEngine.blocksChangedInChunk(this.lightAccess, chunkX, chunkZ, positions, sectionChanges);
                }

                if (skyEngine != null && task.queuedEdgeChecksSky != null) {
                    skyEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ, task.queuedEdgeChecksSky);
                }
                if (blockEngine != null && task.queuedEdgeChecksBlock != null) {
                    blockEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ, task.queuedEdgeChecksBlock);
                }

                task.onComplete.complete(null);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    protected static final class LightQueue {

        protected final Long2ObjectLinkedOpenHashMap<ChunkTasks> chunkTasks = new Long2ObjectLinkedOpenHashMap<>();
        protected final StarLightInterface manager;

        public LightQueue(final StarLightInterface manager) {
            this.manager = manager;
        }

        public synchronized boolean isEmpty() {
            return this.chunkTasks.isEmpty();
        }

        public synchronized CompletableFuture<Void> queueBlockChange(final BlockPos pos) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), ChunkTasks::new);
            tasks.changedPositions.add(pos.toImmutable());
            return tasks.onComplete;
        }

        public synchronized CompletableFuture<Void> queueSectionChange(final SectionPos pos, final boolean newEmptyValue) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), ChunkTasks::new);

            if (tasks.changedSectionSet == null) {
                tasks.changedSectionSet = new Boolean[this.manager.maxSection - this.manager.minSection + 1];
            }
            tasks.changedSectionSet[pos.getY() - this.manager.minSection] = Boolean.valueOf(newEmptyValue);

            return tasks.onComplete;
        }

        public synchronized CompletableFuture<Void> queueChunkLighting(final ChunkPos pos, final Runnable lightTask) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), ChunkTasks::new);
            if (tasks.lightTasks == null) {
                tasks.lightTasks = new ArrayList<>();
            }
            tasks.lightTasks.add(lightTask);

            return tasks.onComplete;
        }

        public synchronized CompletableFuture<Void> queueChunkSkylightEdgeCheck(final SectionPos pos, final ShortCollection sections) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), ChunkTasks::new);

            ShortOpenHashSet queuedEdges = tasks.queuedEdgeChecksSky;
            if (queuedEdges == null) {
                queuedEdges = tasks.queuedEdgeChecksSky = new ShortOpenHashSet();
            }
            queuedEdges.addAll(sections);

            return tasks.onComplete;
        }

        public synchronized CompletableFuture<Void> queueChunkBlocklightEdgeCheck(final SectionPos pos, final ShortCollection sections) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), ChunkTasks::new);

            ShortOpenHashSet queuedEdges = tasks.queuedEdgeChecksBlock;
            if (queuedEdges == null) {
                queuedEdges = tasks.queuedEdgeChecksBlock = new ShortOpenHashSet();
            }
            queuedEdges.addAll(sections);

            return tasks.onComplete;
        }

        public void removeChunk(final ChunkPos pos) {
            final ChunkTasks tasks;
            synchronized (this) {
                tasks = this.chunkTasks.remove(CoordinateUtils.getChunkKey(pos));
            }
            if (tasks != null) {
                tasks.onComplete.complete(null);
            }
        }

        public synchronized ChunkTasks removeFirstTask() {
            if (this.chunkTasks.isEmpty()) {
                return null;
            }
            return this.chunkTasks.removeFirst();
        }

        protected static final class ChunkTasks {

            public final Set<BlockPos> changedPositions = new HashSet<>();
            public Boolean[] changedSectionSet;
            public ShortOpenHashSet queuedEdgeChecksSky;
            public ShortOpenHashSet queuedEdgeChecksBlock;
            public List<Runnable> lightTasks;

            public final CompletableFuture<Void> onComplete = new CompletableFuture<>();

            public final long chunkCoordinate;

            public ChunkTasks(final long chunkCoordinate) {
                this.chunkCoordinate = chunkCoordinate;
            }
        }
    }
}
