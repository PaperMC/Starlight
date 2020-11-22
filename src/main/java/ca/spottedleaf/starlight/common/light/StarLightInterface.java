package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.chunk.ThreadedAnvilChunkStorageMethods;
import ca.spottedleaf.starlight.common.chunk.NibbledChunk;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.light.ChunkLightingView;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class StarLightInterface {

    public static final ChunkTicketType<Long> CHUNK_WORK_TICKET = ChunkTicketType.create("starlight_chunk_work_ticket", Long::compareTo);

    protected final World world;
    protected final ChunkProvider lightAccess;

    protected final ArrayDeque<SkyStarLightEngine> cachedSkyPropagators;
    protected final ArrayDeque<BlockStarLightEngine> cachedBlockPropagators;

    protected final Long2ObjectOpenHashMap<Set<BlockPos>> changedBlocks = new Long2ObjectOpenHashMap<>();

    protected final ChunkLightingView skyReader;
    protected final ChunkLightingView blockReader;
    protected final boolean isClientSide;

    public StarLightInterface(final ChunkProvider lightAccess, final boolean hasSkyLight, final boolean hasBlockLight) {
        this.lightAccess = lightAccess;
        this.world = (World)lightAccess.getWorld();
        this.cachedSkyPropagators = hasSkyLight ? new ArrayDeque<>() : null;
        this.cachedBlockPropagators = hasBlockLight ? new ArrayDeque<>() : null;
        this.isClientSide = !(world instanceof ServerWorld);
        this.skyReader = !hasSkyLight ? ChunkLightingView.Empty.INSTANCE : new ChunkLightingView() {
            @Override
            public @Nullable ChunkNibbleArray getLightSection(ChunkSectionPos pos) {
                Chunk chunk = StarLightInterface.this.getAnyChunkNow(pos.getX(), pos.getZ());
                return chunk != null ? ((NibbledChunk)chunk).getSkyNibbles()[pos.getY() + 1].asNibble() : null;
            }

            @Override
            public int getLightLevel(BlockPos blockPos) {
                int cx = blockPos.getX() >> 4;
                int cy = blockPos.getY() >> 4;
                int cz = blockPos.getZ() >> 4;
                Chunk chunk = StarLightInterface.this.getAnyChunkNow(cx, cz);
                if (chunk == null) {
                    return 15;
                }
                if (cy < -1) {
                    cy = -1;
                } else if (cy > 16) {
                    cy = 16;
                }
                SWMRNibbleArray nibble = ((NibbledChunk)chunk).getSkyNibbles()[cy + 1];
                return nibble.getVisible(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            }

            @Override
            public void setSectionStatus(ChunkSectionPos pos, boolean notReady) {
                return; // don't care.
            }
        };
        this.blockReader = !hasBlockLight ? ChunkLightingView.Empty.INSTANCE : new ChunkLightingView() {
            @Override
            public @Nullable ChunkNibbleArray getLightSection(ChunkSectionPos pos) {
                Chunk chunk = StarLightInterface.this.getAnyChunkNow(pos.getX(), pos.getZ());
                return chunk != null ? ((NibbledChunk)chunk).getBlockNibbles()[pos.getY() + 1].asNibble() : null;
            }

            @Override
            public int getLightLevel(BlockPos blockPos) {
                int cx = blockPos.getX() >> 4;
                int cy = blockPos.getY() >> 4;
                int cz = blockPos.getZ() >> 4;
                Chunk chunk = StarLightInterface.this.getAnyChunkNow(cx, cz);
                if (chunk == null) {
                    return 0;
                }
                if (cy < -1 || cy > 16) {
                    return 0;
                }
                SWMRNibbleArray nibble = ((NibbledChunk)chunk).getBlockNibbles()[cy + 1];
                return nibble.getVisible(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            }

            @Override
            public void setSectionStatus(ChunkSectionPos pos, boolean notReady) {
                return; // don't care.
            }
        };
    }

    public ChunkLightingView getSkyReader() {
        return this.skyReader;
    }

    public ChunkLightingView getBlockReader() {
        return this.blockReader;
    }

    public boolean isClientSide() {
        return this.isClientSide;
    }

    public Chunk getAnyChunkNow(int chunkX, int chunkZ) {
        if (this.isClientSide) {
            return this.world.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
        } else {
            ServerWorld world = (ServerWorld)this.world;
            ChunkHolder chunkHolder = ((ThreadedAnvilChunkStorageMethods)world.getChunkManager().threadedAnvilChunkStorage)
                    .getOffMainChunkHolder(CoordinateUtils.getChunkKey(chunkX, chunkZ));

            return chunkHolder == null ? null : chunkHolder.getCurrentChunk();
        }
    }

    public boolean hasUpdates() {
        synchronized (this) {
            return !this.changedBlocks.isEmpty();
        }
    }

    public World getWorld() {
        return this.world;
    }

    public ChunkProvider getLightAccess() {
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
            return new SkyStarLightEngine(this.isClientSide);
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
            return new BlockStarLightEngine(this.isClientSide);
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

    public void blockChange(BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() > 255) {
            return;
        }

        pos = pos.toImmutable();
        synchronized (this.changedBlocks) {
            this.changedBlocks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), (final long keyInMap) -> {
                return new HashSet<>();
            }).add(pos);
        }
    }

    public void lightChunk(final int chunkX, final int chunkZ) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.light(this.lightAccess, chunkX, chunkZ);
            }
            if (blockEngine != null) {
                blockEngine.light(this.lightAccess, chunkX, chunkZ);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void relightChunk(final int chunkX, final int chunkZ) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.relight(this.lightAccess, chunkX, chunkZ);
            }
            if (blockEngine != null) {
                blockEngine.relight(this.lightAccess, chunkX, chunkZ);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void checkChunkEdges(final int chunkX, final int chunkZ) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ);
            }
            if (blockEngine != null) {
                blockEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void propagateChanges() {
        synchronized (this.changedBlocks) {
            if (this.changedBlocks.isEmpty()) {
                return;
            }
        }
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            // TODO be smarter about this in the future
            final Long2ObjectOpenHashMap<Set<BlockPos>> changedBlocks;
            synchronized (this.changedBlocks) {
                changedBlocks = this.changedBlocks.clone();
                this.changedBlocks.clear();
            }

            for (final Iterator<Long2ObjectMap.Entry<Set<BlockPos>>> iterator = changedBlocks.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final Long2ObjectMap.Entry<Set<BlockPos>> entry = iterator.next();
                final long coordinate = entry.getLongKey();
                final Set<BlockPos> positions = entry.getValue();

                if (skyEngine != null) {
                    skyEngine.blocksChangedInChunk(this.lightAccess, CoordinateUtils.getChunkX(coordinate), CoordinateUtils.getChunkZ(coordinate), positions);
                }
                if (blockEngine != null) {
                    blockEngine.blocksChangedInChunk(this.lightAccess, CoordinateUtils.getChunkX(coordinate), CoordinateUtils.getChunkZ(coordinate), positions);
                }
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }
}
