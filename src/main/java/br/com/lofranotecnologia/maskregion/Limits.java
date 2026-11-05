package br.com.lofranotecnologia.maskregion;

import static br.com.lofranotecnologia.maskregion.MaskException.Code;

/** Configurable downward-only limits; scratch is a conservative logical-byte budget. */
public record Limits(int maxDimension, int maxPixels, long maxScratchBytes) {
    public static final Limits DEFAULT = new Limits(4096, 1_048_576, 64L * 1024 * 1024);

    public Limits {
        if (maxDimension < 1 || maxDimension > 4096 || maxPixels < 1
                || maxPixels > 1_048_576 || maxScratchBytes < 0
                || maxScratchBytes > 64L * 1024 * 1024) {
            throw new MaskException(Code.InvalidArgument);
        }
    }

    public int pixels(int width, int height) {
        if (width < 1 || height < 1) {
            throw new MaskException(Code.InvalidDimensions);
        }
        if (width > maxDimension || height > maxDimension) {
            throw new MaskException(Code.DimensionLimit);
        }
        long count = multiply(width, height);
        if (count > maxPixels) {
            throw new MaskException(Code.PixelLimit);
        }
        return (int) count;
    }

    public void scratch(long bytes) {
        if (bytes < 0 || bytes > maxScratchBytes) {
            throw new MaskException(Code.ScratchLimit);
        }
    }

    static long multiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException exception) {
            throw new MaskException(Code.ScratchLimit);
        }
    }

    static long add(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException exception) {
            throw new MaskException(Code.ScratchLimit);
        }
    }

    static long packedBytes(int pixels) {
        return multiply(8, add(pixels, 63) / 64);
    }

    static void require(Object object) {
        if (object == null) {
            throw new MaskException(Code.InvalidArgument);
        }
    }
}
