package ca.spottedleaf.starlight.mixin.common.blockstate;

import ca.spottedleaf.starlight.common.blockstate.LightAccessBlockState;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin implements LightAccessBlockState {

    @Shadow
    @Final
    private boolean hasSidedTransparency;

    @Shadow
    @Final
    private boolean opaque;

    @Shadow
    public abstract Block getBlock();

    @Shadow
    public abstract int getOpacity(BlockView world, BlockPos pos);

    @Unique
    private int opacityIfCached = -1;

    @Unique
    private boolean isConditionallyFullOpaque = true;

    @Inject(method = "initShapeCache", at = @At("RETURN"))
    public void initLightAccessState(CallbackInfo ci) {
        this.isConditionallyFullOpaque = this.opaque & this.hasSidedTransparency;
        this.opacityIfCached = this.getBlock().hasDynamicBounds() || this.isConditionallyFullOpaque() ? -1 : this.getOpacity(null, null);
    }

    @Override
    public final boolean isConditionallyFullOpaque() {
        return this.isConditionallyFullOpaque;
    }

    @Override
    public final int getOpacityIfCached() {
        return this.opacityIfCached;
    }
}
