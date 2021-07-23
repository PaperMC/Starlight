package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin {

    @Shadow
    public boolean resendLight;

    @Unique
    private int blockChangesForLight;

    @Shadow
    @Final
    ChunkPos pos;

    @Shadow
    @Final
    private LevelHeightAccessor levelHeightAccessor;

    @Shadow
    private boolean hasChangedSections;

    /**
     * @reason
     * Rewrites the resendLight field if any of the neighbours inside one radius have it set. This fixes vanilla's
     * incorrect broadcasting of the section light data since it will tell the client two things:
     * 1. Do not run checkBlock for blocks changed (the server will send the light for the chunk, but not its neighbours - which is what is being FIXED here)
     * 2. Do not edge check the incoming light data
     *
     * With both of the above combined, the client is not going to be able to run the correct logic to handle light data
     * on chunk borders - it's told to simply not check it in any capacity. This could be fixed by telling the client
     * to check edges, but that could be costly - checking light for large block updates is not always cheap.
     *
     * @author Spottedleaf
     */
    @Redirect(
            method = "blockChanged",
            at = @At(
                    target = "Lit/unimi/dsi/fastutil/shorts/ShortSet;add(S)Z",
                    value = "INVOKE"
            )
    )
    private boolean fixLightBroadcastingHook(final ShortSet shorts, final short key) {
        if (!shorts.add(key)) {
            return false;
        }

        final ChunkMap chunkMap = ((ServerLevel)this.levelHeightAccessor).getChunkSource().chunkMap;

        if (++this.blockChangesForLight == ChunkHolder.BLOCKS_BEFORE_RESEND_FUDGE) {
            this.resendLight = true;
            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    if ((dx | dz) == 0) {
                        continue;
                    }

                    final int cx = this.pos.x + dx;
                    final int cz = this.pos.z + dz;

                    final ChunkHolder neighbour = chunkMap.getVisibleChunkIfPresent(CoordinateUtils.getChunkKey(cx, cz));
                    if (neighbour != null) {
                        neighbour.resendLight = true;
                    }
                }
            }
        }

        return true;
    }

    /**
     * @reason Used to reset blockChangesForLight when changes are sent
     * @author Spottedleaf
     */
    @Redirect(
            method = "broadcastChanges",
            at = @At(
                    target = "Lnet/minecraft/server/level/ChunkHolder;hasChangedSections:Z",
                    value = "FIELD",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void resetBlockChangeCountForLightSendFix(final ChunkHolder holder, final boolean value) {
        this.hasChangedSections = value;
        if (!value) {
            this.blockChangesForLight = 0;
        }
    }
}
