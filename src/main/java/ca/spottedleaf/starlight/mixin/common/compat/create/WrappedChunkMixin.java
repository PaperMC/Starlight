package ca.spottedleaf.starlight.mixin.common.compat.create;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import com.simibubi.create.foundation.utility.worldWrappers.PlacementSimulationWorld;
import com.simibubi.create.foundation.utility.worldWrappers.chunk.WrappedChunk;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author AeiouEnigma
 */
@Mixin(value = WrappedChunk.class, remap = false)
public class WrappedChunkMixin implements ExtendedChunk {

    @Unique
    private volatile SWMRNibbleArray[] blockNibbles;

    @Unique
    private volatile SWMRNibbleArray[] skyNibbles;

    @Unique
    private volatile boolean[] skyEmptinessMap;

    @Unique
    private volatile boolean[] blockEmptinessMap;

    @Final
    @Shadow(remap = false)
    private ChunkSection[] sections;

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return this.blockNibbles;
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        this.blockNibbles = nibbles;
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return this.skyNibbles;
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        this.skyNibbles = nibbles;
    }

    @Override
    public boolean[] getSkyEmptinessMap() {
        return this.skyEmptinessMap;
    }

    @Override
    public void setSkyEmptinessMap(final boolean[] emptinessMap) {
        this.skyEmptinessMap = emptinessMap;
    }

    @Override
    public boolean[] getBlockEmptinessMap() {
        return this.blockEmptinessMap;
    }

    @Override
    public void setBlockEmptinessMap(final boolean[] emptinessMap) {
        this.blockEmptinessMap = emptinessMap;
    }

    /**
     * Initialises the nibble data.
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void onConstruct(PlacementSimulationWorld world, int x, int z, CallbackInfo ci) {
        this.blockNibbles = this.skyNibbles = StarLightEngine.getFilledEmptyLight(world);
        this.skyEmptinessMap = this.blockEmptinessMap = this.getEmptySections();
    }

    public boolean[] getEmptySections() {
        boolean[] ret = new boolean[this.sections.length];

        for (int i = 0; i < this.sections.length; ++i) {
            ret[i] = (this.sections[i] == null || this.sections[i].isEmpty());
        }

        return ret;
    }
}
