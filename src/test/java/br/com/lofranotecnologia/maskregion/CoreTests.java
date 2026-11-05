package br.com.lofranotecnologia.maskregion;

import java.util.Arrays;
import java.util.BitSet;
import java.util.SplittableRandom;
import static br.com.lofranotecnologia.maskregion.TestSupport.*;
import static br.com.lofranotecnologia.maskregion.MaskException.Code;

final class CoreTests {
    private CoreTests() { }

    static void run() throws Exception {
        acceptance();
        exhaustive();
        skinnyAndTies();
        ownershipAndFailures();
    }

    private static void acceptance() {
        BitGrid diagonal = grid("100", "010", "001");
        Labeling four = MaskOps.labelComponents(diagonal, Connectivity.FOUR);
        Labeling eight = MaskOps.labelComponents(diagonal, Connectivity.EIGHT);
        check(four.componentCount() == 3, "diagonal four count");
        for (int i = 1; i <= 3; i++) {
            check(four.component(i).area() == 1, "diagonal singleton");
        }
        check(eight.componentCount() == 1 && eight.component(1).area() == 3,
                "diagonal eight area");
        BitGrid ring = grid("111", "101", "111");
        MaskOps.HoleFill noFill = MaskOps.fillHoles(ring, Connectivity.FOUR, 0);
        MaskOps.HoleFill fill = MaskOps.fillHoles(ring, Connectivity.FOUR, 1);
        check(noFill.grid().equals(ring) && noFill.filledPixels() == 0, "zero hole limit");
        check(fill.filledPixels() == 1 && fill.filledRegions() == 1, "ring hole");
        same(grid("111", "111", "111").toBytes(), fill.grid().toBytes(), "solid ring");
        same(new long[] {1, 1, 1, 1, 4, 1, 1, 1, 1},
                MaskOps.squaredDistanceToBackground(fill.grid()).values(), "virtual frame");
        MaskOps.Retention retained = MaskOps.retainByArea(grid("110111"), Connectivity.FOUR, 3);
        check(retained.grid().equals(grid("000111")), "inclusive area threshold");
        check(retained.retainedComponents() == 1 && retained.removedComponents() == 1
                && retained.retainedPixels() == 3, "retention report");
        BitGrid cornerLeak = grid("011", "101", "111");
        check(MaskOps.fillHoles(cornerLeak, Connectivity.FOUR, 1).filledPixels() == 0,
                "background eight diagonal escape");
        check(MaskOps.fillHoles(cornerLeak, Connectivity.EIGHT, 1).filledPixels() == 1,
                "background four diagonal enclosure");
    }

    private static void exhaustive() {
        for (int mask = 0; mask < 512; mask++) {
            byte[] data = new byte[9];
            for (int i = 0; i < 9; i++) {
                data[i] = (byte) ((mask >>> i) & 1);
            }
            BitGrid source = BitGrid.fromBytes(3, 3, data);
            for (Connectivity connectivity : Connectivity.values()) {
                int neighbors = connectivity == Connectivity.FOUR ? 4 : 8;
                References.Regions expected = References.bfs(data, 3, 3, neighbors, (byte) 1);
                compareLabels(expected, MaskOps.labelComponents(source, connectivity));
                for (int threshold = 0; threshold <= 10; threshold++) {
                    MaskOps.Retention kept = MaskOps.retainByArea(source, connectivity, threshold);
                    byte[] expectedPixels = new byte[9];
                    int count = 0;
                    int pixels = 0;
                    for (int r = 0; r < expected.regions().size(); r++) {
                        if (expected.regions().get(r).area() >= threshold) {
                            count++;
                            pixels += expected.regions().get(r).area();
                            for (int i = 0; i < 9; i++) {
                                if (expected.labels()[i] == r + 1) {
                                    expectedPixels[i] = 1;
                                }
                            }
                        }
                    }
                    same(expectedPixels, kept.grid().toBytes(), "exhaustive retention pixels");
                    check(kept.retainedComponents() == count && kept.retainedPixels() == pixels
                            && kept.removedComponents() == expected.regions().size() - count,
                            "exhaustive retention counts");
                    References.Filled holes = References.holes(data, 3, 3, neighbors, threshold);
                    MaskOps.HoleFill filled = MaskOps.fillHoles(source, connectivity, threshold);
                    same(holes.pixels(), filled.grid().toBytes(), "exhaustive dual holes");
                    check(holes.count() == filled.filledPixels()
                            && holes.regions() == filled.filledRegions(), "exhaustive hole counts");
                }
                same(References.distances(data, 3, 3),
                        MaskOps.squaredDistanceToBackground(source).values(), "exhaustive distance");
                same(data, source.toBytes(), "exhaustive source unchanged");
            }
        }
    }

