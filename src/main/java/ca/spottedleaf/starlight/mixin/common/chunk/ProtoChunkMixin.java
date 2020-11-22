package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.chunk.NibbledChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkTickScheduler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin implements NibbledChunk, Chunk {

    @Unique
    private volatile SWMRNibbleArray[] blockNibbles;
    @Unique
    private volatile SWMRNibbleArray[] skyNibbles;
    @Unique
    private boolean wasLoadedFromDisk;

    @Inject(method = "<init>(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/chunk/UpgradeData;[Lnet/minecraft/world/chunk/ChunkSection;Lnet/minecraft/world/ChunkTickScheduler;Lnet/minecraft/world/ChunkTickScheduler;)V", at = @At("TAIL"))
    public void onConstruct(ChunkPos pos, UpgradeData upgradeData, @Nullable ChunkSection[] sections, ChunkTickScheduler<Block> blockTickScheduler, ChunkTickScheduler<Fluid> fluidTickScheduler, CallbackInfo ci) {
        this.blockNibbles = StarLightEngine.getFilledEmptyLight(false);
        this.skyNibbles = StarLightEngine.getFilledEmptyLight(true);
        this.wasLoadedFromDisk = false;
    }

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return this.blockNibbles;
    }

    @Override
    public void setBlockNibbles(SWMRNibbleArray[] nibbles) {
        this.blockNibbles = nibbles;
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return this.skyNibbles;
    }

    @Override
    public void setSkyNibbles(SWMRNibbleArray[] nibbles) {
        this.skyNibbles = nibbles;
    }

    @Override
    public boolean wasLoadedFromDisk() {
        return this.wasLoadedFromDisk;
    }

    @Override
    public void setWasLoadedFromDisk(boolean value) {
        this.wasLoadedFromDisk = value;
    }
}
