package ca.spottedleaf.starlight.mixin.common.chunk;

import ca.spottedleaf.starlight.common.chunk.ThreadedAnvilChunkStorageMethods;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.thread.ThreadExecutor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.function.IntSupplier;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ThreadedAnvilChunkStorageMixin implements ThreadedAnvilChunkStorageMethods {

    @Final
    @Shadow
    private ThreadExecutor<Runnable> mainThreadExecutor;

    @Shadow
    protected abstract IntSupplier getCompletedLevelSupplier(long pos);

    @Shadow
    protected abstract void releaseLightTicket(ChunkPos pos);

    @Shadow
    protected @Nullable abstract ChunkHolder getChunkHolder(long pos);

    @Override
    public final IntSupplier getCompletedLevelSupplierPublic(long pos) {
        return this.getCompletedLevelSupplier(pos);
    }

    @Override
    public final void releaseLightTicketPublic(ChunkPos pos) {
        this.releaseLightTicket(pos);
    }

    @Override
    public final void scheduleOntoMain(Runnable runnable) {
        this.mainThreadExecutor.execute(runnable);
    }

    @Override
    public final ChunkHolder getOffMainChunkHolder(long pos) {
        return this.getChunkHolder(pos);
    }
}
