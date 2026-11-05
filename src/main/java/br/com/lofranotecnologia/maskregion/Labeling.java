package br.com.lofranotecnologia.maskregion;

import static br.com.lofranotecnologia.maskregion.MaskException.Code;

/** Immutable labels and component statistics. Zero labels denote background. */
public final class Labeling {
    public record Component(int label, int area, int minX, int minY,
                            int maxXExclusive, int maxYExclusive) { }

    private final int width;
    private final int height;
    private final int count;
    private final int[] labels;
    private final int[] stats;

    Labeling(int width, int height, int count, int[] ownedLabels, int[] ownedStats) {
        this.width = width;
        this.height = height;
        this.count = count;
        this.labels = ownedLabels;
        this.stats = ownedStats;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int componentCount() {
        return count;
    }

    public int labelAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new MaskException(Code.InvalidArgument);
        }
        return labels[y * width + x];
    }

    int at(int index) {
        return labels[index];
    }

    public int[] labels() {
        return labels.clone();
    }

    public Component component(int label) {
        if (label < 1 || label > count) {
            throw new MaskException(Code.InvalidArgument);
        }
        int i = label * 5;
        return new Component(label, stats[i], stats[i + 1], stats[i + 2],
                stats[i + 3], stats[i + 4]);
    }

    int area(int label) {
        return stats[label * 5];
    }
}
