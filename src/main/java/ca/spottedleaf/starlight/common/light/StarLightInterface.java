package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import net.minecraft.util.math.BlockPos;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class StarLightInterface {

    public static final TicketType<Long> CHUNK_WORK_TICKET = TicketType.create("starlight_chunk_work_ticket", Long::compareTo);

    /**
     * Can be {@code null}, indicating the light is all empty.
     */
    protected final World world;
    protected final IChunkLightProvider lightAccess;

    protected final ArrayDeque<SkyStarLightEngine> cachedSkyPropagators;
    protected final ArrayDeque<BlockStarLightEngine> cachedBlockPropagators;

    protected final Long2ObjectOpenHashMap<ChunkChanges> changedBlocks = new Long2ObjectOpenHashMap<>();

    protected final IWorldLightListener skyReader;
    protected final IWorldLightListener blockReader;
    protected final boolean isClientSide;

    public StarLightInterface(final IChunkLightProvider lightAccess, final boolean hasSkyLight, final boolean hasBlockLight) {
        this.lightAccess = lightAccess;
        this.world = lightAccess == null ? null : (World)lightAccess.getWorld();
        this.cachedSkyPropagators = hasSkyLight && lightAccess != null ? new ArrayDeque<>() : null;
        this.cachedBlockPropagators = hasBlockLight && lightAccess != null ? new ArrayDeque<>() : null;
        this.isClientSide = !(this.world instanceof ServerWorld);
        this.skyReader = !hasSkyLight ? IWorldLightListener.Dummy.INSTANCE : new IWorldLightListener() {
            @Override
            public NibbleArray getData(final SectionPos pos) {
                final IChunk chunk = StarLightInterface.this.getAnyChunkNow(pos.getX(), pos.getZ());
                if (chunk == null || (!StarLightInterface.this.isClientSide && !chunk.hasLight()) || !chunk.getStatus().isAtLeast(ChunkStatus.LIGHT)) {
                    return null;
                }

                final int sectionY = pos.getY();

                if (sectionY > 16 || sectionY < -1) {
                    return null;
                }

                if (((ExtendedChunk)chunk).getEmptinessMap()[ExtendedChunk.getEmptinessMapIndex(0, 0)] == null) {
                    return null;
                }

                return ((ExtendedChunk)chunk).getSkyNibbles()[sectionY + 1].toVanillaNibble();
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

                if (sectionY > 16) {
                    return 15;
                }

                if (sectionY < -1) {
                    y = -16;
                    sectionY = -1;
                }

                final SWMRNibbleArray[] nibbles = ((ExtendedChunk)chunk).getSkyNibbles();
                final SWMRNibbleArray immediate = nibbles[sectionY + 1];

                if (StarLightInterface.this.isClientSide) {
                    if (!immediate.isNullNibbleUpdating()) {
                        return immediate.getUpdating(x, y, z);
                    }
                } else {
                    if (!immediate.isNullNibbleVisible()) {
                        return immediate.getVisible(x, y, z);
                    }
                }

                final boolean[] emptinessMap = ((ExtendedChunk)chunk).getEmptinessMap()[ExtendedChunk.getEmptinessMapIndex(0, 0)];

                if (emptinessMap == null) {
                    return 15;
                }

                // are we above this chunk's lowest empty section?
                int lowestY = -2;
                for (int currY = 15; currY >= 0; --currY) {
                    if (emptinessMap[currY]) {
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
                for (int currY = sectionY + 1; currY <= 16; ++currY) {
                    final SWMRNibbleArray nibble = nibbles[currY + 1];
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
                return chunk != null ? ((ExtendedChunk)chunk).getBlockNibbles()[pos.getY() + 1].toVanillaNibble() : null;
            }

            @Override
            public int getLightFor(final BlockPos blockPos) {
                final int cx = blockPos.getX() >> 4;
                final int cy = blockPos.getY() >> 4;
                final int cz = blockPos.getZ() >> 4;

                if (cy < -1 || cy > 16) {
                    return 0;
                }

                final IChunk chunk = StarLightInterface.this.getAnyChunkNow(cx, cz);

                if (chunk == null) {
                    return 0;
                }

                final SWMRNibbleArray nibble = ((ExtendedChunk)chunk).getBlockNibbles()[cy + 1];
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
        synchronized (this) {
            return !this.changedBlocks.isEmpty();
        }
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
        if (pos.getY() < 0 || pos.getY() > 255 || this.world == null) { // empty world
            return;
        }

        pos = pos.toImmutable();
        synchronized (this.changedBlocks) {
            this.changedBlocks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), (final long keyInMap) -> {
                return new ChunkChanges();
            }).changedPositions.add(pos);
        }
    }

    public void sectionChange(final SectionPos pos, final boolean newEmptyValue) {
        if (this.world == null) { // empty world
            return;
        }

        synchronized (this.changedBlocks) {
            final ChunkChanges changes = this.changedBlocks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), (final long keyInMap) -> {
                return new ChunkChanges();
            });
            if (changes.changedSectionSet == null) {
                changes.changedSectionSet = new Boolean[16];
            }
            changes.changedSectionSet[pos.getY()] = Boolean.valueOf(newEmptyValue);
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

    public void lightChunk(final int chunkX, final int chunkZ, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.light(this.lightAccess, chunkX, chunkZ, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.light(this.lightAccess, chunkX, chunkZ, emptySections);
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
            final Long2ObjectOpenHashMap<ChunkChanges> changedBlocks;
            synchronized (this.changedBlocks) {
                changedBlocks = this.changedBlocks.clone();
                this.changedBlocks.clear();
            }

            for (final Iterator<Long2ObjectMap.Entry<ChunkChanges>> iterator = changedBlocks.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final Long2ObjectMap.Entry<ChunkChanges> entry = iterator.next();
                final long coordinate = entry.getLongKey();
                final ChunkChanges changes = entry.getValue();
                final Set<BlockPos> positions = changes.changedPositions;
                final Boolean[] sectionChanges = changes.changedSectionSet;

                final int chunkX = CoordinateUtils.getChunkX(coordinate);
                final int chunkZ = CoordinateUtils.getChunkZ(coordinate);

                if (skyEngine != null) {
                    skyEngine.blocksChangedInChunk(this.lightAccess, chunkX, chunkZ, positions, sectionChanges);
                }
                if (blockEngine != null) {
                    blockEngine.blocksChangedInChunk(this.lightAccess, chunkX, chunkZ, positions, sectionChanges);
                }
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    protected static final class ChunkChanges {

        // note: on the main thread, empty section changes are queued before block changes. This means we don't need
        // to worry about cases where a block change is called inside an empty chunk section, according to the "emptiness" map per chunk,
        // for example.
        public final Set<BlockPos> changedPositions = new HashSet<>();

        public Boolean[] changedSectionSet;

    }
}
