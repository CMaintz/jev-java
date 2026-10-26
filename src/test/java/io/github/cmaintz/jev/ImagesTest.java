package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImagesTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 4, 5, 6};
    private static final Map<String, Question> ONE = Map.of("q", new Noul("Is there a person?"));

    /** One multipart part: its headers block and its raw bytes (ISO-8859-1 keeps bytes intact). */
    private record Part(String headers, String content) {}

    private static List<Part> parts(StubTransport transport) {
        String contentType = transport.lastHeaders.get("Content-Type");
        assertTrue(contentType.startsWith("multipart/form-data; boundary="), contentType);
        String boundary = "--" + contentType.substring(contentType.indexOf('=') + 1);
        String body = new String(plain(transport), StandardCharsets.ISO_8859_1);
        assertTrue(body.endsWith(boundary + "--\r\n"));
        return Arrays.stream(body.split(boundary))
                .filter(chunk -> chunk.startsWith("\r\n"))
                .map(chunk -> chunk.substring(2, chunk.length() - 2))
                .map(chunk -> new Part(
                        chunk.substring(0, chunk.indexOf("\r\n\r\n")), chunk.substring(chunk.indexOf("\r\n\r\n") + 4)))
                .toList();
    }

    /** The body as sent before any gzip, which large multipart bodies get too. */
    private static byte[] plain(StubTransport transport) {
        if (!"gzip".equals(transport.lastHeaders.get("Content-Encoding"))) {
            return transport.lastBodyBytes;
        }
        try (var in = new GZIPInputStream(new ByteArrayInputStream(transport.lastBodyBytes))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<?, ?> requestJson(List<Part> parts) {
        assertTrue(parts.get(0).headers().contains("name=\"request\""));
        assertTrue(parts.get(0).headers().contains("Content-Type: application/json"));
        return (Map<?, ?>) JsonParser.parse(
                new String(parts.get(0).content().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
    }

    @Test
    void anImageInTheStateIsSentAsAMultipartAssetUnderItsReference() {
        var transport = new StubTransport();
        var door = Image.png(PNG);
        transport.client(0).systemOne(Map.of("door", door), ONE);

        var parts = parts(transport);
        var json = requestJson(parts);
        assertEquals(Map.of("door", door.reference()), json.get("state"));
        assertEquals(Map.of(door.reference(), Map.of("type", "image", "part", "image-1")), json.get("assets"));
        assertTrue(parts.get(1).headers().contains("name=\"image-1\"; filename=\"image-1.png\""));
        assertTrue(parts.get(1).headers().contains("Content-Type: image/png"));
        assertEquals(new String(PNG, StandardCharsets.ISO_8859_1), parts.get(1).content());
        assertEquals("true", transport.lastHeaders.get("TypeSafe-Danger-Zone-Images"));
    }

    @Test
    void imagesInQuestionsAndInlineTextAreCollectedOnceEach() {
        var transport = new StubTransport();
        var poster = Image.jpeg(JPEG);
        var thumb = Image.png(PNG);
        var questions = new LinkedHashMap<String, Question>();
        questions.put("same", new Noul(TextWithImages.of("Is the dog in `photo` the dog in ", poster, "?")));
        questions.put("again", new Noul(poster));
        questions.put("react", new Choice("Which reaction?", Map.of("thumbs_up", thumb, "none", "No reaction.")));
        transport.client(0).systemOne("s", questions);

        var parts = parts(transport);
        var json = requestJson(parts);
        var sent = (Map<?, ?>) json.get("questions");
        assertEquals(
                "Is the dog in `photo` the dog in " + poster.reference() + "?",
                ((Map<?, ?>) sent.get("same")).get("instructions"));
        assertEquals(poster.reference(), ((Map<?, ?>) sent.get("again")).get("instructions"));
        assertEquals(thumb.reference(), ((Map<?, ?>) ((Map<?, ?>) sent.get("react")).get("criteria")).get("thumbs_up"));
        assertEquals(
                List.of(poster.reference(), thumb.reference()), List.copyOf(((Map<?, ?>) json.get("assets")).keySet()));
        assertEquals(3, parts.size());
        assertTrue(parts.get(1).headers().contains("Content-Type: image/jpeg"));
    }

    @Test
    void imagesNestedInStructuredCriteriaAndGlossesAreSent() {
        var transport = new StubTransport();
        var example = Image.png(PNG);
        var low = Image.png(PNG);
        var yes = Image.jpeg(JPEG);
        var questions = new LinkedHashMap<String, Question>();
        questions.put("team", new Choice("Which team?", Map.of("billing", Map.of("examples", List.of(example)))));
        questions.put("mess", new Score("How messy?", List.of(low, "Very messy.")));
        questions.put("dog", new Noul("Is it a dog?", yes, null));
        transport.client(0).systemOne("s", questions);

        var json = requestJson(parts(transport));
        var assets = ((Map<?, ?>) json.get("assets")).keySet();
        assertEquals(List.of(example.reference(), low.reference(), yes.reference()), List.copyOf(assets));
    }

    @Test
    void aGatedQuestionWithAnImageNeverWarnsAboutRewording() {
        var thresholds = JevThresholds.parse("{\"version\":1,\"model\":\"jev-latest\",\"questions\":{"
                + "\"react\":{\"type\":\"choice\",\"threshold\":0.8,\"accuracy\":0.9,\"coverage\":0.5,\"n\":10}},"
                + "\"definitions\":{\"react\":{\"type\":\"choice\",\"instructions\":\"Which?\","
                + "\"criteria\":{\"up\":\"image-old\"}}}}");
        var question = new Choice("Which?", Map.of("up", Image.png(PNG)));

        assertEquals(List.of(), thresholds.pick(Map.of("react", question)).warnings());
    }

    @Test
    void twoImagesWithTheSameBytesAreTwoAssets() {
        var transport = new StubTransport();
        var first = Image.png(PNG);
        var second = Image.png(PNG);
        transport.client(0).systemOne(List.of(first, second), ONE);

        var parts = parts(transport);
        assertEquals(3, parts.size());
        assertEquals(2, ((Map<?, ?>) requestJson(parts).get("assets")).size());
    }

    @Test
    void largeMultipartBodiesAreGzipped() {
        var transport = new StubTransport();
        transport.client(0).systemOne(Map.of("door", Image.png(Arrays.copyOf(PNG, 8192))), ONE);

        assertEquals("gzip", transport.lastHeaders.get("Content-Encoding"));
        assertEquals(2, parts(transport).size());
    }

    @Test
    void requestsWithoutImagesStayPlainJson() {
        var transport = new StubTransport();
        transport.client(0).systemOne("s", ONE);

        assertEquals("application/json", transport.lastHeaders.get("Content-Type"));
        assertFalse(((Map<?, ?>) JsonParser.parse(transport.lastBody)).containsKey("assets"));
    }

    @Test
    void imageBytesAreCopiedAndChecked(@TempDir Path dir) throws IOException {
        byte[] source = PNG.clone();
        var image = Image.png(source);
        source[9] = 99;
        var transport = new StubTransport();
        transport.client(0).systemOne(image, ONE);
        assertEquals(
                new String(PNG, StandardCharsets.ISO_8859_1),
                parts(transport).get(1).content());

        assertThrows(IllegalArgumentException.class, () -> Image.png(JPEG));
        assertThrows(IllegalArgumentException.class, () -> Image.jpeg(PNG));
        assertThrows(IllegalArgumentException.class, () -> Image.png(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> Image.png(Arrays.copyOf(PNG, 16 * 1024 * 1024 + 1)));

        Path jpg = Files.write(dir.resolve("photo.bin"), JPEG);
        Path gif = Files.write(dir.resolve("anim.gif"), "GIF89a".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(JPEG, Image.fromFile(jpg).data());
        assertThrows(IllegalArgumentException.class, () -> Image.fromFile(gif));
    }

    @Test
    void textWithImagesTakesOnlyTextAndImages() {
        assertThrows(IllegalArgumentException.class, () -> TextWithImages.of("count: ", 3));
        assertThrows(IllegalArgumentException.class, () -> TextWithImages.of("  "));
        assertThrows(IllegalArgumentException.class, () -> new TextWithImages("hi", List.of(Image.png(PNG))));
        var image = Image.png(PNG);
        assertEquals(new TextWithImages("see " + image.reference(), List.of(image)), TextWithImages.of("see ", image));
    }
}
