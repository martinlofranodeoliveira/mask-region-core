package br.com.lofranotecnologia.maskregion;

/** Self-contained asserting runner: a failed check or unexpected exception exits nonzero. */
public final class AllTests {
    private AllTests() { }

    public static void main(String[] args) throws Exception {
        CoreTests.run();
        System.out.println("PASS core: exhaustive 512 masks x 2 connectivities; BFS and brute-force oracles");
        PngTests.run();
        System.out.println("PASS PNG: raw samples, model rejection, limits, IO failures, synthetic demo");
        System.out.println("PASS all tests");
        System.out.flush();
        if (System.out.checkError()) {
            throw new AssertionError("test report IO failure");
        }
    }
}
