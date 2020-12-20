package ca.spottedleaf.starlight.mixin.world;

import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(World.class)
public abstract class WorldMixin implements WorldAccess, AutoCloseable, ExtendedWorld {}
