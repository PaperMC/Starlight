package ca.spottedleaf.starlight.mixin.common.blockstate;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.State;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin extends State<Block, BlockState> implements ExtendedAbstractBlockState {

    protected AbstractBlockStateMixin(final Block owner, final ImmutableMap<Property<?>, Comparable<?>> entries,
                                      final MapCodec<BlockState> codec) {
        super(owner, entries, codec);
    }

    @Shadow
    @Final
    private boolean hasSidedTransparency;

    @Shadow
    @Final
    private boolean opaque;

    @Shadow
    protected AbstractBlock.AbstractBlockState.ShapeCache shapeCache;

    @Unique
    private int opacityIfCached;

    @Unique
    private boolean isConditionallyFullOpaque;

    /**
     * Initialises our light state for this block.
     */
    @Inject(
            method = "initShapeCache",
            at = @At("RETURN")
    )
    public void initLightAccessState(final CallbackInfo ci) {
        this.isConditionallyFullOpaque = this.opaque & this.hasSidedTransparency;
        this.opacityIfCached = this.shapeCache == null || this.isConditionallyFullOpaque ? -1 : this.shapeCache.lightSubtracted;
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
