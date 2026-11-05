package br.com.lofranotecnologia.maskregion;

import java.util.Arrays;

final class TestSupport {
    private TestSupport() { }

    @FunctionalInterface
    interface CheckedAction {
        void run() throws Exception;
    }

    static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    static void same(byte[] expected, byte[] actual, String description) {
        check(Arrays.equals(expected, actual), description);
    }

    static void same(int[] expected, int[] actual, String description) {
        check(Arrays.equals(expected, actual), description);
    }

    static void same(long[] expected, long[] actual, String description) {
        check(Arrays.equals(expected, actual), description);
    }

    static void error(MaskException.Code expected, CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (MaskException exception) {
            check(exception.code() == expected, "wrong error: " + exception.code());
            check(exception.getMessage().equals(expected.name()), "diagnostic must be fixed");
            return;
        }
        throw new AssertionError("missing error: " + expected);
    }

    static BitGrid grid(String... rows) {
        check(rows.length > 0, "fixture height");
        int w = rows[0].length();
        byte[] data = new byte[w * rows.length];
        for (int y = 0; y < rows.length; y++) {
            check(rows[y].length() == w, "fixture width");
            for (int x = 0; x < w; x++) {
                char value = rows[y].charAt(x);
                check(value == '0' || value == '1', "fixture digit");
                data[y * w + x] = (byte) (value - '0');
            }
        }
        return BitGrid.fromBytes(w, rows.length, data);
    }
}
