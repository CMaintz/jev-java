package io.github.cmaintz.jev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/**
 * A PNG or JPEG image to send with a request. Put it anywhere text can go: as the state,
 * as a value inside the state, as a question's instructions, as a criteria value, or
 * inside longer text with {@link TextWithImages}. The client lists each image once and
 * sends it as a file part, writing its {@link #reference()} wherever it was placed.
 *
 * <p>Images are experimental on TypeSafe's side ("dreamer-ready"). The API rejects images
 * outside its limits rather than resizing them: PNG or still JPEG, 280 to 1568 pixels per
 * side, at most 16 MiB per file and 32 MiB per request. Rotate pixels upright first, since
 * EXIF orientation is dropped. See https://docs.typesafe.ai/images.
 *
 * <p>Equality is identity: two images with the same bytes are still two assets.
 */
public final class Image {

    /** The API's per-file limit. */
    public static final int MAX_BYTES = 16 * 1024 * 1024;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};

    private final byte[] data;
    private final boolean png;
    private final String reference = "image-" + UUID.randomUUID();

    private Image(byte[] data, boolean png) {
        this.data = data.clone();
        this.png = png;
    }

    /**
     * A PNG image.
     *
     * @param data the file's bytes; copied
     * @return the image
     * @throws IllegalArgumentException if the bytes are not a PNG, or are empty or over 16 MiB
     */
    public static Image png(byte[] data) {
        return checked(data, true);
    }

    /**
     * A JPEG image.
     *
     * @param data the file's bytes; copied
     * @return the image
     * @throws IllegalArgumentException if the bytes are not a JPEG, or are empty or over 16 MiB
     */
    public static Image jpeg(byte[] data) {
        return checked(data, false);
    }

    /**
     * An image read from a file, PNG or JPEG by its content (not its extension).
     *
     * @param path the file
     * @return the image
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if it is neither PNG nor JPEG, or is empty or over 16 MiB
     */
    public static Image fromFile(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) {
            throw new IllegalArgumentException("An image must be between 1 byte and 16 MiB.");
        }
        byte[] data = Files.readAllBytes(path);
        if (!startsWith(data, PNG_SIGNATURE) && !startsWith(data, JPEG_SIGNATURE)) {
            throw new IllegalArgumentException("The file is neither a PNG nor a JPEG image: " + path);
        }
        return checked(data, startsWith(data, PNG_SIGNATURE));
    }

    /**
     * The placeholder written wherever this image is placed; unique per image.
     *
     * @return the reference
     */
    public String reference() {
        return reference;
    }

    /** The image file's bytes, as sent. */
    byte[] data() {
        return data.clone();
    }

    String mediaType() {
        return png ? "image/png" : "image/jpeg";
    }

    String extension() {
        return png ? "png" : "jpg";
    }

    @Override
    public String toString() {
        return "Image[" + reference + ", " + mediaType() + ", " + data.length + " bytes]";
    }

    private static Image checked(byte[] data, boolean png) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            throw new IllegalArgumentException("An image must be between 1 byte and 16 MiB.");
        }
        if (!startsWith(data, png ? PNG_SIGNATURE : JPEG_SIGNATURE)) {
            throw new IllegalArgumentException("The bytes are not a " + (png ? "PNG" : "JPEG") + " image.");
        }
        return new Image(data, png);
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        return data.length >= signature.length
                && Arrays.equals(data, 0, signature.length, signature, 0, signature.length);
    }
}
