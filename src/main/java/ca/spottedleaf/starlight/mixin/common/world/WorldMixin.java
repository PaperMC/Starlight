package ca.spottedleaf.starlight.mixin.common.world;

import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(World.class)
public abstract class WorldMixin implements IWorld, AutoCloseable, ExtendedWorld {}
