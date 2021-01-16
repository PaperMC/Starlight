package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin implements ExtendedChunk, Chunk {

    @Unique
    private volatile SWMRNibbleArray[] blockNibbles;

    @Unique
    private volatile SWMRNibbleArray[] skyNibbles;

    @Unique
    private volatile boolean[] emptinessMap;

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
    public boolean[] getEmptinessMap() {
        return this.emptinessMap;
    }

    @Override
    public void setEmptinessMap(final boolean[] emptinessMap) {
        this.emptinessMap = emptinessMap;
    }

    /**
     * Copies the nibble data from the protochunk.
     * TODO since this is a constructor inject, check for new constructors on update.
     */
    @Inject(
            method = "<init>(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/ProtoChunk;Ljava/util/function/Consumer;)V",
            at = @At("TAIL")
    )
    public void onTransitionToFull(final ServerWorld serverWorld, final ProtoChunk protoChunk, final Consumer<WorldChunk> consumer,
                                   final CallbackInfo ci) {
        this.setBlockNibbles(((ExtendedChunk)protoChunk).getBlockNibbles());
        this.setSkyNibbles(((ExtendedChunk)protoChunk).getSkyNibbles());
        this.setEmptinessMap(((ExtendedChunk)protoChunk).getEmptinessMap());
    }

    /**
     * Initialises the nibble arrays to default values.
     * TODO since this is a constructor inject, check for new constructors on update.
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/biome/source/BiomeArray;Lnet/minecraft/world/chunk/UpgradeData;Lnet/minecraft/world/TickScheduler;Lnet/minecraft/world/TickScheduler;J[Lnet/minecraft/world/chunk/ChunkSection;Ljava/util/function/Consumer;)V",
            at = @At("TAIL")
    )
    public void onConstruct(final World world, final ChunkPos pos, final BiomeArray biomes, final UpgradeData upgradeData,
                            final TickScheduler<Block> blockTickScheduler, final TickScheduler<Fluid> fluidTickScheduler,
                            final long inhabitedTime, final ChunkSection[] sections, final Consumer<WorldChunk> loadToWorldConsumer,
                            final CallbackInfo ci) {
        this.blockNibbles = StarLightEngine.getFilledEmptyLight(world);
        this.skyNibbles = StarLightEngine.getFilledEmptyLight(world);
    }
}
