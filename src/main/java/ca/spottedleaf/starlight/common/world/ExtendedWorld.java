package ca.spottedleaf.starlight.common.world;

import ca.spottedleaf.starlight.common.light.VariableBlockLightHandler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

public interface ExtendedWorld {

    // rets full chunk without blocking
    public WorldChunk getChunkAtImmediately(final int chunkX, final int chunkZ);

    // rets chunk at any stage, if it exists, immediately
    public Chunk getAnyChunkImmediately(final int chunkX, final int chunkZ);

    public VariableBlockLightHandler getCustomLightHandler();

    public void setCustomLightHandler(final VariableBlockLightHandler handler);

}
