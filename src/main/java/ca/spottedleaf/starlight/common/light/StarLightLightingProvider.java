package ca.spottedleaf.starlight.common.light;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;

public interface StarLightLightingProvider {

    StarLightInterface getLightEngine();

    void clientUpdateLight(final LightLayer lightType, final SectionPos pos,
                                  final DataLayer nibble, final boolean trustEdges);

    void clientRemoveLightData(final ChunkPos chunkPos);

    void clientChunkLoad(final ChunkPos pos, final LevelChunk chunk);

}
