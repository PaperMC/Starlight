package ca.spottedleaf.starlight.common.chunk;

public interface ExtendedChunkSection {

    public static final long BLOCK_IS_TRANSPARENT = 0b00;
    public static final long BLOCK_IS_FULL_OPAQUE = 0b01;
    public static final long BLOCK_UNKNOWN_TRANSPARENCY = 0b10;
    public static final long BLOCK_SPECIAL_TRANSPARENCY = 0b11;

    public boolean hasOpaqueBlocks();

    /* NOTE: Index is y | (x << 4) | (z << 8) */
    public long getKnownTransparency(final int blockIndex);

    public long getBitsetForColumn(final int columnX, final int columnZ);
}
