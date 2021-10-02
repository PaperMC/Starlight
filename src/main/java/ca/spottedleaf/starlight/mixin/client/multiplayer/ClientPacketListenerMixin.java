package ca.spottedleaf.starlight.mixin.client.multiplayer;

import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin implements ClientGamePacketListener {

    /*
    The call behaviors in the packet handler are much more clear about how they should affect the light engine,
    and as a result makes the client light load/unload more reliable
    */

    @Shadow
    private ClientLevel level;

    @Shadow
    protected abstract void applyLightData(final int chunkX, final int chunkZ, final ClientboundLightUpdatePacketData clientboundLightUpdatePacketData);

    /**
     * Re-route light update packet to our own logic
     * @author Spottedleaf
     */
    @Redirect(
            method = "readSectionList",
            at = @At(
                    target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;queueSectionData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;Z)V",
                    value = "INVOKE",
                    ordinal = 0
            )
    )
    private void loadLightDataHook(final LevelLightEngine lightEngine, final LightLayer lightType, final SectionPos pos,
                                   final @Nullable DataLayer nibble, final boolean trustEdges) {
        ((StarLightLightingProvider)this.level.getChunkSource().getLightEngine()).clientUpdateLight(lightType, pos, nibble, trustEdges);
    }


    /**
     * Use this hook to completely destroy light data loaded
     * @author Spottedleaf
     */
    @Inject(
            method = "handleForgetLevelChunk",
            at = @At("RETURN")
    )
    private void unloadLightDataHook(final ClientboundForgetLevelChunkPacket clientboundForgetLevelChunkPacket, final CallbackInfo ci) {
        ((StarLightLightingProvider)this.level.getChunkSource().getLightEngine()).clientRemoveLightData(new ChunkPos(clientboundForgetLevelChunkPacket.getX(), clientboundForgetLevelChunkPacket.getZ()));
    }

    /**
     * Hook for loading in a chunk to the world
     * @author Spottedleaf
     */
    @Redirect(
            method = "handleLevelChunkWithLight",
            at = @At(
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;queueLightUpdate(IILnet/minecraft/network/protocol/game/ClientboundLightUpdatePacketData;)V",
                    value = "INVOKE",
                    ordinal = 0
            )
    )
    private void postChunkLoadHook(final ClientPacketListener clientPacketListener, final int chunkX, final int chunkZ,
                                   final ClientboundLightUpdatePacketData clientboundLightUpdatePacketData) {
        final LevelChunk chunk = (LevelChunk)this.level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            // failed to load
            return;
        }
        // load in light data from packet immediately
        this.applyLightData(chunkX, chunkZ, clientboundLightUpdatePacketData);
        ((StarLightLightingProvider)this.level.getChunkSource().getLightEngine()).clientChunkLoad(new ChunkPos(chunkX, chunkZ), chunk);
    }
}
