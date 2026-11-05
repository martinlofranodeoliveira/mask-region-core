package br.com.lofranotecnologia.maskregion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static br.com.lofranotecnologia.maskregion.MaskException.Code;

/** Synthetic, offline numerical demonstration; no user-supplied image paths. */
public final class Demo {
    private Demo() { }

    public static void main(String[] args) {
        try {
            run(args, System.out);
        } catch (MaskException exception) {
            System.err.println("ERROR " + exception.code().name());
            System.err.flush();
            System.exit(1);
        } catch (IOException | SecurityException exception) {
            System.err.println("ERROR IoError");
            System.err.flush();
            System.exit(1);
        }
    }

    public static void run(String[] args, PrintStream output) throws IOException {
        Limits.require(args);
        Limits.require(output);
        boolean json = args.length == 1 && "--json".equals(args[0]);
        if (args.length != 0 && !json) {
            throw new MaskException(Code.InvalidArgument);
        }
        Path directory = Files.createTempDirectory("mask-region-synthetic-");
        Path ringPath = directory.resolve("ring.png");
        Path filledPath = directory.resolve("filled.png");
        try {
            BitGrid ring = BitGrid.fromBytes(3, 3, new byte[] {
                1, 1, 1, 1, 0, 1, 1, 1, 1
            });
            write(ring, ringPath);
            BitGrid reread = read(ringPath);
            require(ring.equals(reread));
            MaskOps.HoleFill fill = MaskOps.fillHoles(reread, Connectivity.FOUR, 1);
            require(fill.filledPixels() == 1 && fill.filledRegions() == 1);
            write(fill.grid(), filledPath);
            BitGrid solid = read(filledPath);
            require(solid.equals(fill.grid()));
            int count = MaskOps.labelComponents(solid, Connectivity.FOUR).componentCount();
            long center = MaskOps.squaredDistanceToBackground(solid).get(1, 1);
            require(count == 1 && center == 4);
        } finally {
            // Only these known synthetic files are removed; never walk a directory tree.
            try {
                Files.deleteIfExists(ringPath);
            } finally {
                try {
                    Files.deleteIfExists(filledPath);
                } finally {
                    Files.deleteIfExists(directory);
                }
            }
        }
        output.println(json
                ? "{\"status\":\"PASS\",\"components\":1,\"filled\":1,\"center_d2\":4}"
                : "PASS components=1 filled=1 center_d2=4");
        output.flush();
        if (output.checkError()) {
            throw new MaskException(Code.IoError);
        }
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new MaskException(Code.InvalidData);
        }
    }

    private static void write(BitGrid grid, Path path) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            PngAdapter.write(grid, output);
        }
    }

    private static BitGrid read(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return PngAdapter.read(input, 128);
        }
    }
}
