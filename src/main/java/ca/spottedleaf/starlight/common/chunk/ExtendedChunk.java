package ca.spottedleaf.starlight.common.chunk;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;

public interface ExtendedChunk {

    public SWMRNibbleArray[] getBlockNibbles();
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles);

    public SWMRNibbleArray[] getSkyNibbles();
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles);

    // cx, cz are relative to the target chunk's map
    public static int getEmptinessMapIndex(final int cx, final int cz) {
        //return (cx + 1) + 3*(cz + 1);
        return (1 + 3 * 1) + (cx) + 3*(cz);
    }
    public boolean[][] getEmptinessMap();
}
