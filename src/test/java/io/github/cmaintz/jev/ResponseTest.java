package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponseTest {

    private static final Map<String, Question> ONE = Map.of("q", new Noul("Refund?"));

    private static TypeSafeClient client(StubTransport transport, int retries) {
        return TypeSafeClient.builder()
                .apiKey("k")
                .transport(transport)
                .maxRetries(retries)
                .build();
    }

    private static SystemOneResponse parse(String body) {
        var transport = new StubTransport().enqueue(200, body);
        return client(transport, 3).systemOne("s", ONE);
    }

    @Test
    void readsChoiceWithProbabilityMap() {
        var response = parse("{\"model\":\"jev-1\",\"answers\":{\"team\":{\"type\":\"choice\","
                + "\"choice\":\"tech\",\"probabilities\":{\"billing\":0.1,\"tech\":0.9},\"confidence\":0.8}},"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}");
        var answer = response.get("team");

        assertEquals("choice", answer.type());
        assertEquals("tech", answer.choice());
        assertEquals(0.9, answer.probabilities().get("tech"));
        assertNull(answer.scoreProbabilities());
        assertTrue(answer.isConfident(0.7));
        assertEquals(10, response.usage().inputTokens());
        assertEquals(1, response.choices().size());
    }

    @Test
    void readsScoreWithProbabilityArrayAndLegend() {
        var answer = parse("{\"answers\":{\"anger\":{\"type\":\"score\",\"score\":1.5,"
                        + "\"legend\":{\"0\":\"calm\",\"1\":\"cross\",\"2\":\"furious\"},"
                        + "\"probabilities\":[0.2,0.3,0.5],\"confidence\":0.6}}}")
                .get("anger");

        assertEquals(1.5, answer.score());
        assertEquals(3, answer.scoreProbabilities().size());
        assertNull(answer.probabilities());
        assertEquals("furious", answer.legend().get("2"));
        assertEquals(0.5, answer.scoreProbabilities().get(2));
    }

    @Test
    void readsNoulWhichHasNoConfidence() {
        var response = parse("{\"answers\":{\"refund\":{\"type\":\"noul\",\"noul\":0.85}}}");
        var answer = response.get("refund");

        assertEquals(0.85, answer.noul());
        assertNull(answer.confidence());
        assertFalse(answer.isConfident(0.5));
        assertEquals(1, response.nouls().size());
    }

    @Test
    void infersTypeWhenTheFieldIsAbsent() {
        var answer = parse("{\"answers\":{\"x\":{\"choice\":\"a\",\"probabilities\":{\"a\":1.0},\"confidence\":1.0}}}")
                .get("x");
        assertEquals("choice", answer.type());
    }

    @Test
    void retriesA429ThenSucceeds() {
        var transport = new StubTransport().enqueue(429, "{}").enqueue(200, "{\"answers\":{}}");
        client(transport, 3).systemOne("s", ONE);
        assertEquals(2, transport.calls);
    }

    @Test
    void unauthorizedAndValidationMapToTypedErrors() {
        assertThrows(JevAuthException.class, () -> client(new StubTransport().enqueue(401, "{}"), 3)
                .systemOne("s", ONE));
        assertThrows(JevValidationException.class, () -> client(new StubTransport().enqueue(422, "{}"), 3)
                .systemOne("s", ONE));
    }

    @Test
    void exhaustedRetriesSurfaceAsRateLimit() {
        var transport = new StubTransport().enqueue(429, "{}");
        assertThrows(JevRateLimitException.class, () -> client(transport, 0).systemOne("s", ONE));
        assertEquals(1, transport.calls);
    }

    @Test
    void overloaded529IsTypedWhenExhausted() {
        assertThrows(JevOverloadedException.class, () -> client(new StubTransport().enqueue(529, "{}"), 0)
                .systemOne("s", ONE));
    }

    @Test
    void unknownStatusCarriesItsCode() {
        var error = assertThrows(JevException.class, () -> client(new StubTransport().enqueue(500, "boom"), 0)
                .systemOne("s", ONE));
        assertEquals(500, error.statusCode());
    }
}
