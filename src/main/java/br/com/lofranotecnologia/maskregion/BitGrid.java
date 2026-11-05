package br.com.lofranotecnologia.maskregion;

import java.util.BitSet;
import static br.com.lofranotecnologia.maskregion.MaskException.Code;

/** Immutable, positive-sized, row-major binary grid. */
public final class BitGrid {
    private final int width;
    private final int height;
    private final BitSet bits;

    private BitGrid(int width, int height, BitSet ownedBits) {
        this.width = width;
        this.height = height;
        this.bits = ownedBits;
    }

    public static BitGrid fromBytes(int width, int height, byte[] pixels) {
        return fromBytes(width, height, pixels, Limits.DEFAULT);
    }

    public static BitGrid fromBytes(int width, int height, byte[] pixels, Limits limits) {
        Limits.require(limits);
        int count = limits.pixels(width, height);
        limits.scratch(Limits.packedBytes(count));
        Limits.require(pixels);
        if (pixels.length != count) {
            throw new MaskException(Code.InvalidData);
        }
        for (byte pixel : pixels) {
            if (pixel != 0 && pixel != 1) {
                throw new MaskException(Code.InvalidData);
            }
        }
        BitSet bits = new BitSet(count);
        for (int i = 0; i < count; i++) {
            if (pixels[i] == 1) {
                bits.set(i);
            }
        }
        return adopt(width, height, bits);
    }

    public static BitGrid fromBitSet(int width, int height, BitSet bits) {
        return fromBitSet(width, height, bits, Limits.DEFAULT);
    }

    public static BitGrid fromBitSet(int width, int height, BitSet bits, Limits limits) {
        Limits.require(limits);
        int count = limits.pixels(width, height);
        limits.scratch(Limits.packedBytes(count));
        Limits.require(bits);
        if (bits.nextSetBit(count) >= 0) {
            throw new MaskException(Code.InvalidData);
        }
        // Do not clone caller capacity: a sparse BitSet can have a huge backing array.
        BitSet copy = new BitSet(count);
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            copy.set(i);
        }
        return adopt(width, height, copy);
    }

    // Ownership transfer is package-private and used only with newly allocated buffers.
    static BitGrid adopt(int width, int height, BitSet ownedBits) {
        return new BitGrid(width, height, ownedBits);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int pixelCount() {
        return width * height;
    }

    public boolean get(int x, int y) {
        return bits.get(index(x, y));
    }

    int index(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new MaskException(Code.InvalidArgument);
        }
        return y * width + x;
    }

    boolean at(int index) {
        return bits.get(index);
    }

    public byte[] toBytes() {
        byte[] result = new byte[pixelCount()];
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            result[i] = 1;
        }
        return result;
    }

    public BitSet toBitSet() {
        return bits.get(0, pixelCount());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BitGrid grid && width == grid.width
                && height == grid.height && bits.equals(grid.bits);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + bits.hashCode();
    }
}
