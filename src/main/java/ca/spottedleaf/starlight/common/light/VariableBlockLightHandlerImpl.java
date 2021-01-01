package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;

public class VariableBlockLightHandlerImpl implements VariableBlockLightHandler {

    protected final Long2ObjectOpenHashMap<Set<BlockPos>> positionsByChunk = new Long2ObjectOpenHashMap<>();
    protected final Long2IntOpenHashMap lightValuesByPosition = new Long2IntOpenHashMap();
    protected final StampedLock seqlock = new StampedLock();
    {
        this.lightValuesByPosition.defaultReturnValue(-1);
        this.positionsByChunk.defaultReturnValue(Collections.emptySet());
    }

    @Override
    public int getLightLevel(final int x, final int y, final int z) {
        final long key = CoordinateUtils.getBlockKey(x, y, z);
        try {
            final long attempt = this.seqlock.tryOptimisticRead();
            if (attempt != 0L) {
                final int ret = this.lightValuesByPosition.get(key);

                if (this.seqlock.validate(attempt)) {
                    return ret;
                }
            }
        } catch (final Error error) {
            throw error;
        } catch (final Throwable thr) {
            // ignore
        }

        this.seqlock.readLock();
        try {
            return this.lightValuesByPosition.get(key);
        } finally {
            this.seqlock.tryUnlockRead();
        }
    }

    @Override
    public Collection<BlockPos> getCustomLightPositions(final int chunkX, final int chunkZ) {
        final long key = CoordinateUtils.getChunkKey(chunkX, chunkZ);
        try {
            final long attempt = this.seqlock.tryOptimisticRead();
            if (attempt != 0L) {
                final Set<BlockPos> ret = new HashSet<>(this.positionsByChunk.get(key));

                if (this.seqlock.validate(attempt)) {
                    return ret;
                }
            }
        } catch (final Error error) {
            throw error;
        } catch (final Throwable thr) {
            // ignore
        }

        this.seqlock.readLock();
        try {
            return new HashSet<>(this.positionsByChunk.get(key));
        } finally {
            this.seqlock.tryUnlockRead();
        }
    }

    public void setSource(final int x, final int y, final int z, final int to) {
        if (to < 0 || to > 15) {
            throw new IllegalArgumentException();
        }
        this.seqlock.writeLock();
        try {
            if (this.lightValuesByPosition.put(CoordinateUtils.getBlockKey(x, y, z), to) == -1) {
                this.positionsByChunk.computeIfAbsent(CoordinateUtils.getChunkKey(x >> 4, z >> 4), (final long keyInMap) -> {
                    return new HashSet<>();
                }).add(new BlockPos(x, y, z));
            }
        } finally {
            this.seqlock.tryUnlockWrite();
        }
    }

    public int removeSource(final int x, final int y, final int z) {
        this.seqlock.writeLock();
        try {
            final int ret = this.lightValuesByPosition.remove(CoordinateUtils.getBlockKey(x, y, z));

            if (ret != -1) {
                final long chunkKey = CoordinateUtils.getChunkKey(x >> 4, z >> 4);

                final Set<BlockPos> positions = this.positionsByChunk.get(chunkKey);
                positions.remove(new BlockPos(x, y, z));

                if (positions.isEmpty()) {
                    this.positionsByChunk.remove(chunkKey);
                }
            }

            return ret;
        } finally {
            this.seqlock.tryUnlockWrite();
        }
    }
}
