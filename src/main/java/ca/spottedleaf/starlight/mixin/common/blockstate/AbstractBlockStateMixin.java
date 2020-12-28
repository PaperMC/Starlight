package ca.spottedleaf.starlight.mixin.common.blockstate;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.Property;
import net.minecraft.state.StateHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javax.annotation.Nullable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin extends StateHolder<Block, BlockState> implements ExtendedAbstractBlockState {

    protected AbstractBlockStateMixin(final Block p_i231879_1_, final ImmutableMap<Property<?>, Comparable<?>> p_i231879_2_,
                                      final MapCodec<BlockState> p_i231879_3_) {
        super(p_i231879_1_, p_i231879_2_, p_i231879_3_);
    }

    @Shadow
    @Final
    private boolean isSolid;

    @Shadow
    @Final
    private boolean transparent;

    @Shadow
    @Nullable
    protected AbstractBlock.AbstractBlockState.Cache cache;


    @Unique
    private int opacityIfCached;

    @Unique
    private boolean isConditionallyFullOpaque;

    /**
     * Initialises our light state for this block.
     */
    @Inject(
            method = "cacheState",
            at = @At("RETURN")
    )
    public void initLightAccessState(final CallbackInfo ci) {
        this.isConditionallyFullOpaque = this.isSolid & this.transparent; // oof this mapping.
        this.opacityIfCached = this.cache == null || this.isConditionallyFullOpaque ? -1 : this.cache.opacity;
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