    private static void compareLabels(References.Regions expected, Labeling actual) {
        same(expected.labels(), actual.labels(), "row-major labels versus BFS");
        check(expected.regions().size() == actual.componentCount(), "component count");
        for (int i = 1; i <= actual.componentCount(); i++) {
            References.Region r = expected.regions().get(i - 1);
            Labeling.Component c = actual.component(i);
            check(c.label() == i && c.area() == r.area() && c.minX() == r.minX()
                    && c.minY() == r.minY() && c.maxXExclusive() == r.maxX()
                    && c.maxYExclusive() == r.maxY(), "half-open bounds and area");
        }
    }

    private static void skinnyAndTies() {
        SplittableRandom random = new SplittableRandom(20260101L);
        int[][] shapes = {{1, 1}, {1, 17}, {17, 1}, {2, 19}, {19, 2}, {7, 9}, {13, 11}};
        for (int[] shape : shapes) {
            int w = shape[0];
            int h = shape[1];
            for (int trial = 0; trial < 24; trial++) {
                byte[] data = new byte[w * h];
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) (trial == 0 ? 0 : trial == 1 ? 1 : random.nextInt(2));
                }
                BitGrid source = BitGrid.fromBytes(w, h, data);
                same(References.distances(data, w, h),
                        MaskOps.squaredDistanceToBackground(source).values(), "skinny/tie distances");
                for (Connectivity c : Connectivity.values()) {
                    int neighbors = c == Connectivity.FOUR ? 4 : 8;
                    compareLabels(References.bfs(data, w, h, neighbors, (byte) 1),
                            MaskOps.labelComponents(source, c));
                    References.Filled expected = References.holes(data, w, h, neighbors, 7);
                    MaskOps.HoleFill actual = MaskOps.fillHoles(source, c, 7);
                    same(expected.pixels(), actual.grid().toBytes(), "larger hole flood");
                    check(expected.count() == actual.filledPixels(), "larger hole count");
                }
            }
        }
        BitGrid metric = grid("1111111", "1111111", "1101111", "1111111", "1111111", "1111111", "1111111");
        check(MaskOps.squaredDistanceToBackground(metric).get(3, 3) == 2,
                "Euclidean diagonal is two, not Manhattan squared four");
        same(References.distances(metric.toBytes(), 7, 7),
                MaskOps.squaredDistanceToBackground(metric).values(), "internal and frame ties");
        byte[] large = new byte[257 * 259];
        Arrays.fill(large, (byte) 1);
        DistanceGrid solid = MaskOps.squaredDistanceToBackground(BitGrid.fromBytes(257, 259, large));
        check(solid.get(128, 129) == 129L * 129, "larger exact center");
    }

    private static void ownershipAndFailures() throws Exception {
        byte[] bytes = {1, 0, 1};
        BitGrid source = BitGrid.fromBytes(3, 1, bytes);
        bytes[0] = 0;
        byte[] exported = source.toBytes();
        exported[0] = 0;
        BitSet hugeCapacity = new BitSet(8_000_000);
        hugeCapacity.set(0);
        BitGrid detached = BitGrid.fromBitSet(1, 1, hugeCapacity);
        hugeCapacity.clear();
        check(detached.get(0, 0) && detached.toBitSet().size() <= 64, "bounded BitSet copy");
        BitSet returned = source.toBitSet();
        returned.clear();
        check(source.get(0, 0), "detached BitSet accessor");
        Labeling labels = MaskOps.labelComponents(source, Connectivity.FOUR);
        labels.labels()[0] = 99;
        check(labels.labelAt(0, 0) == 1, "detached labels");
        DistanceGrid distances = MaskOps.squaredDistanceToBackground(source);
        distances.values()[0] = 999;
        check(distances.get(0, 0) == 1, "detached distances");

        check(!source.equals(null), "equality with null");
        check(!source.equals("other"), "equality with non-grid");
        check(source.equals(source), "reflexive equality");

        error(Code.InvalidArgument, () -> source.get(-1, 0));
        error(Code.InvalidArgument, () -> source.get(0, -1));
        error(Code.InvalidArgument, () -> source.get(3, 0));
        error(Code.InvalidArgument, () -> source.get(0, 1));

        error(Code.InvalidArgument, () -> distances.get(-1, 0));
        error(Code.InvalidArgument, () -> distances.get(0, -1));
        error(Code.InvalidArgument, () -> distances.get(3, 0));
        error(Code.InvalidArgument, () -> distances.get(0, 1));

        error(Code.InvalidArgument, () -> labels.labelAt(-1, 0));
        error(Code.InvalidArgument, () -> labels.labelAt(0, -1));
        error(Code.InvalidArgument, () -> labels.labelAt(3, 0));
        error(Code.InvalidArgument, () -> labels.labelAt(0, 1));
        error(Code.InvalidArgument, () -> labels.component(-1));
        error(Code.InvalidArgument, () -> labels.component(0));
        error(Code.InvalidArgument, () -> labels.component(labels.componentCount() + 1));

        byte[] emptyData = new byte[25];
        BitGrid emptyGrid = BitGrid.fromBytes(5, 5, emptyData);
        Labeling emptyLabels = MaskOps.labelComponents(emptyGrid, Connectivity.FOUR);
        check(emptyLabels.componentCount() == 0, "empty grid has zero components");
        MaskOps.Retention emptyRetain = MaskOps.retainByArea(emptyGrid, Connectivity.FOUR, 0);
        check(emptyRetain.retainedComponents() == 0 && emptyRetain.removedComponents() == 0
                && emptyRetain.retainedPixels() == 0, "empty grid retention counts");
        MaskOps.HoleFill emptyHoles = MaskOps.fillHoles(emptyGrid, Connectivity.FOUR, 100);
        check(emptyHoles.filledRegions() == 0 && emptyHoles.filledPixels() == 0
                && emptyHoles.grid().equals(emptyGrid), "empty grid hole filling fills nothing");
        DistanceGrid emptyDist = MaskOps.squaredDistanceToBackground(emptyGrid);
        same(new long[25], emptyDist.values(), "empty grid has all zero distances");

        BitGrid solid1x1 = BitGrid.fromBytes(1, 1, new byte[] {1});
        Labeling solid1x1Labels = MaskOps.labelComponents(solid1x1, Connectivity.FOUR);
        check(solid1x1Labels.componentCount() == 1, "solid 1x1 has one component");
        check(solid1x1Labels.component(1).area() == 1, "solid 1x1 component area is 1");
        check(solid1x1Labels.component(1).minX() == 0 && solid1x1Labels.component(1).minY() == 0
                && solid1x1Labels.component(1).maxXExclusive() == 1
                && solid1x1Labels.component(1).maxYExclusive() == 1, "solid 1x1 component bounds");
        check(MaskOps.squaredDistanceToBackground(solid1x1).get(0, 0) == 1,
                "solid 1x1 has distance 1 to exterior frame");

        MaskOps.Retention retainAll = MaskOps.retainByArea(source, Connectivity.FOUR, 0);
        check(retainAll.retainedComponents() == 2 && retainAll.retainedPixels() == 2,
                "minArea 0 retains all components");
        MaskOps.Retention retainNone = MaskOps.retainByArea(source, Connectivity.FOUR, Integer.MAX_VALUE);
        check(retainNone.retainedComponents() == 0 && retainNone.removedComponents() == 2,
                "minArea MAX_VALUE retains no components");

        error(Code.DimensionLimit, () -> BitGrid.fromBytes(4097, 1, null));
        error(Code.DimensionLimit, () -> BitGrid.fromBytes(Integer.MAX_VALUE, Integer.MAX_VALUE, null));
        error(Code.InvalidDimensions, () -> BitGrid.fromBytes(0, 1, null));
        error(Code.InvalidDimensions, () -> BitGrid.fromBytes(-1, 1, null));
        error(Code.PixelLimit, () -> BitGrid.fromBytes(4096, 4096, null));
        error(Code.InvalidData, () -> BitGrid.fromBytes(1, 1, new byte[] {2}));
        error(Code.InvalidData, () -> BitGrid.fromBytes(1, 1, new byte[2]));
        BitSet outOfRange = new BitSet();
        outOfRange.set(1);
        error(Code.InvalidData, () -> BitGrid.fromBitSet(1, 1, outOfRange));

        error(Code.InvalidArgument, () -> new Limits(0, 100, 100));
        error(Code.InvalidArgument, () -> new Limits(4097, 1, 0));
        error(Code.InvalidArgument, () -> new Limits(100, 0, 100));
        error(Code.InvalidArgument, () -> new Limits(100, 1_048_577, 100));
        error(Code.InvalidArgument, () -> new Limits(100, 100, -1));
        error(Code.InvalidArgument, () -> new Limits(1, 1, Long.MAX_VALUE));
        error(Code.InvalidArgument, () -> new Limits(100, 100, 64L * 1024 * 1024 + 1));
        error(Code.InvalidArgument, () -> Limits.require(null));

        error(Code.ScratchLimit, () -> Limits.multiply(Long.MAX_VALUE, 2));
        error(Code.ScratchLimit, () -> Limits.add(Long.MAX_VALUE, 1));
        error(Code.ScratchLimit, () -> BitGrid.fromBytes(1, 1, new byte[] {0}, new Limits(1, 1, 0)));
        Limits tiny = new Limits(4096, 1_048_576, 1);
        error(Code.ScratchLimit, () -> MaskOps.labelComponents(source, Connectivity.FOUR, tiny));
        error(Code.ScratchLimit, () -> MaskOps.retainByArea(source, Connectivity.FOUR, 1, tiny));
        error(Code.ScratchLimit, () -> MaskOps.fillHoles(source, Connectivity.FOUR, 1, tiny));
        error(Code.ScratchLimit, () -> MaskOps.squaredDistanceToBackground(source, tiny));
        error(Code.DimensionLimit, () -> MaskOps.labelComponents(source, Connectivity.FOUR, new Limits(2, 4, 1024)));
        error(Code.PixelLimit, () -> MaskOps.squaredDistanceToBackground(source, new Limits(3, 2, 1024)));
        error(Code.InvalidArgument, () -> MaskOps.fillHoles(source, Connectivity.FOUR, -1));
        error(Code.InvalidArgument, () -> MaskOps.retainByArea(source, Connectivity.FOUR, -1));
        error(Code.InvalidArgument, () -> MaskOps.labelComponents(source, null));
        same(new byte[] {1, 0, 1}, source.toBytes(), "source survives every failed operation");
        check(source.equals(BitGrid.fromBytes(3, 1, new byte[] {1, 0, 1})), "value equality");
        check(source.hashCode() == BitGrid.fromBytes(3, 1, new byte[] {1, 0, 1}).hashCode(), "value hash");
    }
}
