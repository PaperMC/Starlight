package ca.spottedleaf.starlight.mixin.common.world;

import ca.spottedleaf.starlight.common.light.VariableBlockLightHandler;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(World.class)
public abstract class WorldMixin implements IWorld, AutoCloseable, ExtendedWorld {

    @Unique
    private VariableBlockLightHandler customBlockLightHandler;

    @Override
    public final VariableBlockLightHandler getCustomLightHandler() {
        return this.customBlockLightHandler;
    }

    @Override
    public final void setCustomLightHandler(final VariableBlockLightHandler handler) {
        this.customBlockLightHandler = handler;
    }

}
