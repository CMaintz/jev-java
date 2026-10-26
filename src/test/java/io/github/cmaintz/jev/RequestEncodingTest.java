package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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
        JevClient.builder()
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
    void structuredInstructionsAndCriteriaAreSentAsJson() {
        var instructions = Map.of("task", "Pick the team", "exclude", List.of("spam"));
        var billing = Map.of("description", "Payments", "examples", List.of("double charge", 2));
        var questions = new LinkedHashMap<String, Question>();
        questions.put("team", new Choice(instructions, Map.of("billing", billing, "tech", "Bugs")));
        questions.put("urgency", new Score("How urgent", List.of(Map.of("level", "low"), "high")));
        questions.put("spam", new Noul(List.of("Is this spam?", "Ignore greetings."), Map.of("means", "ads"), null));

        var block = questionsBlock(questions);
        var team = (Map<?, ?>) block.get("team");
        assertEquals(Map.of("task", "Pick the team", "exclude", List.of("spam")), team.get("instructions"));
        assertEquals(
                List.of("double charge", 2.0),
                ((Map<?, ?>) ((Map<?, ?>) team.get("criteria")).get("billing")).get("examples"));
        assertEquals(List.of(Map.of("level", "low"), "high"), ((Map<?, ?>) block.get("urgency")).get("criteria"));
        assertEquals(Map.of("true", Map.of("means", "ads")), ((Map<?, ?>) block.get("spam")).get("criteria"));
    }

    @Test
    void structuredPromptsAreCopiedSoLaterChangesDoNotLeak() {
        var examples = new ArrayList<Object>(List.of("double charge"));
        var description = new LinkedHashMap<String, Object>(Map.of("examples", examples));
        var choice = new Choice("Which team", Map.of("billing", description));
        examples.add("refund");
        description.put("extra", "x");

        assertEquals(Map.of("billing", Map.of("examples", List.of("double charge"))), choice.criteria());
        var frozen = (Map<?, ?>) choice.criteria().get("billing");
        assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) frozen.get("examples")).add("x"));
    }

    @Test
    void structuredPromptsRejectWhatJsonCannotCarry() {
        assertThrows(IllegalArgumentException.class, () -> new Noul(new Object()));
        assertThrows(IllegalArgumentException.class, () -> new Noul(42));
        assertThrows(IllegalArgumentException.class, () -> new Noul(Map.of(1, "non-string key")));
        assertThrows(IllegalArgumentException.class, () -> new Noul(Map.of("n", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> new Score("x", List.of(Map.of("at", new Object()))));
        assertThrows(IllegalArgumentException.class, () -> new Noul(Map.of("n", new AtomicLong(1))));
        assertThrows(IllegalArgumentException.class, () -> new Noul(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Noul(List.of()));
    }

    @Test
    void structuredPromptsNestUpToTheApiLimitOf64() {
        assertEquals(nested(64), new Noul(nested(64)).instructions());
        assertThrows(IllegalArgumentException.class, () -> new Noul(nested(65)));
        assertEquals(List.of(nested(63)), new Score("x", List.of(nested(63))).criteria());
        assertThrows(IllegalArgumentException.class, () -> new Score("x", List.of(nested(64))));
    }

    private static Object nested(int levels) {
        Object value = "leaf";
        for (int i = 0; i < levels; i++) {
            value = List.of(value);
        }
        return value;
    }

    @Test
    void aScoreMayHaveASingleLevel() {
        assertEquals(List.of("only one"), new Score("x", List.of("only one")).criteria());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Score("x", List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k")));
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
        assertThrows(IllegalArgumentException.class, () -> new Score("x", List.of()));
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
        var builder = JevClient.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.apiKey("   "));
        assertThrows(IllegalArgumentException.class, () -> builder.maxRetries(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.model(""));
        assertThrows(IllegalArgumentException.class, () -> builder.timeout(Duration.ZERO));
    }

    @Test
    void buildRejectsUnusableUrlsAndKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JevClient.builder().apiKey("k").baseUrl("api.typesafe.ai").build());
        assertThrows(IllegalArgumentException.class, () -> JevClient.builder()
                .apiKey("k")
                .baseUrl("ftp://example.com")
                .build());
        assertThrows(IllegalArgumentException.class, () -> JevClient.builder()
                .apiKey("k")
                .baseUrl("https://proxy.example.com?x=1")
                .build());
        assertThrows(JevException.class, () -> JevClient.builder()
                .apiKey("ke\ny")
                .transport(new StubTransport())
                .build());
        assertThrows(JevException.class, () -> JevClient.builder()
                .apiKey("sk-abc€")
                .transport(new StubTransport())
                .build());
    }

    @Test
    void baseUrlMayCarryAPathPrefixAndPort() {
        var transport = new StubTransport();
        JevClient.builder()
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
        JevClient.builder()
                .apiKey(" secret\n")
                .transport(transport)
                .build()
                .systemOne("s", Map.of("q", new Noul("Refund?")));
        assertEquals("Bearer secret", transport.lastHeaders.get("Authorization"));
    }
}
