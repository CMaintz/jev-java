package io.github.cmaintz.jev;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The {@code multipart/form-data} body TypeSafe recommends for requests with images: the
 * JSON request as the first part, named {@code request}, then one file part per image.
 * Raw file parts avoid base64's third-larger payload, which counts toward the 32 MiB limit.
 */
final class Multipart {

    /**
     * An encoded body and the Content-Type header that names its boundary.
     *
     * @param bytes the body
     * @param contentType the header value
     */
    record Body(byte[] bytes, String contentType) {}

    private Multipart() {}

    /** The file part name for the image at {@code index}; the JSON's assets point at it. */
    static String partName(int index) {
        return "image-" + (index + 1);
    }

    static Body of(String json, List<Image> images) {
        String boundary = "jev-" + UUID.randomUUID();
        var out = new ByteArrayOutputStream();
        part(out, boundary, "name=\"request\"", "application/json", json.getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < images.size(); i++) {
            Image image = images.get(i);
            String name = partName(i);
            String disposition = "name=\"" + name + "\"; filename=\"" + name + "." + image.extension() + "\"";
            part(out, boundary, disposition, image.mediaType(), image.data());
        }
        out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return new Body(out.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }

    private static void part(
            ByteArrayOutputStream out, String boundary, String disposition, String type, byte[] content) {
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; " + disposition + "\r\nContent-Type: "
                + type + "\r\n\r\n";
        out.writeBytes(head.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(content);
        out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
}
