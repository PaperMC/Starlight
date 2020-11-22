package ca.spottedleaf.starlight.common.blockstate;

public interface LightAccessBlockState {

    public boolean isConditionallyFullOpaque();

    public int getOpacityIfCached();

}
