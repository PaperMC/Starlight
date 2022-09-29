package ca.spottedleaf.starlight.common.blockstate;

public interface ExtendedAbstractBlockState {

    boolean isConditionallyFullOpaque();

    int getOpacityIfCached();

}
