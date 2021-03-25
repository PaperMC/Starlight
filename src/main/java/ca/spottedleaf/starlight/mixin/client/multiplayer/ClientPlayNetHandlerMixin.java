package ca.spottedleaf.starlight.mixin.client.multiplayer;

import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.network.play.IClientPlayNetHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.play.server.SChunkDataPacket;
import net.minecraft.network.play.server.SUnloadChunkPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.WorldLightManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javax.annotation.Nullable;

@Mixin(ClientPlayNetHandler.class)
public abstract class ClientPlayNetHandlerMixin implements IClientPlayNetHandler {

    /*
    The call behaviors in the packet handler are much more clear about how they should affect the light engine,
    and as a result makes the client light load/unload more reliable
    */

    @Shadow
    private ClientWorld world;

    /**
     * Re-route light update packet to our own logic
     * @author Spottedleaf
     */
    @Redirect(
            method = "setLightData",
            at = @At(
                    target = "Lnet/minecraft/world/lighting/WorldLightManager;setData(Lnet/minecraft/world/LightType;Lnet/minecraft/util/math/SectionPos;Lnet/minecraft/world/chunk/NibbleArray;Z)V",
                    value = "INVOKE",
                    ordinal = 0
            )
    )
    private void loadLightDataHook(final WorldLightManager lightEngine, final LightType lightType, SectionPos pos, final @Nullable NibbleArray nibble,
                                   final boolean trustEdges) {
        ((StarLightLightingProvider)this.world.getLightManager()).clientUpdateLight(lightType, pos, nibble, trustEdges);
    }


    /**
     * Use this hook to completely destroy light data loaded
     * @author Spottedleaf
     */
    @Inject(
            method = "processChunkUnload",
            at = @At("RETURN")
    )
    private void unloadLightDataHook(final SUnloadChunkPacket clientboundForgetLevelChunkPacket, final CallbackInfo ci) {
        ((StarLightLightingProvider)this.world.getLightManager()).clientRemoveLightData(new ChunkPos(clientboundForgetLevelChunkPacket.getX(), clientboundForgetLevelChunkPacket.getZ()));
    }

    /**
     * Hook for loading in a chunk to the world
     * Note that the new chunk can be merged into the previous one and the new chunk can fail to load
     * @author Spottedleaf
     */
    @Inject(
            method = "handleChunkData",
            at = @At(
                    target = "Lnet/minecraft/client/multiplayer/ClientChunkProvider;loadChunk(IILnet/minecraft/world/biome/BiomeContainer;Lnet/minecraft/network/PacketBuffer;Lnet/minecraft/nbt/CompoundNBT;IZ)Lnet/minecraft/world/chunk/Chunk;",
                    value = "INVOKE",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void postChunkLoadHook(final SChunkDataPacket clientboundLevelChunkPacket, final CallbackInfo ci) {
        final int chunkX = clientboundLevelChunkPacket.getChunkX();
        final int chunkZ = clientboundLevelChunkPacket.getChunkZ();
        final Chunk chunk = (Chunk)this.world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            // failed to load
            return;
        }
        ((StarLightLightingProvider)this.world.getLightManager()).clientChunkLoad(new ChunkPos(chunkX, chunkZ), chunk);
    }
}
