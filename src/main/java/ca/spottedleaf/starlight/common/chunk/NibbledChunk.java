package ca.spottedleaf.starlight.common.chunk;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;

public interface NibbledChunk {

    public SWMRNibbleArray[] getBlockNibbles();
    public void setBlockNibbles(SWMRNibbleArray[] nibbles);

    public SWMRNibbleArray[] getSkyNibbles();
    public void setSkyNibbles(SWMRNibbleArray[] nibbles);

    public boolean wasLoadedFromDisk();

    public void setWasLoadedFromDisk(boolean value);
}
