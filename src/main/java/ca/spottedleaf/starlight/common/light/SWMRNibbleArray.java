package ca.spottedleaf.starlight.common.light;

import net.minecraft.world.chunk.ChunkNibbleArray;
import java.util.ArrayDeque;
import java.util.Arrays;

// SWMR -> Single Writer Multi Reader Nibble Array
public final class SWMRNibbleArray {

    public static final int ARRAY_SIZE = 16 * 16 * 16 / (8/4); // blocks / bytes per block
    protected static final byte[] FULL_LIT = new byte[ARRAY_SIZE];
    static {
        Arrays.fill(FULL_LIT, (byte)-1);
    }
    // this allows us to maintain only 1 byte array when we're not updating
    static final ThreadLocal<ArrayDeque<byte[]>> WORKING_BYTES_POOL = ThreadLocal.withInitial(ArrayDeque::new);

    private static byte[] allocateBytes() {
        final byte[] inPool = WORKING_BYTES_POOL.get().pollFirst();
        if (inPool != null) {
            return inPool;
        }

        return new byte[ARRAY_SIZE];
    }

    private static void freeBytes(final byte[] bytes) {
        WORKING_BYTES_POOL.get().addFirst(bytes);
    }

    protected byte[] workingBytes;
    protected byte[] visibleBytes;
    protected final int defaultNullValue;
    private boolean isNullNibble;

    public SWMRNibbleArray(final boolean isNullNibble, final int defaultNullValue) {
        this(null, defaultNullValue);
        this.isNullNibble = isNullNibble;
    }

    public SWMRNibbleArray() {
        this(null, 0); // lazy init
    }

    public SWMRNibbleArray(final byte[] bytes) {
        this(bytes, 0);
    }

    protected SWMRNibbleArray(final byte[] bytes, final int defaultNullValue) {
        if (bytes != null && bytes.length != ARRAY_SIZE) {
            throw new IllegalArgumentException();
        }
        this.defaultNullValue = defaultNullValue;
        this.visibleBytes = bytes != null ? bytes.clone() : null;
    }

    public boolean isDirty() {
        return this.workingBytes != null;
    }

    public boolean isNullNibbleUpdating() {
        return this.workingBytes == null && this.isNullNibble;
    }

    public boolean isNullNibbleVisible() {
        synchronized (this) {
            return this.isNullNibble;
        }
    }

    public void markNonNull() {
        synchronized (this) {
            this.isNullNibble = false;
        }
    }

    public boolean isInitialisedUpdating() {
        return this.workingBytes != null || this.visibleBytes != null;
    }

    public boolean isInitialisedVisible() {
        synchronized (this) {
            return this.visibleBytes != null;
        }
    }

    public void initialiseWorking() {
        if (this.workingBytes != null) {
            return;
        }
        final byte[] working = allocateBytes();
        this.copyIntoImpl(working, 0);
        this.workingBytes = working;
    }

    public void copyFrom(final byte[] src, final int off) {
        if (this.workingBytes == null) {
            this.workingBytes = allocateBytes();
        }
        System.arraycopy(src, off, this.workingBytes, 0, ARRAY_SIZE);
    }

    public boolean updateVisible() {
        if (this.workingBytes == null) {
            return false;

        }
        final byte[] oldVisible = this.visibleBytes;

        synchronized (this) {
            this.isNullNibble = false;

            this.visibleBytes = this.workingBytes;
            this.workingBytes = null;
        }

        if (oldVisible != null) {
            freeBytes(oldVisible);
        }

        return true;
    }

    public void copyInto(final byte[] bytes, final int off) {
        synchronized (this) {
            this.copyIntoImpl(bytes, off);
        }
    }

    protected void copyIntoImpl(final byte[] bytes, final int off) {
        if (this.visibleBytes != null) {
            System.arraycopy(this.visibleBytes, 0, bytes, off, ARRAY_SIZE);
        } else {
            if (this.isNullNibble && this.defaultNullValue != 0) {
                Arrays.fill(bytes, off, off + ARRAY_SIZE, (byte)(this.defaultNullValue | (this.defaultNullValue << 4)));
            } else {
                Arrays.fill(bytes, off, off + ARRAY_SIZE, (byte)0);
            }
        }
    }

    public ChunkNibbleArray asNibble() {
        synchronized (this) {
            return this.visibleBytes == null ? (this.isNullNibble ? null : new ChunkNibbleArray()) : new ChunkNibbleArray(this.visibleBytes.clone());
        }
    }

    public int getUpdating(final int x, final int y, final int z) {
        return this.getUpdating((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    public int getUpdating(final int index) {
        // indices range from 0 -> 4096
        byte[] bytes = this.workingBytes == null ? this.visibleBytes : this.workingBytes;
        if (bytes == null) {
            return this.isNullNibble ? this.defaultNullValue : 0;
        }
        final byte value = bytes[index >>> 1];

        // if we are an even index, we want lower 4 bits
        // if we are an odd index, we want upper 4 bits
        return ((value >>> ((index & 1) << 2)) & 0xF);
    }

    public int getVisible(final int x, final int y, final int z) {
        return this.getVisible((x & 15) | ((z & 15) << 4) | ((y & 15) << 8));
    }

    public int getVisible(final int index) {
        synchronized (this) {
            // indices range from 0 -> 4096
            if (this.visibleBytes == null) {
                return this.isNullNibble ? this.defaultNullValue : 0;
            }
            final byte value = this.visibleBytes[index >>> 1];

            // if we are an even index, we want lower 4 bits
            // if we are an odd index, we want upper 4 bits
            return ((value >>> ((index & 1) << 2)) & 0xF);
        }
    }

    public void set(final int x, final int y, final int z, final int value) {
        this.set((x & 15) | ((z & 15) << 4) | ((y & 15) << 8), value);
    }

    public void set(final int index, final int value) {
        if (this.workingBytes == null) {
            this.initialiseWorking();
        }
        final int shift = (index & 1) << 2;
        final int i = index >>> 1;

        this.workingBytes[i] = (byte)((this.workingBytes[i] & (0xF0 >>> shift)) | (value << shift));
    }
}
