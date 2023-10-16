package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements ExtendedChunk {

    /**
     * Copies the nibble data from the protochunk.
     * TODO since this is a constructor inject, check for new constructors on update.
     */
    @Inject(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
            at = @At("TAIL")
    )
    public void onTransitionToFull(ServerLevel serverLevel, ProtoChunk protoChunk, LevelChunk.PostLoadProcessor postLoadProcessor, CallbackInfo ci) {
        this.setBlockNibbles(((ExtendedChunk)protoChunk).getBlockNibbles());
        this.setSkyNibbles(((ExtendedChunk)protoChunk).getSkyNibbles());
        this.setSkyEmptinessMap(((ExtendedChunk)protoChunk).getSkyEmptinessMap());
        this.setBlockEmptinessMap(((ExtendedChunk)protoChunk).getBlockEmptinessMap());
    }

    /**
     * @reason Remove unused skylight sources
     * @author Spottedleaf
     */
    @Redirect(
            method = "setBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;update(Lnet/minecraft/world/level/BlockGetter;III)Z"
            )
    )
    private boolean skipLightSources(final ChunkSkyLightSources instance, final BlockGetter blockGetter,
                                     final int x, final int y, final int z) {
        return false;
    }
}
