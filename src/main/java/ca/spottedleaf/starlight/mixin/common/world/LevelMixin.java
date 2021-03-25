package ca.spottedleaf.starlight.mixin.common.world;

import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelAccessor, AutoCloseable, ExtendedWorld {}
