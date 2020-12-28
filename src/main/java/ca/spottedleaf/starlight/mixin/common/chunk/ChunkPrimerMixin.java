package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.palette.UpgradeData;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.ChunkPrimerTickList;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkPrimer.class)
public abstract class ChunkPrimerMixin implements IChunk, ExtendedChunk {

    @Unique
    private volatile SWMRNibbleArray[] blockNibbles;

    @Unique
    private volatile SWMRNibbleArray[] skyNibbles;

    @Unique
    private boolean[][] emptinessMap;

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return this.blockNibbles;
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        this.blockNibbles = nibbles;
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return this.skyNibbles;
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        this.skyNibbles = nibbles;
    }

    @Override
    public boolean[][] getEmptinessMap() {
        return this.emptinessMap;
    }

    /**
     * Initialises the nibble arrays to default values.
     * TODO since this is a constructor inject, check for new constructors on update.
     */
    @Inject(
            method = "<init>(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/util/palette/UpgradeData;[Lnet/minecraft/world/chunk/ChunkSection;Lnet/minecraft/world/chunk/ChunkPrimerTickList;Lnet/minecraft/world/chunk/ChunkPrimerTickList;)V",
            at = @At("TAIL")
    )
    public void onConstruct(final ChunkPos pos, final UpgradeData upgradeData, final ChunkSection[] sections,
                            final ChunkPrimerTickList<Block> pendingBlockTicks, final ChunkPrimerTickList<Fluid> pendingFluidTicks,
                            final CallbackInfo ci) {
        this.blockNibbles = StarLightEngine.getFilledEmptyLight();
        this.skyNibbles = StarLightEngine.getFilledEmptyLight();
        this.emptinessMap = new boolean[9][];
    }
}
