package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ResponseDecodingTest {

    private static final Map<String, Question> QUESTIONS = Map.of(
            "team", new Choice("Which team", Map.of("billing", "pay", "tech", "bugs")),
            "anger", new Score("How angry", List.of("calm", "cross", "furious")),
            "refund", new Noul("Refund?"));

    private static SystemOneResponse decode(String body) {
        return new StubTransport().enqueue(200, body).client(0).systemOne("s", QUESTIONS);
    }

    @Test
    void readsChoiceWithProbabilityMap() {
        var response = decode("{\"model\":\"jev-1\",\"answers\":{\"team\":{\"type\":\"choice\","
                + "\"choice\":\"tech\",\"probabilities\":{\"billing\":0.1,\"tech\":0.9},\"confidence\":0.8}},"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}");
        var answer = response.choice("team");

        assertEquals("jev-1", response.model());
        assertEquals("tech", answer.choice());
        assertEquals(
                List.of("billing", "tech"), List.copyOf(answer.probabilities().keySet()));
        assertTrue(answer.isConfident(0.7));
        assertEquals(new Usage(10, 2), response.usage());
        assertEquals(1, response.choices().size());
    }

    @Test
    void readsScoreWithProbabilityArrayAndLegend() {
        var answer = decode("{\"answers\":{\"anger\":{\"score\":1.5,"
                        + "\"legend\":{\"0\":\"calm\",\"1\":\"cross\",\"2\":\"furious\"},"
                        + "\"probabilities\":[0.2,0.3,0.5],\"confidence\":0.6}}}")
                .score("anger");

        assertEquals(1.5, answer.score());
        assertEquals(List.of(0.2, 0.3, 0.5), answer.probabilities());
        assertEquals("furious", answer.legend().get("2"));
        assertFalse(answer.isConfident(0.7));
    }

    @Test
    void readsNoulAndTreatsMissingUsageAsNull() {
        var response = decode("{\"answers\":{\"refund\":{\"noul\":0.85}}}");

        assertEquals(0.85, response.noul("refund").probability());
        assertTrue(response.noul("refund").isTrue(0.85));
        assertEquals(1, response.nouls().size());
        assertTrue(response.scores().isEmpty());
        assertNull(response.usage());
        assertNull(response.model());
    }

    @Test
    void answerShapeComesFromTheQuestionAskedAndUnknownIdsAreIgnored() {
        var response = decode("{\"answers\":{\"refund\":{\"type\":\"bogus\",\"noul\":0.1},\"extra\":{\"noul\":1}}}");

        assertInstanceOf(NoulAnswer.class, response.get("refund"));
        assertEquals(1, response.answers().size());
    }

    @Test
    void choiceAndScoreAnswersGateUniformlyAsCalibrated() {
        var response = decode("{\"answers\":{\"team\":{\"choice\":\"tech\",\"confidence\":0.9},"
                + "\"anger\":{\"score\":0.4,\"confidence\":0.3},\"refund\":{\"noul\":0.99}}}");

        var confident = response.answers().entrySet().stream()
                .filter(e -> e.getValue() instanceof CalibratedAnswer c && c.isConfident(0.7))
                .map(Map.Entry::getKey)
                .toList();
        assertEquals(List.of("team"), confident);
        assertEquals("noul 0.99", describe(response.get("refund")));
    }

    private static String describe(Answer answer) {
        return switch (answer) {
            case ChoiceAnswer c -> "choice " + c.choice();
            case ScoreAnswer s -> "score " + s.score();
            case NoulAnswer n -> "noul " + n.probability();
        };
    }

    @Test
    void missingConfidenceIsNeverConfident() {
        var answer = decode("{\"answers\":{\"team\":{\"choice\":\"tech\"}}}").choice("team");
        assertTrue(Double.isNaN(answer.confidence()));
        assertFalse(answer.isConfident(0.0));
    }

    @Test
    void accessorsRejectMissingIdsAndWrongTypes() {
        var response = decode("{\"answers\":{\"refund\":{\"noul\":0.5}}}");
        assertThrows(NoSuchElementException.class, () -> response.get("team"));
        assertThrows(IllegalStateException.class, () -> response.choice("refund"));
    }

    @Test
    void malformedBodiesSurfaceAsJevExceptionWithTheBody() {
        var notJson = assertThrows(JevException.class, () -> decode("<html>oops</html>"));
        assertEquals("<html>oops</html>", notJson.responseBody());
        assertThrows(JevException.class, () -> decode("[]"));
        assertThrows(JevException.class, () -> decode("{\"answers\":{\"refund\":{}}}"));
        assertThrows(
                JevException.class, () -> decode("{\"answers\":{\"anger\":{\"score\":1,\"probabilities\":[\"x\"]}}}"));
    }

    @Test
    void asyncCallCompletesWithTheDecodedResponse() throws Exception {
        var future = new StubTransport()
                .enqueue(200, "{\"answers\":{\"refund\":{\"noul\":0.2}}}")
                .client(0)
                .systemOneAsync("s", QUESTIONS);
        assertEquals(0.2, future.get(5, TimeUnit.SECONDS).noul("refund").probability());
    }
}
