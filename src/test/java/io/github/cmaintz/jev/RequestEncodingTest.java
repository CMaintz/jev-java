package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestEncodingTest {

    private static Map<?, ?> capture(StubTransport transport, Map<String, Question> questions, Object state) {
        transport.client(0).systemOne(state, questions);
        return (Map<?, ?>) JsonParser.parse(transport.lastBody);
    }

    private static Map<?, ?> questionsBlock(Map<String, Question> questions) {
        return (Map<?, ?>) capture(new StubTransport(), questions, "s").get("questions");
    }

    @Test
    void sendsModelStateQuestionsAuthAndEndpoint() {
        var transport = new StubTransport();
        var root = capture(transport, Map.of("q", new Noul("Refund?")), Map.of("text", "hello"));

        assertEquals("jev-latest", root.get("model"));
        assertEquals("hello", ((Map<?, ?>) root.get("state")).get("text"));
        assertEquals("noul", ((Map<?, ?>) ((Map<?, ?>) root.get("questions")).get("q")).get("type"));
        assertEquals("Bearer test-key", transport.lastHeaders.get("Authorization"));
        assertEquals("https://api.typesafe.ai/v1/systemone", transport.lastUrl);
    }

    @Test
    void baseUrlTrailingSlashesAreTrimmed() {
        var transport = new StubTransport();
        TypeSafeClient.builder()
                .apiKey("k")
                .baseUrl("http://localhost:8080//")
                .transport(transport)
                .build()
                .systemOne("s", Map.of("q", new Noul("Refund?")));
        assertEquals("http://localhost:8080/v1/systemone", transport.lastUrl);
    }

    @Test
    void choiceCriteriaIsMapAndScoreIsArray() {
        var questions = new LinkedHashMap<String, Question>();
        questions.put("team", new Choice("Which team", Map.of("billing", "pay", "tech", "bugs")));
        questions.put("anger", new Score("How angry", List.of("calm", "cross", "furious")));

        var block = questionsBlock(questions);
        assertInstanceOf(Map.class, ((Map<?, ?>) block.get("team")).get("criteria"));
        assertEquals(List.of("calm", "cross", "furious"), ((Map<?, ?>) block.get("anger")).get("criteria"));
    }

    @Test
    void choiceOptionsKeepTheCallersOrder() {
        var options = new LinkedHashMap<String, String>();
        for (String option : List.of("zeta", "alpha", "mid", "beta", "omega")) {
            options.put(option, "about " + option);
        }
        var criteria = (Map<?, ?>) ((Map<?, ?>)
                        questionsBlock(Map.of("q", new Choice("Pick", options))).get("q"))
                .get("criteria");
        assertEquals(List.copyOf(options.keySet()), List.copyOf(criteria.keySet()));
    }

    @Test
    void noulEmitsCriteriaOnlyWhenGlossed() {
        var questions = new LinkedHashMap<String, Question>();
        questions.put("bare", new Noul("Plain?"));
        questions.put("glossed", new Noul("Refund?", "asks for money back", null));

        var block = questionsBlock(questions);
        assertFalse(((Map<?, ?>) block.get("bare")).containsKey("criteria"));
        var criteria = (Map<?, ?>) ((Map<?, ?>) block.get("glossed")).get("criteria");
        assertEquals("asks for money back", criteria.get("true"));
        assertFalse(criteria.containsKey("false"));
    }

    @Test
    void emptyQuestionsIsRejected() {
        var client = new StubTransport().client(0);
        assertThrows(IllegalArgumentException.class, () -> client.systemOne("s", Map.of()));
    }

    @Test
    void unsupportedStateIsRejectedBeforeSending() {
        var transport = new StubTransport();
        var client = transport.client(0);
        assertThrows(
                IllegalArgumentException.class, () -> client.systemOne(new Object(), Map.of("q", new Noul("Refund?"))));
        assertEquals(0, transport.calls);
    }

    @Test
    void questionsValidateTheirArguments() {
        assertThrows(IllegalArgumentException.class, () -> new Score("x", List.of("only one")));
        assertThrows(IllegalArgumentException.class, () -> new Choice("x", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Noul(" "));
        assertThrows(IllegalArgumentException.class, () -> new Choice(null, Map.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> new Choice("x", Map.of("a", " ")));
        assertThrows(IllegalArgumentException.class, () -> new Score("x", List.of("low", "")));
        var tooMany = new LinkedHashMap<String, String>();
        for (int i = 0; i <= Choice.MAX_OPTIONS; i++) {
            tooMany.put("o" + i, "option " + i);
        }
        assertThrows(IllegalArgumentException.class, () -> new Choice("x", tooMany));
    }

    @Test
    void builderValidatesItsSettings() {
        var builder = TypeSafeClient.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.apiKey("   "));
        assertThrows(IllegalArgumentException.class, () -> builder.maxRetries(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.model(""));
        assertThrows(IllegalArgumentException.class, () -> builder.timeout(Duration.ZERO));
    }

    @Test
    void buildRejectsUnusableUrlsAndKeys() {
        assertThrows(IllegalArgumentException.class, () -> TypeSafeClient.builder()
                .apiKey("k")
                .baseUrl("api.typesafe.ai")
                .build());
        assertThrows(IllegalArgumentException.class, () -> TypeSafeClient.builder()
                .apiKey("k")
                .baseUrl("ftp://example.com")
                .build());
        assertThrows(IllegalArgumentException.class, () -> TypeSafeClient.builder()
                .apiKey("k")
                .baseUrl("https://proxy.example.com?x=1")
                .build());
        assertThrows(JevException.class, () -> TypeSafeClient.builder()
                .apiKey("ke\ny")
                .transport(new StubTransport())
                .build());
        assertThrows(JevException.class, () -> TypeSafeClient.builder()
                .apiKey("sk-abc€")
                .transport(new StubTransport())
                .build());
    }

    @Test
    void baseUrlMayCarryAPathPrefixAndPort() {
        var transport = new StubTransport();
        TypeSafeClient.builder()
                .apiKey("k")
                .baseUrl("http://localhost:8080/jev/")
                .transport(transport)
                .build()
                .systemOne("s", Map.of("q", new Noul("Refund?")));
        assertEquals("http://localhost:8080/jev/v1/systemone", transport.lastUrl);
    }

    @Test
    void apiKeyWhitespaceIsStripped() {
        var transport = new StubTransport();
        TypeSafeClient.builder()
                .apiKey(" secret\n")
                .transport(transport)
                .build()
                .systemOne("s", Map.of("q", new Noul("Refund?")));
        assertEquals("Bearer secret", transport.lastHeaders.get("Authorization"));
    }
}
