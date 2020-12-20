package ca.spottedleaf.starlight.mixin.chunk;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Chunk.class)
public interface ChunkMixin extends ExtendedChunk {}