package io.github.cmaintz.jev;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Text with images inline, for a question or a state value that mentions an image
 * mid-sentence: {@code TextWithImages.of("Is it the dog in ", poster, "?")}. Each image is
 * written as its {@link Image#reference()} and sent with the request.
 *
 * @param text the text, with each image's reference in place
 * @param images the images the text refers to, in order
 */
public record TextWithImages(String text, List<Image> images) {

    /**
     * Requires non-blank text that mentions every image's reference, since the API
     * rejects an image the request never refers to; copies the image list.
     */
    public TextWithImages {
        Preconditions.requireText(text, "text");
        images = List.copyOf(images);
        for (Image image : images) {
            if (!text.contains(image.reference())) {
                throw new IllegalArgumentException("The text does not mention " + image.reference() + ".");
            }
        }
    }

    /**
     * Concatenate text and images.
     *
     * @param parts {@link String}s and {@link Image}s, in reading order
     * @return the combined text and the images it refers to
     * @throws IllegalArgumentException if a part is neither text nor an image
     */
    public static TextWithImages of(Object... parts) {
        var text = new StringBuilder();
        for (Object part : parts) {
            text.append(textOf(part));
        }
        var images = Arrays.stream(parts)
                .filter(Image.class::isInstance)
                .map(Image.class::cast)
                .toList();
        return new TextWithImages(text.toString(), images);
    }

    private static String textOf(Object part) {
        return switch (Objects.requireNonNull(part, "part")) {
            case String s -> s;
            case Image image -> image.reference();
            default -> throw new IllegalArgumentException("TextWithImages takes text and images, not " + part);
        };
    }
}
