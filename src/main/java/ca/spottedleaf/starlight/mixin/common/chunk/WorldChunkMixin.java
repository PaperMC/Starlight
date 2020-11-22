package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.chunk.NibbledChunk;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.TickScheduler;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeArray;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin implements NibbledChunk, Chunk {

    @Unique
    private volatile SWMRNibbleArray[] blockNibbles;
    @Unique
    private volatile SWMRNibbleArray[] skyNibbles;
    @Unique
    private boolean wasLoadedFromDisk;

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

    @Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/ProtoChunk;)V", at = @At("TAIL"))
    public void onTransitionToFull(World world, ProtoChunk protoChunk, CallbackInfo ci) {
        this.setBlockNibbles(((NibbledChunk)protoChunk).getBlockNibbles());
        this.setSkyNibbles(((NibbledChunk)protoChunk).getSkyNibbles());
        this.setWasLoadedFromDisk(((NibbledChunk)protoChunk).wasLoadedFromDisk());
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/biome/source/BiomeArray;Lnet/minecraft/world/chunk/UpgradeData;Lnet/minecraft/world/TickScheduler;Lnet/minecraft/world/TickScheduler;J[Lnet/minecraft/world/chunk/ChunkSection;Ljava/util/function/Consumer;)V", at = @At("TAIL"))
    public void onConstruct(World world, ChunkPos pos, BiomeArray biomes, UpgradeData upgradeData, TickScheduler<Block> blockTickScheduler, TickScheduler<Fluid> fluidTickScheduler, long inhabitedTime, @Nullable ChunkSection[] sections, @Nullable Consumer<WorldChunk> loadToWorldConsumer, CallbackInfo ci) {
        this.skyNibbles = StarLightEngine.getFilledEmptyLight(true);
        this.blockNibbles = StarLightEngine.getFilledEmptyLight(false);
        this.wasLoadedFromDisk = false;
    }
}
