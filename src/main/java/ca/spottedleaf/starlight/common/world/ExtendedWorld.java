package ca.spottedleaf.starlight.common.world;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

public interface ExtendedWorld {

    // rets full chunk without blocking
    default LevelChunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        return null;
    }

    // rets chunk at any stage, if it exists, immediately
    default ChunkAccess getAnyChunkImmediately(final int chunkX, final int chunkZ) {
        return null;
    }

}
