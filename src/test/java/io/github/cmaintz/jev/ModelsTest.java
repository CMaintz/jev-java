package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelsTest {

    private static final String MODELS = "{\"models\":["
            + "{\"name\":\"jev-latest\",\"description\":\"General-purpose.\",\"release_date\":\"2026-09-15\"},"
            + "{\"name\":\"jev-preview\",\"description\":\"Newest build.\",\"release_date\":\"2026-10-14\"}]}";

    @Test
    void listsModelsWithAnAuthenticatedGet() {
        var transport = new StubTransport().enqueue(200, MODELS);

        var models = transport.client(0).models();

        assertEquals(
                List.of(
                        new ModelInfo("jev-latest", "General-purpose.", LocalDate.of(2026, 9, 15)),
                        new ModelInfo("jev-preview", "Newest build.", LocalDate.of(2026, 10, 14))),
                models);
        assertEquals("GET", transport.lastMethod);
        assertEquals("https://api.typesafe.ai/v1/models", transport.lastUrl);
        assertEquals("Bearer test-key", transport.lastHeaders.get("Authorization"));
        assertNull(transport.lastBody);
    }

    @Test
    void asyncListingRetriesLikeSystemOne() throws Exception {
        var transport = new StubTransport().enqueue(503, "{}").enqueue(200, MODELS);

        var models = transport.client(1).modelsAsync().get(10, TimeUnit.SECONDS);

        assertEquals(2, models.size());
        assertEquals(2, transport.calls);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "{}",
                "{\"models\":[1]}",
                "{\"models\":[{\"description\":\"d\",\"release_date\":\"2026-09-15\"}]}",
                "{\"models\":[{\"name\":\"m\",\"description\":\"d\",\"release_date\":\"15/09/2026\"}]}"
            })
    void malformedListingsSurfaceAsJevExceptionWithTheBody(String body) {
        var client = new StubTransport().enqueue(200, body).client(0);

        var error = assertThrows(JevException.class, client::models);

        assertEquals(body, error.responseBody());
    }
}
