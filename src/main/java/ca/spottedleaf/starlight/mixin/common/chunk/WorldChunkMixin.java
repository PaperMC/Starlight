package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.palette.UpgradeData;
import net.minecraft.world.ITickList;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(Chunk.class)
public abstract class WorldChunkMixin implements ExtendedChunk, IChunk {

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
     * Copies the nibble data from the protochunk.
     * TODO since this is a constructor inject, check for new constructors on update.
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/ChunkPrimer;)V",
            at = @At("TAIL")
    )
    public void onTransitionToFull(final World world, final ChunkPrimer protoChunk, final CallbackInfo ci) {
        this.setBlockNibbles(((ExtendedChunk)protoChunk).getBlockNibbles());
        this.setSkyNibbles(((ExtendedChunk)protoChunk).getSkyNibbles());
        this.setSkyEmptinessMap(((ExtendedChunk)protoChunk).getSkyEmptinessMap());
        this.setBlockEmptinessMap(((ExtendedChunk)protoChunk).getBlockEmptinessMap());
    }

    /**
     * Initialises the nibble arrays to default values.
     * TODO since this is a constructor inject, check for new constructors on update.
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/biome/BiomeContainer;Lnet/minecraft/util/palette/UpgradeData;Lnet/minecraft/world/ITickList;Lnet/minecraft/world/ITickList;J[Lnet/minecraft/world/chunk/ChunkSection;Ljava/util/function/Consumer;)V",
            at = @At("TAIL")
    )
    public void onConstruct(final World worldIn, final ChunkPos chunkPosIn, final BiomeContainer biomeContainerIn,
                            final UpgradeData upgradeDataIn, final ITickList<Block> tickBlocksIn, final ITickList<Fluid> tickFluidsIn,
                            final long inhabitedTimeIn, final ChunkSection[] sectionsIn, final Consumer<Chunk> postLoadConsumerIn,
                            final CallbackInfo ci) {
        this.blockNibbles = StarLightEngine.getFilledEmptyLight(worldIn);
        this.skyNibbles = StarLightEngine.getFilledEmptyLight(worldIn);
    }
}
