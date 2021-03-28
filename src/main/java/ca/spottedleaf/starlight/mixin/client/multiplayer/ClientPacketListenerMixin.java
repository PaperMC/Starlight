package ca.spottedleaf.starlight.mixin.client.multiplayer;

import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
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
     * Note that the new chunk can be merged into the previous one and the new chunk can fail to load
     * @author Spottedleaf
     */
    @Inject(
            method = "handleLevelChunk",
            at = @At(
                    target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;replaceWithPacketData(IILnet/minecraft/world/level/chunk/ChunkBiomeContainer;Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/nbt/CompoundTag;IZ)Lnet/minecraft/world/level/chunk/LevelChunk;",
                    value = "INVOKE",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void postChunkLoadHook(final ClientboundLevelChunkPacket clientboundLevelChunkPacket, final CallbackInfo ci) {
        final int chunkX = clientboundLevelChunkPacket.getX();
        final int chunkZ = clientboundLevelChunkPacket.getZ();
        final LevelChunk chunk = (LevelChunk)this.level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            // failed to load
            return;
        }
        ((StarLightLightingProvider)this.level.getChunkSource().getLightEngine()).clientChunkLoad(new ChunkPos(chunkX, chunkZ), chunk);
    }
}
