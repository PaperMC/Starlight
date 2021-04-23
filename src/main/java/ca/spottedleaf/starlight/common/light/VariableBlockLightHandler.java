package ca.spottedleaf.starlight.common.light;

import net.minecraft.util.math.BlockPos;
import java.util.Collection;

/**
 * Recommended implementation is {@link VariableBlockLightHandlerImpl}, but you can implement this interface yourself
 * if you want.
 *
 * @deprecated To be removed in 1.17 due to Mojang adding a custom light block.
 */
@Deprecated
public interface VariableBlockLightHandler {

    /**
     * Returns the custom light level for the specified position. Must return {@code -1} if there is custom level.
     * @param x Block x world coordinate
     * @param y Block y world coordinate
     * @param z Block z world coordinate
     * @return Custom light level for the specified position
     */
    public int getLightLevel(final int x, final int y, final int z);

    /**
     * Returns a collection of all the custom light positions inside the specified chunk. This must be fast,
     * as it is used during chunk lighting.
     * @param chunkX Chunk's x coordinate.
     * @param chunkZ Chunk's z coordinate.
     * @return Collection of all the custom light positions in the specified chunk.
     */
    public Collection<BlockPos> getCustomLightPositions(final int chunkX, final int chunkZ);

}
