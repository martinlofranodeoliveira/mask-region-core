package br.com.lofranotecnologia.maskregion;

import java.util.Arrays;
import java.util.BitSet;
import static br.com.lofranotecnologia.maskregion.MaskException.Code;

/** Clock-free, reentrant operations with no persistence or shared mutable state. */
public final class MaskOps {
    private MaskOps() { }

    public record Retention(BitGrid grid, int retainedComponents,
                            int removedComponents, int retainedPixels) { }
    public record HoleFill(BitGrid grid, int filledRegions, int filledPixels) { }

    private static int preflight(BitGrid grid, Limits limits, long bytesPerPixel,
                                 long fixedBytes) {
        Limits.require(grid);
        Limits.require(limits);
        int n = limits.pixels(grid.width(), grid.height());
        limits.scratch(Limits.add(Limits.multiply(bytesPerPixel, n), fixedBytes));
        return n;
    }

    public static Labeling labelComponents(BitGrid grid, Connectivity connectivity) {
        return labelComponents(grid, connectivity, Limits.DEFAULT);
    }

    public static Labeling labelComponents(BitGrid grid, Connectivity connectivity,
                                          Limits limits) {
        Limits.require(connectivity);
        int n = preflight(grid, limits, 40, 128);
        int w = grid.width();
        int h = grid.height();
        int[] labels = new int[n];
        int[] parent = new int[n + 1];
        byte[] rank = new byte[n + 1];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!grid.at(i)) {
                    continue;
                }
                int id = i + 1;
                labels[i] = id;
                parent[id] = id;
                if (x > 0) {
                    union(parent, rank, id, labels[i - 1]);
                }
                if (y > 0) {
                    union(parent, rank, id, labels[i - w]);
                    if (connectivity == Connectivity.EIGHT) {
                        if (x > 0) {
                            union(parent, rank, id, labels[i - w - 1]);
                        }
                        if (x + 1 < w) {
                            union(parent, rank, id, labels[i - w + 1]);
                        }
                    }
                }
            }
        }
        int[] rootLabel = new int[n + 1];
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (labels[i] != 0) {
                int root = root(parent, labels[i]);
                if (rootLabel[root] == 0) {
                    rootLabel[root] = ++count;
                }
                labels[i] = rootLabel[root];
            }
        }
        int[] stats = new int[(count + 1) * 5];
        for (int i = 0; i < n; i++) {
            int label = labels[i];
            if (label == 0) {
                continue;
            }
            int s = label * 5;
            int x = i % w;
            int y = i / w;
            if (stats[s] == 0) {
                stats[s + 1] = x;
                stats[s + 2] = y;
                stats[s + 3] = x + 1;
                stats[s + 4] = y + 1;
            } else {
                stats[s + 1] = Math.min(stats[s + 1], x);
                stats[s + 2] = Math.min(stats[s + 2], y);
                stats[s + 3] = Math.max(stats[s + 3], x + 1);
                stats[s + 4] = Math.max(stats[s + 4], y + 1);
            }
            stats[s]++;
        }
        return new Labeling(w, h, count, labels, stats);
    }

    private static int root(int[] parent, int id) {
        int current = id;
        while (parent[current] != current) {
            current = parent[current];
        }
        while (id != current) {
            int next = parent[id];
            parent[id] = current;
            id = next;
        }
        return current;
    }

    private static void union(int[] parent, byte[] rank, int a, int b) {
        if (b == 0) {
            return;
        }
        int ra = root(parent, a);
        int rb = root(parent, b);
        if (ra == rb) {
            return;
        }
        if (rank[ra] < rank[rb]) {
            parent[ra] = rb;
        } else {
            parent[rb] = ra;
            if (rank[ra] == rank[rb]) {
                rank[ra]++;
            }
        }
    }

    public static Retention retainByArea(BitGrid grid, Connectivity connectivity,
                                         int minArea) {
        return retainByArea(grid, connectivity, minArea, Limits.DEFAULT);
    }

    public static Retention retainByArea(BitGrid grid, Connectivity connectivity,
                                         int minArea, Limits limits) {
        Limits.require(connectivity);
        if (minArea < 0) {
            throw new MaskException(Code.InvalidArgument);
        }
        int n = preflight(grid, limits, 48, 512);
        Labeling labeling = labelComponents(grid, connectivity, limits);
        BitSet kept = new BitSet(n);
        int components = 0;
        int pixels = 0;
        for (int label = 1; label <= labeling.componentCount(); label++) {
            if (labeling.area(label) >= minArea) {
                components++;
                pixels += labeling.area(label);
            }
        }
        for (int i = 0; i < n; i++) {
            int label = labeling.at(i);
            if (label != 0 && labeling.area(label) >= minArea) {
                kept.set(i);
            }
        }
        return new Retention(BitGrid.adopt(grid.width(), grid.height(), kept),
                components, labeling.componentCount() - components, pixels);
    }

    public static HoleFill fillHoles(BitGrid grid, Connectivity foreground,
                                     int maxHoleArea) {
        return fillHoles(grid, foreground, maxHoleArea, Limits.DEFAULT);
    }

    public static HoleFill fillHoles(BitGrid grid, Connectivity foreground,
                                     int maxHoleArea, Limits limits) {
        Limits.require(foreground);
        if (maxHoleArea < 0) {
            throw new MaskException(Code.InvalidArgument);
        }
        int n = preflight(grid, limits, 48, 512);
        int w = grid.width();
        int h = grid.height();
        BitSet background = new BitSet(n);
        for (int i = 0; i < n; i++) {
            if (!grid.at(i)) {
                background.set(i);
            }
        }
        Labeling regions = labelComponents(BitGrid.adopt(w, h, background),
                foreground.dual(), limits);
        boolean[] exterior = new boolean[regions.componentCount() + 1];
        for (int x = 0; x < w; x++) {
            exterior[regions.at(x)] = true;
            exterior[regions.at((h - 1) * w + x)] = true;
        }
        for (int y = 0; y < h; y++) {
            exterior[regions.at(y * w)] = true;
            exterior[regions.at(y * w + w - 1)] = true;
        }
        boolean[] fill = new boolean[regions.componentCount() + 1];
        int filledRegions = 0;
        int filledPixels = 0;
        for (int label = 1; label <= regions.componentCount(); label++) {
            if (!exterior[label] && regions.area(label) <= maxHoleArea) {
                fill[label] = true;
                filledRegions++;
                filledPixels += regions.area(label);
            }
        }
        BitSet output = grid.toBitSet();
        for (int i = 0; i < n; i++) {
            if (fill[regions.at(i)]) {
                output.set(i);
            }
        }
        return new HoleFill(BitGrid.adopt(w, h, output), filledRegions, filledPixels);
    }

    public static DistanceGrid squaredDistanceToBackground(BitGrid grid) {
        return squaredDistanceToBackground(grid, Limits.DEFAULT);
    }

    public static DistanceGrid squaredDistanceToBackground(BitGrid grid, Limits limits) {
        Limits.require(grid);
        Limits.require(limits);
        int n = limits.pixels(grid.width(), grid.height());
        int pw = grid.width() + 2;
        int ph = grid.height() + 2;
        long padded = Limits.multiply(pw, ph);
        int span = Math.max(pw, ph);
        limits.scratch(Limits.add(Limits.add(Limits.multiply(16, padded),
                Limits.multiply(32, span)), 512));
        long[] field = new long[(int) padded];
        Arrays.fill(field, Long.MAX_VALUE);
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                if (x == 0 || y == 0 || x == pw - 1 || y == ph - 1
                        || !grid.get(x - 1, y - 1)) {
                    field[y * pw + x] = 0;
                }
            }
        }
        long[] f = new long[span];
        long[] out = new long[span];
        int[] sites = new int[span];
        long[] starts = new long[span];
        for (int y = 0; y < ph; y++) {
            System.arraycopy(field, y * pw, f, 0, pw);
            envelope(f, out, sites, starts, pw);
            System.arraycopy(out, 0, field, y * pw, pw);
        }
        for (int x = 0; x < pw; x++) {
            for (int y = 0; y < ph; y++) {
                f[y] = field[y * pw + x];
            }
            envelope(f, out, sites, starts, ph);
            for (int y = 0; y < ph; y++) {
                field[y * pw + x] = out[y];
            }
        }
        long[] result = new long[n];
        for (int y = 0; y < grid.height(); y++) {
            System.arraycopy(field, (y + 1) * pw + 1, result,
                    y * grid.width(), grid.width());
        }
        return new DistanceGrid(grid.width(), grid.height(), result);
    }

    /* Integer lower envelope of f[q] + (x-q)^2. Earlier sites win exact ties.
       starts[k] is the first integer x where site k beats its predecessor. */
    private static void envelope(long[] f, long[] out, int[] sites,
                                 long[] starts, int length) {
        try {
            int top = -1;
            for (int q = 0; q < length; q++) {
                if (f[q] == Long.MAX_VALUE) {
                    continue;
                }
                long first = Long.MIN_VALUE;
                while (top >= 0) {
                    int r = sites[top];
                    long qTerm = Math.addExact(f[q], Math.multiplyExact((long) q, q));
                    long rTerm = Math.addExact(f[r], Math.multiplyExact((long) r, r));
                    long numerator = Math.subtractExact(qTerm, rTerm);
                    long denominator = Math.multiplyExact(2L, q - r);
                    first = Math.addExact(Math.floorDiv(numerator, denominator), 1);
                    if (first > starts[top]) {
                        break;
                    }
                    top--;
                }
                if (top < 0) {
                    first = Long.MIN_VALUE;
                }
                sites[++top] = q;
                starts[top] = first;
            }
            if (top < 0) {
                Arrays.fill(out, 0, length, Long.MAX_VALUE);
                return;
            }
            int active = 0;
            for (int x = 0; x < length; x++) {
                while (active < top && starts[active + 1] <= x) {
                    active++;
                }
                long delta = (long) x - sites[active];
                out[x] = Math.addExact(f[sites[active]], Math.multiplyExact(delta, delta));
            }
        } catch (ArithmeticException exception) {
            throw new MaskException(Code.ArithmeticLimit);
        }
    }
}
