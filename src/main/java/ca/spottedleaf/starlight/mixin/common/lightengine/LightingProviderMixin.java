package ca.spottedleaf.starlight.mixin.common.lightengine;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.chunk.NibbledChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.ChunkLightingView;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.chunk.light.LightingView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightingProvider.class)
public abstract class LightingProviderMixin implements LightingView, StarLightLightingProvider {

    @Unique
    protected StarLightInterface lightEngine;

    @Override
    public StarLightInterface getLightEngine() {
        return this.lightEngine;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    public void construct(ChunkProvider chunkProvider, boolean hasBlockLight, boolean hasSkyLight, CallbackInfo ci) {
        this.lightEngine = new StarLightInterface(chunkProvider, hasSkyLight, hasBlockLight);
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void checkBlock(BlockPos pos) {
        this.lightEngine.blockChange(pos.toImmutable());
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void addLightSource(BlockPos pos, int level) {
        // this light engine only reads levels from blocks, so this is a no-op
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public boolean hasUpdates() {
        // route to new light engine
        return this.lightEngine.hasUpdates();
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public int doLightUpdates(int maxUpdateCount, boolean doSkylight, boolean skipEdgeLightPropagation) {
        // replace impl
        boolean hadUpdates = this.hasUpdates();
        this.lightEngine.propagateChanges();
        return hadUpdates ? 1 : 0;
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void setSectionStatus(ChunkSectionPos pos, boolean notReady) {
        // no longer required with new light impl
    }

    protected final Long2ObjectOpenHashMap<SWMRNibbleArray[]> blockLight = new Long2ObjectOpenHashMap<>();
    protected final Long2ObjectOpenHashMap<SWMRNibbleArray[]> skyLight = new Long2ObjectOpenHashMap<>();

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void setColumnEnabled(ChunkPos pos, boolean lightEnabled) {
        Chunk chunk = this.getLightEngine().getAnyChunkNow(pos.x, pos.z);
        if (chunk != null) {
            SWMRNibbleArray[] blockNibbles = this.blockLight.get(CoordinateUtils.getChunkKey(pos));
            SWMRNibbleArray[] skyNibbles = this.skyLight.get(CoordinateUtils.getChunkKey(pos));
            if (blockNibbles != null) {
                ((NibbledChunk)chunk).setBlockNibbles(blockNibbles);
            }
            if (skyNibbles != null) {
                ((NibbledChunk)chunk).setSkyNibbles(skyNibbles);
            }
        } else if (!lightEnabled) {
            this.blockLight.remove(CoordinateUtils.getChunkKey(pos));
            this.skyLight.remove(CoordinateUtils.getChunkKey(pos));
        }
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkLightingView get(LightType lightType) {
        return lightType == LightType.BLOCK ? this.lightEngine.getBlockReader() : this.lightEngine.getSkyReader();
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void enqueueSectionData(LightType lightType, ChunkSectionPos pos, @Nullable ChunkNibbleArray nibble, boolean bl) {
        // data storage changed with new light impl
        Chunk chunk = this.getLightEngine().getAnyChunkNow(pos.getX(), pos.getZ());
        switch (lightType) {
            case BLOCK: {
                SWMRNibbleArray[] blockNibbles = this.blockLight.computeIfAbsent(CoordinateUtils.getChunkKey(pos), (long keyInMap) -> {
                    return StarLightEngine.getFilledEmptyLight(false);
                });
                SWMRNibbleArray replacement;
                if (nibble == null || nibble.isUninitialized()) {
                    replacement = new SWMRNibbleArray();
                } else {
                    replacement = new SWMRNibbleArray(nibble.asByteArray());
                }

                replacement.updateVisible();

                blockNibbles[pos.getY() + 1] = replacement;

                if (chunk != null) {
                    ((NibbledChunk)chunk).setBlockNibbles(blockNibbles);
                    this.lightEngine.getLightAccess().onLightUpdate(LightType.BLOCK, pos);
                }
                break;
            }
            case SKY: {
                SWMRNibbleArray[] skyNibbles = this.skyLight.computeIfAbsent(CoordinateUtils.getChunkKey(pos), (long keyInMap) -> {
                    return StarLightEngine.getFilledEmptyLight(true);
                });
                SWMRNibbleArray replacement;
                if (nibble == null) {
                    replacement = new SWMRNibbleArray(true, 15);
                } else if (nibble.isUninitialized()) {
                    replacement = new SWMRNibbleArray();
                } else {
                    replacement = new SWMRNibbleArray(nibble.asByteArray());
                }

                replacement.updateVisible();

                skyNibbles[pos.getY() + 1] = replacement;

                if (chunk != null) {
                    ((NibbledChunk)chunk).setSkyNibbles(skyNibbles);
                    this.lightEngine.getLightAccess().onLightUpdate(LightType.SKY, pos);
                }
                break;
            }
        }
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public void setRetainData(ChunkPos pos, boolean retainData) {
        // not used by new light impl
    }

    /**
     * @author Spottedleaf
     */
    @Overwrite
    public int getLight(BlockPos pos, int ambientDarkness) {
        // need to use new light hooks for this
        int sky = this.lightEngine.getSkyReader().getLightLevel(pos) - ambientDarkness;
        int block = this.lightEngine.getBlockReader().getLightLevel(pos);
        return Math.max(sky, block);
    }
}
