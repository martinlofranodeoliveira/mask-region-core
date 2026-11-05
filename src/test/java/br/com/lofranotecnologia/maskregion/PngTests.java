package br.com.lofranotecnologia.maskregion;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import static br.com.lofranotecnologia.maskregion.TestSupport.*;
import static br.com.lofranotecnologia.maskregion.MaskException.Code;

final class PngTests {
    private PngTests() { }

    static void run() throws Exception {
        BufferedImage samples = new BufferedImage(3, 1, BufferedImage.TYPE_BYTE_GRAY);
        samples.getRaster().setSample(0, 0, 0, 127);
        samples.getRaster().setSample(1, 0, 0, 128);
        samples.getRaster().setSample(2, 0, 0, 255);
        byte[] png = encode(samples);
        same(new byte[] {0, 1, 1}, read(png, 128).toBytes(), "raw inclusive gray samples");
        same(new byte[] {1, 1, 1}, read(png, 0).toBytes(), "threshold zero");
        same(new byte[] {0, 0, 1}, read(png, 255).toBytes(), "threshold 255");

        error(Code.UnsupportedAlpha, () -> read(encode(new BufferedImage(1, 1,
                BufferedImage.TYPE_4BYTE_ABGR)), 128));
        error(Code.UnsupportedAlpha, () -> read(encode(new BufferedImage(1, 1,
                BufferedImage.TYPE_INT_ARGB)), 128));
        error(Code.UnsupportedAlpha, () -> read(encode(new BufferedImage(1, 1,
                BufferedImage.TYPE_INT_ARGB_PRE)), 128));

        for (int type : new int[] {BufferedImage.TYPE_BYTE_INDEXED,
                BufferedImage.TYPE_3BYTE_BGR, BufferedImage.TYPE_USHORT_GRAY,
                BufferedImage.TYPE_BYTE_BINARY, BufferedImage.TYPE_INT_RGB}) {
            error(Code.UnsupportedPng, () -> read(encode(new BufferedImage(1, 1, type)), 128));
        }

        error(Code.InvalidArgument, () -> PngAdapter.read(null, 128));
        error(Code.InvalidArgument, () -> PngAdapter.read(new ByteArrayInputStream(png), 128, null));

        InputStream mustNotRead = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("invalid threshold must precede reading");
            }
        };
        error(Code.InvalidArgument, () -> PngAdapter.read(mustNotRead, -1));
        error(Code.InvalidArgument, () -> PngAdapter.read(mustNotRead, 256));
        error(Code.CompressedLimit, () -> read(new byte[PngAdapter.MAX_COMPRESSED_BYTES + 1], 128));
        error(Code.InvalidPng, () -> read(new byte[0], 128));
        error(Code.InvalidPng, () -> read(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, 128));

        byte[] badSig = png.clone();
        badSig[0] = 0;
        error(Code.InvalidPng, () -> read(badSig, 128));

        error(Code.DimensionLimit, () -> read(dimensions(png, 4097, 1), 128));
        error(Code.PixelLimit, () -> read(dimensions(png, 4096, 4096), 128));
        error(Code.ScratchLimit, () -> PngAdapter.read(new ByteArrayInputStream(png), 128,
                new Limits(4096, 1_048_576, 10)));
        InputStream failingInput = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("synthetic private path must not escape");
            }
        };
        error(Code.IoError, () -> PngAdapter.read(failingInput, 128));
        InputStream zeroOnce = new ByteArrayInputStream(png) {
            private boolean first = true;

            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                if (first) {
                    first = false;
                    return 0;
                }
                return super.read(buffer, offset, length);
            }

            @Override
            public void close() {
                throw new AssertionError("adapter closed caller input");
            }
        };
        same(new byte[] {0, 1, 1}, PngAdapter.read(zeroOnce, 128).toBytes(), "zero-progress fallback");
        BitGrid ring = grid("111", "101", "111");

        error(Code.InvalidArgument, () -> PngAdapter.write(null, new ByteArrayOutputStream()));
        error(Code.InvalidArgument, () -> PngAdapter.write(ring, null));
        error(Code.InvalidArgument, () -> PngAdapter.write(ring, new ByteArrayOutputStream(), null));

        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override
            public void close() {
                throw new AssertionError("adapter closed caller output");
            }
        };
        PngAdapter.write(ring, output);
        check(read(output.toByteArray(), 128).equals(ring), "binary PNG round trip");
        OutputStream failingOutput = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("synthetic output failure");
            }
        };
        error(Code.IoError, () -> PngAdapter.write(ring, failingOutput));
        OutputStream failingFlush = new ByteArrayOutputStream() {
            @Override
            public void flush() throws IOException {
                throw new IOException("synthetic flush failure");
            }
        };
        error(Code.IoError, () -> PngAdapter.write(ring, failingFlush));
        check(ring.equals(grid("111", "101", "111")), "PNG failures preserve source");
        demo();
    }

    private static BitGrid read(byte[] png, int threshold) {
        return PngAdapter.read(new ByteArrayInputStream(png), threshold);
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("PNG").next();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static byte[] dimensions(byte[] png, int w, int h) {
        byte[] changed = png.clone();
        putInt(changed, 16, w);
        putInt(changed, 20, h);
        CRC32 crc = new CRC32();
        crc.update(changed, 12, 17);
        putInt(changed, 29, (int) crc.getValue());
        return changed;
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) {
            bytes[offset + i] = (byte) (value >>> (24 - 8 * i));
        }
    }

    private static void demo() throws Exception {
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(text, true, StandardCharsets.UTF_8)) {
            Demo.run(new String[0], output);
        }
        check(text.toString(StandardCharsets.UTF_8).strip().equals(
                "PASS components=1 filled=1 center_d2=4"), "demo human report");
        text.reset();
        try (PrintStream output = new PrintStream(text, true, StandardCharsets.UTF_8)) {
            Demo.run(new String[] {"--json"}, output);
        }
        check(text.toString(StandardCharsets.UTF_8).strip().equals(
                "{\"status\":\"PASS\",\"components\":1,\"filled\":1,\"center_d2\":4}"), "demo JSON report");
        error(Code.InvalidArgument, () -> Demo.run(new String[] {"--bad"}, System.out));
        try (PrintStream output = new PrintStream(new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("synthetic report failure");
            }
        })) {
            error(Code.IoError, () -> Demo.run(new String[0], output));
        }
    }
}
