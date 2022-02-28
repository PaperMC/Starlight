package ca.spottedleaf.starlight.mixin.common.blockstate;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin extends StateHolder<Block, BlockState> implements ExtendedAbstractBlockState {

    @Shadow
    @Final
    private boolean useShapeForLightOcclusion;

    @Shadow
    @Final
    private boolean canOcclude;

    @Shadow
    protected BlockBehaviour.BlockStateBase.Cache cache;

    @Shadow
    public abstract Block getBlock();

    @Unique
    private int opacityIfCached;

    @Unique
    private boolean isConditionallyFullOpaque;

    protected BlockStateBaseMixin(final Block object, final ImmutableMap<Property<?>, Comparable<?>> immutableMap, final MapCodec<BlockState> mapCodec) {
        super(object, immutableMap, mapCodec);
    }

    /**
     * Initialises our light state for this block.
     */
    @Inject(
            method = "initCache",
            at = @At("RETURN")
    )
    public void initLightAccessState(final CallbackInfo ci) {
        this.isConditionallyFullOpaque = this.canOcclude & this.useShapeForLightOcclusion;
        this.opacityIfCached = this.cache == null || this.isConditionallyFullOpaque ? -1 : this.cache.lightBlock;
        // Forge
        try {
            if (true) {
                this.opacityIfCached = -1;
            }
        } catch (final Exception ex) {
            this.opacityIfCached = -1;
        }
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
