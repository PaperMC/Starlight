package ca.spottedleaf.starlight.mixin.common.lightengine;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.util.WorldUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.IChunkLightProvider;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.ILightListener;
import net.minecraft.world.lighting.IWorldLightListener;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.world.server.ServerWorldLightManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Iterator;

@Mixin(WorldLightManager.class)
public abstract class WorldLightManagerMixin implements ILightListener, StarLightLightingProvider {

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
    public void construct(final IChunkLightProvider provider, final boolean hasBlockLight, final boolean hasSkyLight,
                          final CallbackInfo ci) {
        this.lightEngine = new StarLightInterface(provider, hasSkyLight, hasBlockLight);
    }

    /**
     * @reason Route to new light engine
     * @author Spottedleaf
     */
    @Overwrite
    public void checkBlock(final BlockPos pos) {
        this.lightEngine.blockChange(pos.toImmutable());
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
    public int tick(final int maxUpdateCount, final boolean doSkylight, final boolean skipEdgeLightPropagation) {
        // replace impl
        if ((Object)this instanceof ServerWorldLightManager) {
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
        final IChunk chunk = this.getLightEngine().getAnyChunkNow(pos.x, pos.z);
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
    public IWorldLightListener getLightEngine(final LightType lightType) {
        return lightType == LightType.BLOCK ? this.lightEngine.getBlockReader() : this.lightEngine.getSkyReader();
    }

    /**
     * @reason Data is loaded in differently on the client
     * @author Spottedleaf
     */
    @Overwrite
    public void setData(final LightType lightType, final SectionPos pos, final NibbleArray nibble,
                        final boolean trustEdges) {
        // data storage changed with new light impl
        final IChunk chunk = this.getLightEngine().getAnyChunkNow(pos.getX(), pos.getZ());
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
                    this.lightEngine.getLightAccess().markLightChanged(LightType.BLOCK, pos);
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
                    this.lightEngine.getLightAccess().markLightChanged(LightType.SKY, pos);
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
    public int getLightSubtracted(final BlockPos pos, final int ambientDarkness) {
        // need to use new light hooks for this
        final int sky = this.lightEngine.getSkyReader().getLightFor(pos) - ambientDarkness;
        final int block = this.lightEngine.getBlockReader().getLightFor(pos);
        return Math.max(sky, block);
    }
}
