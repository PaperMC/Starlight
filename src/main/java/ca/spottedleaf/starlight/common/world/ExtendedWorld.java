package ca.spottedleaf.starlight.common.world;

import ca.spottedleaf.starlight.common.light.VariableBlockLightHandler;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

public interface ExtendedWorld {

    // rets full chunk without blocking
    public LevelChunk getChunkAtImmediately(final int chunkX, final int chunkZ);

    // rets chunk at any stage, if it exists, immediately
    public ChunkAccess getAnyChunkImmediately(final int chunkX, final int chunkZ);

    public VariableBlockLightHandler getCustomLightHandler();

    public void setCustomLightHandler(final VariableBlockLightHandler handler);

}
