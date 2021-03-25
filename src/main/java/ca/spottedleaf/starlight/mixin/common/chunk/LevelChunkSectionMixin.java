package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import ca.spottedleaf.starlight.common.chunk.ExtendedChunkSection;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin implements ExtendedChunkSection {

    @Final
    @Shadow
    public PalettedContainer<BlockState> states;

    @Unique
    protected int transparentBlockCount;

    @Unique
    private final long[] knownBlockTransparencies = new long[16 * 16 * 16 * 2 / Long.SIZE]; // blocks * bits per block / bits per long

    @Unique
    private static long getKnownTransparency(final BlockState state) {
        final int opacityIfCached = ((ExtendedAbstractBlockState)state).getOpacityIfCached();

        if (opacityIfCached == 0) {
            return ExtendedChunkSection.BLOCK_IS_TRANSPARENT;
        }
        if (opacityIfCached == 15) {
            return ExtendedChunkSection.BLOCK_IS_FULL_OPAQUE;
        }

        return opacityIfCached == -1 ? ExtendedChunkSection.BLOCK_SPECIAL_TRANSPARENCY : ExtendedChunkSection.BLOCK_UNKNOWN_TRANSPARENCY;
    }

    /* NOTE: Index is y | (x << 4) | (z << 8) */
    @Unique
    private void updateTransparencyInfo(final int blockIndex, final long transparency) {
        final int arrayIndex = (blockIndex >>> (6 - 1)); // blockIndex / (64/2)
        final int valueShift = (blockIndex & (Long.SIZE / 2 - 1)) << 1;

        long value = this.knownBlockTransparencies[arrayIndex];

        value &= ~(0b11L << valueShift);
        value |= (transparency << valueShift);

        this.knownBlockTransparencies[arrayIndex] = value;
    }

    @Unique
    private void initKnownTransparenciesData() {
        this.transparentBlockCount = 0;
        for (int y = 0; y <= 15; ++y) {
            for (int z = 0; z <= 15; ++z) {
                for (int x = 0; x <= 15; ++x) {
                    final long transparency = getKnownTransparency(this.states.get(x, y, z));
                    if (transparency == ExtendedChunkSection.BLOCK_IS_TRANSPARENT) {
                        ++this.transparentBlockCount;
                    }
                    this.updateTransparencyInfo(y | (x << 4) | (z << 8), transparency);
                }
            }
        }
    }

    /**
     * Callback used to initialise the transparency data serverside. This only is for the server side since
     * calculateCounts is not called clientside.
     */
    @Inject(
            method = "recalcBlockCounts",
            at = @At("RETURN")
    )
    private void initKnownTransparenciesDataServerSide(final CallbackInfo ci) {
        this.initKnownTransparenciesData();
    }

    /**
     * Callback used to initialise the transparency data clientside. This is only for the client side as
     * calculateCounts is called server side, and fromPacket is only used clientside.
     */
    @Environment(EnvType.CLIENT)
    @Inject(
            method = "read",
            at = @At("RETURN")
    )
    private void initKnownTransparenciesDataClientSide(final CallbackInfo ci) {
        this.initKnownTransparenciesData();
    }

    /**
     * Callback used to update the transparency data on block update.
     */
    @Inject(
            method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN")
    )
    private void updateBlockCallback(final int x, final int y, final int z, final BlockState state, final boolean lock,
                                     final CallbackInfoReturnable<BlockState> cir) {
        final BlockState oldState = cir.getReturnValue();
        final long oldTransparency = getKnownTransparency(oldState);
        final long newTransparency = getKnownTransparency(state);

        if (oldTransparency == ExtendedChunkSection.BLOCK_IS_TRANSPARENT) {
            --this.transparentBlockCount;
        }
        if (newTransparency == ExtendedChunkSection.BLOCK_IS_TRANSPARENT) {
            ++this.transparentBlockCount;
        }

        this.updateTransparencyInfo(y | (x << 4) | (z << 8), newTransparency);
    }

    @Override
    public final boolean hasOpaqueBlocks() {
        return this.transparentBlockCount != 4096;
    }

    @Override
    public final long getKnownTransparency(final int blockIndex) {
        // index = y | (x << 4) | (z << 8)
        final int arrayIndex = (blockIndex >>> (6 - 1)); // blockIndex / (64/2)
        final int valueShift = (blockIndex & (Long.SIZE / 2 - 1)) << 1;

        final long value = this.knownBlockTransparencies[arrayIndex];

        return (value >>> valueShift) & 0b11L;
    }


    @Override
    public final long getBitsetForColumn(final int columnX, final int columnZ) {
        // index = y | (x << 4) | (z << 8)
        final int columnIndex = (columnX << 4) | (columnZ << 8);
        final long value = this.knownBlockTransparencies[columnIndex >>> (6 - 1)]; // columnIndex / (64/2)

        final int startIndex = (columnIndex & (Long.SIZE / 2 - 1)) << 1;

        return (value >>> startIndex) & ((1L << (16 * 2)) - 1);
    }
}
