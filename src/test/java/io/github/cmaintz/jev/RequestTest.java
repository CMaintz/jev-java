package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.cmaintz.jev.json.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestTest {

    private static TypeSafeClient client(StubTransport transport) {
        return TypeSafeClient.builder().apiKey("test-key").transport(transport).build();
    }

    private static Map<?, ?> capture(StubTransport transport, Map<String, Question> questions, Object state) {
        transport.enqueue(200, "{\"answers\":{}}");
        client(transport).systemOne(state, questions);
        return (Map<?, ?>) JsonParser.parse(transport.lastBody);
    }

    @Test
    void sendsModelStateQuestionsAuthAndEndpoint() {
        var transport = new StubTransport();
        var root = capture(transport, Map.of("q", new Noul("Refund?")), Map.of("text", "hello"));

        assertEquals("jev-latest", root.get("model"));
        assertEquals("hello", ((Map<?, ?>) root.get("state")).get("text"));
        assertTrue(((Map<?, ?>) root.get("questions")).containsKey("q"));
        assertEquals("Bearer test-key", transport.lastHeaders.get("Authorization"));
        assertTrue(transport.lastUrl.endsWith("/v1/systemone"));
    }

    @Test
    void choiceCriteriaIsMapAndScoreIsArray() {
        var questions = new LinkedHashMap<String, Question>();
        questions.put("team", new Choice("Which team", Map.of("billing", "pay", "tech", "bugs")));
        questions.put("anger", new Score("How angry", List.of("calm", "cross", "furious")));

        var block = (Map<?, ?>) capture(new StubTransport(), questions, "s").get("questions");
        assertInstanceOf(Map.class, ((Map<?, ?>) block.get("team")).get("criteria"));
        var levels = ((Map<?, ?>) block.get("anger")).get("criteria");
        assertInstanceOf(List.class, levels);
        assertEquals(3, ((List<?>) levels).size());
    }

    @Test
    void noulEmitsCriteriaOnlyWhenGlossed() {
        var questions = new LinkedHashMap<String, Question>();
        questions.put("bare", new Noul("Plain?"));
        questions.put("glossed", new Noul("Refund?", "asks for money back", null));

        var block = (Map<?, ?>) capture(new StubTransport(), questions, "s").get("questions");
        assertFalse(((Map<?, ?>) block.get("bare")).containsKey("criteria"));
        var criteria = (Map<?, ?>) ((Map<?, ?>) block.get("glossed")).get("criteria");
        assertEquals("asks for money back", criteria.get("true"));
    }

    @Test
    void emptyQuestionsIsRejected() {
        var client = client(new StubTransport());
        assertThrows(IllegalArgumentException.class, () -> client.systemOne("s", Map.of()));
    }

    @Test
    void scoreAndChoiceValidateTheirCriteria() {
        assertThrows(IllegalArgumentException.class, () -> new Score("x", List.of("only one")));
        assertThrows(IllegalArgumentException.class, () -> new Choice("x", Map.of()));
    }

    @Test
    void missingApiKeyIsRejected() {
        assertThrows(
                JevException.class, () -> TypeSafeClient.builder().apiKey("   ").build());
    }
}
