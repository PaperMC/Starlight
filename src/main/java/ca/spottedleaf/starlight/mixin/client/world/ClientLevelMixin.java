package ca.spottedleaf.starlight.mixin.client.world;

import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.function.Supplier;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin extends Level implements ExtendedWorld {

    @Shadow
    public abstract ClientChunkCache getChunkSource();

    protected ClientLevelMixin(final WritableLevelData writableLevelData, final ResourceKey<Level> resourceKey, final Holder<DimensionType> dimensionType,
                               final Supplier<ProfilerFiller> supplier, final boolean bl, final boolean bl2, final long l, final int maxUpdates) {
        super(writableLevelData, resourceKey, dimensionType, supplier, bl, bl2, l, maxUpdates);
    }

    @Override
    public final LevelChunk getChunkAtImmediately(final int chunkX, final int chunkZ) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, false);
    }

    @Override
    public final ChunkAccess getAnyChunkImmediately(int chunkX, int chunkZ) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, false);
    }
}
