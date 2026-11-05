package br.com.lofranotecnologia.maskregion;

/** A stable error code with no input data, decoder diagnostics or host paths. */
public final class MaskException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Code {
        InvalidArgument, InvalidDimensions, DimensionLimit, PixelLimit,
        ScratchLimit, InvalidData, ArithmeticLimit, CompressedLimit,
        UnsupportedAlpha, UnsupportedPng, InvalidPng, IoError
    }

    private final Code code;

    public MaskException(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
