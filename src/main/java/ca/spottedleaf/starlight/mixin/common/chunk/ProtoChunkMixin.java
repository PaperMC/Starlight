package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ProtoTickList;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin implements ChunkAccess, ExtendedChunk {

    @Unique
    private volatile SWMRNibbleArray[] blockNibbles;

    @Unique
    private volatile SWMRNibbleArray[] skyNibbles;

    @Unique
    private volatile boolean[] skyEmptinessMap;

    @Unique
    private volatile boolean[] blockEmptinessMap;

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
    public boolean[] getSkyEmptinessMap() {
        return this.skyEmptinessMap;
    }

    @Override
    public void setSkyEmptinessMap(final boolean[] emptinessMap) {
        this.skyEmptinessMap = emptinessMap;
    }

    @Override
    public boolean[] getBlockEmptinessMap() {
        return this.blockEmptinessMap;
    }

    @Override
    public void setBlockEmptinessMap(final boolean[] emptinessMap) {
        this.blockEmptinessMap = emptinessMap;
    }

    /**
     * Initialises the nibble arrays to default values.
     * TODO since this is a constructor inject, check for new constructors on update.
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/ProtoTickList;Lnet/minecraft/world/level/chunk/ProtoTickList;)V",
            at = @At("TAIL")
    )
    public void onConstruct(final ChunkPos chunkPos, final UpgradeData upgradeData, final @Nullable LevelChunkSection[] levelChunkSections,
                            final ProtoTickList<Block> protoTickList, final ProtoTickList<Fluid> protoTickList2,
                            final CallbackInfo ci) {
        this.blockNibbles = StarLightEngine.getFilledEmptyLight();
        this.skyNibbles = StarLightEngine.getFilledEmptyLight();
    }
}
