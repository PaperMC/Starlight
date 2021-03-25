package ca.spottedleaf.starlight.mixin.common.lightengine;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.util.WorldUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEventListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Iterator;

@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin implements LightEventListener, StarLightLightingProvider {

    @Unique
    protected StarLightInterface lightEngine;

    @Override
    public final StarLightInterface getLightEngine() {
        return this.lightEngine;
    }

    /**
     *
     * TODO since this is a constructor inject, check on update for new constructors
     */
    @Inject(
            method = "<init>", at = @At("TAIL")
    )
    public void construct(final LightChunkGetter chunkProvider, final boolean hasBlockLight, final boolean hasSkyLight,
                          final CallbackInfo ci) {
        this.lightEngine = new StarLightInterface(chunkProvider, hasSkyLight, hasBlockLight);
    }

    /**
     * @reason Route to new light engine
     * @author Spottedleaf
     */
    @Overwrite
    public void checkBlock(final BlockPos pos) {
        this.lightEngine.blockChange(pos.immutable());
    }

    /**
     * @reason Avoid messing with vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void onBlockEmissionIncrease(final BlockPos pos, final int level) {
        // this light engine only reads levels from blocks, so this is a no-op
    }

    /**
     * @reason Route to new light engine
     * @author Spottedleaf
     */
    @Overwrite
    public boolean hasLightWork() {
        // route to new light engine
        return this.lightEngine.hasUpdates();
    }

    /**
     * @reason Hook into new light engine for light updates
     * @author Spottedleaf
     */
    @Overwrite
    public int runUpdates(final int maxUpdateCount, final boolean doSkylight, final boolean skipEdgeLightPropagation) {
        // replace impl
        if ((Object)this instanceof ThreadedLevelLightEngine) {
            // serverside
            final boolean hadUpdates = this.hasLightWork();
            this.lightEngine.propagateChanges();
            return hadUpdates ? 1 : 0;
        } else {
            // clientside
            final boolean hadUpdates = this.hasLightWork() || !this.queuedChunkLoads.isEmpty();
            for (final Iterator<Long2ObjectMap.Entry<Boolean[]>> iterator = this.queuedChunkLoads.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final Long2ObjectMap.Entry<Boolean[]> entry = iterator.next();
                final long coordinate = entry.getLongKey();
                final Boolean[] emptiness = entry.getValue();
                iterator.remove();

                this.lightEngine.loadInChunk(CoordinateUtils.getChunkX(coordinate), CoordinateUtils.getChunkZ(coordinate), emptiness);
            }
            for (final Iterator<Long2ObjectMap.Entry<ShortOpenHashSet>> iterator = this.queuedEdgeChecksSky.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final Long2ObjectMap.Entry<ShortOpenHashSet> entry = iterator.next();
                final long coordinate = entry.getLongKey();
                final ShortOpenHashSet sections = entry.getValue();
                iterator.remove();

                this.lightEngine.checkSkyEdges(CoordinateUtils.getChunkX(coordinate), CoordinateUtils.getChunkZ(coordinate), sections);
            }
            for (final Iterator<Long2ObjectMap.Entry<ShortOpenHashSet>> iterator = this.queuedEdgeChecksBlock.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final Long2ObjectMap.Entry<ShortOpenHashSet> entry = iterator.next();
                final long coordinate = entry.getLongKey();
                final ShortOpenHashSet sections = entry.getValue();
                iterator.remove();

                this.lightEngine.checkBlockEdges(CoordinateUtils.getChunkX(coordinate), CoordinateUtils.getChunkZ(coordinate), sections);
            }

            this.lightEngine.propagateChanges();
            return hadUpdates ? 1 : 0;
        }
    }

    /**
     * @reason New light engine hook for handling empty section changes
     * @author Spottedleaf
     */
    @Overwrite
    public void updateSectionStatus(final SectionPos pos, final boolean notReady) {
        this.lightEngine.sectionChange(pos, notReady);
    }

    @Unique
    protected final Long2ObjectOpenHashMap<SWMRNibbleArray[]> blockLightMap = new Long2ObjectOpenHashMap<>();

    @Unique
    protected final Long2ObjectOpenHashMap<SWMRNibbleArray[]> skyLightMap = new Long2ObjectOpenHashMap<>();

    @Unique
    protected final Long2ObjectOpenHashMap<Boolean[]> queuedChunkLoads = new Long2ObjectOpenHashMap<>();

    @Unique
    protected final Long2ObjectOpenHashMap<ShortOpenHashSet> queuedEdgeChecksBlock = new Long2ObjectOpenHashMap<>();

    @Unique
    protected final Long2ObjectOpenHashMap<ShortOpenHashSet> queuedEdgeChecksSky = new Long2ObjectOpenHashMap<>();

    /**
     * @reason Replace hook clientside for enabling light data, we use it for loading in light data previously queued
     * @author Spottedleaf
     */
    @Overwrite
    public void enableLightSources(final ChunkPos pos, final boolean lightEnabled) {
        final ChunkAccess chunk = this.getLightEngine().getAnyChunkNow(pos.x, pos.z);
        if (chunk != null) {
            final SWMRNibbleArray[] blockNibbles = this.blockLightMap.get(CoordinateUtils.getChunkKey(pos));
            final SWMRNibbleArray[] skyNibbles = this.skyLightMap.get(CoordinateUtils.getChunkKey(pos));
            if (blockNibbles != null) {
                ((ExtendedChunk)chunk).setBlockNibbles(blockNibbles);
            }
            if (skyNibbles != null) {
                ((ExtendedChunk)chunk).setSkyNibbles(skyNibbles);
            }

            this.queuedChunkLoads.put(CoordinateUtils.getChunkKey(pos), StarLightEngine.getEmptySectionsForChunk(chunk));
        } else if (!lightEnabled) {
            this.blockLightMap.remove(CoordinateUtils.getChunkKey(pos));
            this.skyLightMap.remove(CoordinateUtils.getChunkKey(pos));
            this.queuedChunkLoads.remove(CoordinateUtils.getChunkKey(pos));
            this.queuedEdgeChecksBlock.remove(CoordinateUtils.getChunkKey(pos));
            this.queuedEdgeChecksSky.remove(CoordinateUtils.getChunkKey(pos));
        }
    }

    /**
     * @reason Replace light views with our own that hook into the new light engine instead of vanilla's
     * @author Spottedleaf
     */
    @Overwrite
    public LayerLightEventListener getLayerListener(final LightLayer lightType) {
        return lightType == LightLayer.BLOCK ? this.lightEngine.getBlockReader() : this.lightEngine.getSkyReader();
    }

    /**
     * @reason Data is loaded in differently on the client
     * @author Spottedleaf
     */
    @Overwrite
    public void queueSectionData(final LightLayer lightType, final SectionPos pos, @Nullable final DataLayer nibble,
                                   final boolean trustEdges) {
        // data storage changed with new light impl
        final ChunkAccess chunk = this.getLightEngine().getAnyChunkNow(pos.getX(), pos.getZ());
        switch (lightType) {
            case BLOCK: {
                final SWMRNibbleArray[] blockNibbles = this.blockLightMap.computeIfAbsent(CoordinateUtils.getChunkKey(pos), (final long keyInMap) -> {
                    return StarLightEngine.getFilledEmptyLight(this.lightEngine.getWorld());
                });

                final SWMRNibbleArray replacement;
                if (nibble == null) {
                    replacement = new SWMRNibbleArray(null, true);
                } else if (nibble.isEmpty()) {
                    replacement = new SWMRNibbleArray();
                } else {
                    replacement = new SWMRNibbleArray(nibble.getData().clone()); // make sure we don't write to the parameter later
                }

                blockNibbles[pos.getY() - WorldUtil.getMinLightSection(this.lightEngine.getWorld())] = replacement;

                if (chunk != null) {
                    ((ExtendedChunk)chunk).setBlockNibbles(blockNibbles);
                    this.lightEngine.getLightAccess().onLightUpdate(LightLayer.BLOCK, pos);
                }
                break;
            }
            case SKY: {
                final SWMRNibbleArray[] skyNibbles = this.skyLightMap.computeIfAbsent(CoordinateUtils.getChunkKey(pos), (final long keyInMap) -> {
                    return StarLightEngine.getFilledEmptyLight(this.lightEngine.getWorld());
                });

                final SWMRNibbleArray replacement;
                if (nibble == null) {
                    replacement = new SWMRNibbleArray(null, true);
                } else if (nibble.isEmpty()) {
                    replacement = new SWMRNibbleArray();
                } else {
                    replacement = new SWMRNibbleArray(nibble.getData().clone()); // make sure we don't write to the parameter later
                }

                skyNibbles[pos.getY() - WorldUtil.getMinLightSection(this.lightEngine.getWorld())] = replacement;

                if (chunk != null) {
                    ((ExtendedChunk)chunk).setSkyNibbles(skyNibbles);
                    this.lightEngine.getLightAccess().onLightUpdate(LightLayer.SKY, pos);
                }
                break;
            }
        }
    }

    /**
     * @reason Avoid messing with the vanilla light engine state
     * @author Spottedleaf
     */
    @Overwrite
    public void retainData(final ChunkPos pos, final boolean retainData) {
        // not used by new light impl
    }

    /**
     * @reason Need to use our own hooks for retrieving light data
     * @author Spottedleaf
     */
    @Overwrite
    public int getRawBrightness(final BlockPos pos, final int ambientDarkness) {
        // need to use new light hooks for this
        final int sky = this.lightEngine.getSkyReader().getLightValue(pos) - ambientDarkness;
        final int block = this.lightEngine.getBlockReader().getLightValue(pos);
        return Math.max(sky, block);
    }
}
