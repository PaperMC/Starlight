package ca.spottedleaf.starlight.common.chunk;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;

public interface ExtendedChunk {

    public SWMRNibbleArray[] getBlockNibbles();
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles);

    public SWMRNibbleArray[] getSkyNibbles();
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles);

    public boolean[] getEmptinessMap();
    public void setEmptinessMap(final boolean[] emptinessMap);
}
