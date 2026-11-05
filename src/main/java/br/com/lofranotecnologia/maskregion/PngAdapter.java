package br.com.lofranotecnologia.maskregion;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.BitSet;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import static br.com.lofranotecnologia.maskregion.MaskException.Code;

/** PNG-only adapter. Caller streams are not closed. This is not a hostile-image sandbox. */
public final class PngAdapter {
    public static final int MAX_COMPRESSED_BYTES = 4 * 1024 * 1024;
    private static final byte[] SIGNATURE = {
        (byte) 137, 80, 78, 71, 13, 10, 26, 10
    };

    private PngAdapter() { }

    public static BitGrid read(InputStream input, int threshold) {
        return read(input, threshold, Limits.DEFAULT);
    }

    public static BitGrid read(InputStream input, int threshold, Limits limits) {
        Limits.require(input);
        Limits.require(limits);
        if (threshold < 0 || threshold > 255) {
            throw new MaskException(Code.InvalidArgument);
        }
        limits.scratch(2L * (MAX_COMPRESSED_BYTES + 1));
        byte[] compressed = new byte[MAX_COMPRESSED_BYTES + 1];
        int length = readBounded(input, compressed);
        if (length < SIGNATURE.length) {
            throw new MaskException(Code.InvalidPng);
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (compressed[i] != SIGNATURE[i]) {
                throw new MaskException(Code.InvalidPng);
            }
        }
        try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(compressed, 0, length))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new MaskException(Code.UnsupportedPng);
            }
            ImageReader reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) {
                    throw new MaskException(Code.UnsupportedPng);
                }
                reader.setInput(stream, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                int n = limits.pixels(w, h);
                pngScratch(limits, n);
                ImageTypeSpecifier raw = reader.getRawImageType(0);
                if (raw == null) {
                    throw new MaskException(Code.UnsupportedPng);
                }
                checkModel(raw.getColorModel(), raw.getSampleModel());
                BufferedImage image = reader.read(0, reader.getDefaultReadParam());
                if (image == null || image.getWidth() != w || image.getHeight() != h) {
                    throw new MaskException(Code.InvalidPng);
                }
                checkModel(image.getColorModel(), image.getSampleModel());
                BitSet pixels = new BitSet(n);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (image.getRaster().getSample(x, y, 0) >= threshold) {
                            pixels.set(y * w + x);
                        }
                    }
                }
                return BitGrid.adopt(w, h, pixels);
            } finally {
                reader.dispose();
            }
        } catch (MaskException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new MaskException(Code.InvalidPng);
        }
    }

    private static int readBounded(InputStream input, byte[] buffer) {
        int used = 0;
        try {
            while (used < buffer.length) {
                int count = input.read(buffer, used, buffer.length - used);
                if (count < 0) {
                    return used;
                }
                if (count == 0) {
                    int value = input.read();
                    if (value < 0) {
                        return used;
                    }
                    buffer[used++] = (byte) value;
                } else {
                    used += count;
                }
            }
        } catch (IOException exception) {
            throw new MaskException(Code.IoError);
        }
        throw new MaskException(Code.CompressedLimit);
    }

    private static void pngScratch(Limits limits, int n) {
        limits.scratch(Limits.add(2L * (MAX_COMPRESSED_BYTES + 1) + 4096,
                Limits.multiply(4, n)));
    }

    private static void checkModel(ColorModel color, SampleModel samples) {
        if (color == null || samples == null) {
            throw new MaskException(Code.UnsupportedPng);
        }
        if (color.hasAlpha()) {
            throw new MaskException(Code.UnsupportedAlpha);
        }
        if (!(color instanceof ComponentColorModel)
                || color.getTransparency() != Transparency.OPAQUE
                || color.getColorSpace().getType() != ColorSpace.TYPE_GRAY
                || color.getNumColorComponents() != 1 || color.getNumComponents() != 1
                || color.getComponentSize(0) != 8
                || samples.getNumBands() != 1 || samples.getSampleSize(0) != 8
                || samples.getDataType() != DataBuffer.TYPE_BYTE) {
            throw new MaskException(Code.UnsupportedPng);
        }
    }

    public static void write(BitGrid grid, OutputStream output) {
        write(grid, output, Limits.DEFAULT);
    }

    public static void write(BitGrid grid, OutputStream output, Limits limits) {
        Limits.require(grid);
        Limits.require(output);
        Limits.require(limits);
        int n = limits.pixels(grid.width(), grid.height());
        pngScratch(limits, n);
        BufferedImage image = new BufferedImage(grid.width(), grid.height(),
                BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                image.getRaster().setSample(x, y, 0, grid.get(x, y) ? 255 : 0);
            }
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("PNG");
        if (!writers.hasNext()) {
            throw new MaskException(Code.UnsupportedPng);
        }
        ImageWriter writer = writers.next();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
            stream.flush();
            output.flush();
        } catch (IOException | RuntimeException exception) {
            throw new MaskException(Code.IoError);
        } finally {
            writer.dispose();
        }
    }
}
