package io.github.cmaintz.jev;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Decodes a {@code GET /v1/models} body. Anything that is not the documented shape
 * surfaces as a {@link JevException} carrying the raw body, as for System One responses.
 */
final class ModelsDecoder {

    private final ResponseBody body;

    private ModelsDecoder(String body) {
        this.body = new ResponseBody(body);
    }

    static List<ModelInfo> decode(String body) {
        return new ModelsDecoder(body).decode();
    }

    private List<ModelInfo> decode() {
        if (!(body.parse() instanceof Map<?, ?> root) || !(root.get("models") instanceof List<?> models)) {
            throw malformed("expected an object with a \"models\" list");
        }
        return models.stream().map(this::model).toList();
    }

    private ModelInfo model(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            throw malformed("a model entry is not an object");
        }
        return new ModelInfo(
                body.requireString(map, "name"),
                body.requireString(map, "description"),
                date(body.requireString(map, "release_date")));
    }

    private LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw malformed("release_date is not YYYY-MM-DD: " + value);
        }
    }

    private JevException malformed(String detail) {
        return body.malformed(detail);
    }
}
