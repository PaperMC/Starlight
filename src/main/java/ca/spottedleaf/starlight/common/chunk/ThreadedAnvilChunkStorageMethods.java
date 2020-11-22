package ca.spottedleaf.starlight.common.chunk;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.util.math.ChunkPos;
import java.util.function.IntSupplier;

public interface ThreadedAnvilChunkStorageMethods {

    public IntSupplier getCompletedLevelSupplierPublic(long pos);

    public void releaseLightTicketPublic(ChunkPos pos);

    public void scheduleOntoMain(Runnable runnable);

    public ChunkHolder getOffMainChunkHolder(long pos);
}
