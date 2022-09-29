package ca.spottedleaf.starlight.common.chunk;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;

public interface ExtendedChunk {

    SWMRNibbleArray[] getBlockNibbles();
    void setBlockNibbles(final SWMRNibbleArray[] nibbles);

    SWMRNibbleArray[] getSkyNibbles();
    void setSkyNibbles(final SWMRNibbleArray[] nibbles);

    boolean[] getSkyEmptinessMap();
    void setSkyEmptinessMap(final boolean[] emptinessMap);

    boolean[] getBlockEmptinessMap();
    void setBlockEmptinessMap(final boolean[] emptinessMap);
}
