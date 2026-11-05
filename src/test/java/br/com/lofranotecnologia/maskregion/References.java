package br.com.lofranotecnologia.maskregion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Independent test oracles: no union-find, production labels, or distance envelopes. */
final class References {
    private References() { }

    record Region(int area, int minX, int minY, int maxX, int maxY, boolean border) { }
    record Regions(int[] labels, List<Region> regions) { }
    record Filled(byte[] pixels, int regions, int count) { }

    static Regions bfs(byte[] data, int w, int h, int neighbors, byte target) {
        int[] labels = new int[data.length];
        List<Region> regions = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < data.length; start++) {
            if (data[start] != target || labels[start] != 0) {
                continue;
            }
            int label = regions.size() + 1;
            labels[start] = label;
            queue.add(start);
            int area = 0;
            int minX = w;
            int minY = h;
            int maxX = 0;
            int maxY = 0;
            boolean border = false;
            while (!queue.isEmpty()) {
                int p = queue.remove();
                int x = p % w;
                int y = p / w;
                area++;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + 1);
                maxY = Math.max(maxY, y + 1);
                border |= x == 0 || y == 0 || x == w - 1 || y == h - 1;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int steps = Math.abs(dx) + Math.abs(dy);
                        if (steps == 0 || (neighbors == 4 && steps != 1)) {
                            continue;
                        }
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                            continue;
                        }
                        int next = ny * w + nx;
                        if (data[next] == target && labels[next] == 0) {
                            labels[next] = label;
                            queue.add(next);
                        }
                    }
                }
            }
            regions.add(new Region(area, minX, minY, maxX, maxY, border));
        }
        return new Regions(labels, List.copyOf(regions));
    }

    static Filled holes(byte[] data, int w, int h, int foregroundNeighbors, int maxArea) {
        Regions background = bfs(data, w, h, foregroundNeighbors == 4 ? 8 : 4, (byte) 0);
        byte[] filled = data.clone();
        int count = 0;
        int regions = 0;
        for (int r = 0; r < background.regions().size(); r++) {
            Region region = background.regions().get(r);
            if (region.border() || region.area() > maxArea) {
                continue;
            }
            regions++;
            for (int i = 0; i < data.length; i++) {
                if (background.labels()[i] == r + 1) {
                    filled[i] = 1;
                    count++;
                }
            }
        }
        return new Filled(filled, regions, count);
    }

    static long[] distances(byte[] data, int w, int h) {
        long[] result = new long[data.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                long best = Long.MAX_VALUE;
                for (int by = -1; by <= h; by++) {
                    for (int bx = -1; bx <= w; bx++) {
                        boolean frame = bx == -1 || by == -1 || bx == w || by == h;
                        if (frame || data[by * w + bx] == 0) {
                            long dx = (long) x - bx;
                            long dy = (long) y - by;
                            best = Math.min(best, dx * dx + dy * dy);
                        }
                    }
                }
                result[y * w + x] = best;
            }
        }
        return result;
    }
}
