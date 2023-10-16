package ca.spottedleaf.starlight.mixin.common.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin implements WorldGenLevel {

    @Shadow
    public abstract ChunkAccess getChunk(int i, int j);

    /**
     * @reason During feature generation, light data is not initialised and will always return 15 in Starlight. Vanilla
     * can possibly return 0 if partially initialised, which allows some mushroom blocks to generate.
     * In general, the brightness value from the light engine should not be used until the chunk is ready. To emulate
     * Vanilla behavior better, we return 0 as the brightness during world gen unless the target chunk is finished
     * lighting.
     * @author Spottedleaf
     */
    @Override
    public int getBrightness(final LightLayer lightLayer, final BlockPos blockPos) {
        final ChunkAccess chunk = this.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        return this.getLightEngine().getLayerListener(lightLayer).getLightValue(blockPos);
    }

    /**
     * @reason See above
     * @author Spottedleaf
     */
    @Override
    public int getRawBrightness(final BlockPos blockPos, final int subtract) {
        final ChunkAccess chunk = this.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        return this.getLightEngine().getRawBrightness(blockPos, subtract);
    }
}
