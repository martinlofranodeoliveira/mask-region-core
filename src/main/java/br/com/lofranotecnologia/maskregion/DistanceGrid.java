package br.com.lofranotecnologia.maskregion;

import static br.com.lofranotecnologia.maskregion.MaskException.Code;

/** Immutable exact squared pixel-center distances, including zeros at background. */
public final class DistanceGrid {
    private final int width;
    private final int height;
    private final long[] values;

    DistanceGrid(int width, int height, long[] ownedValues) {
        this.width = width;
        this.height = height;
        this.values = ownedValues;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long get(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new MaskException(Code.InvalidArgument);
        }
        return values[y * width + x];
    }

    public long[] values() {
        return values.clone();
    }
}
