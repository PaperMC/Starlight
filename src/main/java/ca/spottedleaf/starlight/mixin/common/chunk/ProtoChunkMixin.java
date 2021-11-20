package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin implements ExtendedChunk {

    /**
     * Initialises the nibble arrays to default values.
     * TODO since this is a constructor inject, check for new constructors on update.
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/ticks/ProtoChunkTicks;Lnet/minecraft/world/ticks/ProtoChunkTicks;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
            at = @At("TAIL")
    )
    public void onConstruct(ChunkPos chunkPos, UpgradeData upgradeData, LevelChunkSection[] levelChunkSections, ProtoChunkTicks protoChunkTicks, ProtoChunkTicks protoChunkTicks2, LevelHeightAccessor levelHeightAccessor, Registry registry, BlendingData blendingData, CallbackInfo ci) {
        if ((Object)this instanceof ImposterProtoChunk) {
            return;
        }
        this.setBlockNibbles(StarLightEngine.getFilledEmptyLight(levelHeightAccessor));
        this.setSkyNibbles(StarLightEngine.getFilledEmptyLight(levelHeightAccessor));
    }
}
