package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ThresholdsTest {

    private static final Choice TEAM = new Choice("Which team?", Map.of("tech", "Tech", "billing", "Billing"));
    private static final Score SENTIMENT = new Score("sentiment", List.of("negative", "neutral", "positive"));
    private static final Noul URGENT = new Noul("Is it urgent?");

    private static JevThresholds fixture() throws IOException, URISyntaxException {
        return JevThresholds.load(
                Path.of(ThresholdsTest.class.getResource("/thresholds.json").toURI()));
    }

    private static Map<String, Question> questions(Object... idsAndQuestions) {
        var map = new LinkedHashMap<String, Question>();
        for (int i = 0; i < idsAndQuestions.length; i += 2) {
            map.put((String) idsAndQuestions[i], (Question) idsAndQuestions[i + 1]);
        }
        return map;
    }

    private static String file(String questions, String extra) {
        return "{\"version\":1,\"model\":\"jev-latest\",\"questions\":{" + questions + "}" + extra + "}";
    }

    private static final String GATE = "{\"threshold\":0.8,\"accuracy\":0.9,\"coverage\":0.5,\"n\":10}";

    @Test
    void oneGatedQuestionUsesItsOwnGateAndIgnoresNouls() throws Exception {
        ThresholdGate gate = fixture().pick(questions("team", TEAM, "urgent", URGENT), "jev-latest");

        assertEquals(0.8, gate.threshold());
        assertEquals(0.94, gate.accuracy());
        assertEquals(0.61, gate.coverage());
        assertEquals(300, gate.n());
        assertEquals("team", gate.source());
        assertEquals(List.of("team"), gate.questionIds());
        assertEquals("jev-latest", gate.model());
        assertEquals(List.of(), gate.warnings());
    }

    @Test
    void severalGatedQuestionsUseTheCompositeGate() throws Exception {
        ThresholdGate gate = fixture().pick(questions("team", TEAM, "sentiment", SENTIMENT, "urgent", URGENT));

        assertEquals(0.82, gate.threshold());
        assertEquals(ThresholdGate.COMPOSITE, gate.source());
        assertEquals(List.of("sentiment", "team"), gate.questionIds());
    }

    @Test
    void compositeMeasuredOverOtherQuestionsIsRefused() throws Exception {
        var other = new Choice("Which product?", Map.of("a", "A"));
        var error = assertThrows(
                IllegalArgumentException.class, () -> fixture().pick(questions("team", TEAM, "product", other)));

        assertTrue(error.getMessage().contains("[sentiment, team]"), error.getMessage());
        assertTrue(error.getMessage().contains("[product, team]"), error.getMessage());
        assertTrue(error.getMessage().contains("re-run jev-eval thresholds"), error.getMessage());
    }

    @Test
    void missingGatesAndAllNoulQuestionsAreRefused() throws Exception {
        JevThresholds noComposite = JevThresholds.parse(file("\"team\":" + GATE, ""));
        var other = new Choice("Which product?", Map.of("a", "A"));

        assertMessage("every question is a noul", () -> fixture().pick(questions("urgent", URGENT)));
        assertMessage("no gate for \"product\"", () -> fixture().pick(questions("product", other)));
        assertMessage("row gate unstable", () -> noComposite.pick(questions("team", TEAM, "product", other)));
    }

    @Test
    void warnsOnAModelChangeAndARewordedQuestion() throws Exception {
        var reworded = new Choice("Which team now?", Map.of("billing", "Billing", "tech", "Tech"));
        ThresholdGate gate = fixture().pick(questions("team", reworded, "urgent", new Noul("changed")), "jev-2");

        assertEquals(2, gate.warnings().size());
        assertTrue(gate.warnings().get(0).contains("measured on jev-latest but this run uses jev-2"));
        assertEquals(
                "team was reworded since it was measured; re-measure",
                gate.warnings().get(1));
    }

    @Test
    void reorderedScoreLevelsCountAsReworded() throws Exception {
        var reordered = new Score("sentiment", List.of("positive", "neutral", "negative"));

        assertEquals(
                1, fixture().pick(questions("sentiment", reordered)).warnings().size());
    }

    @Test
    void escalatesOnTheLowestGatedConfidence() throws Exception {
        ThresholdGate gate = fixture().pick(questions("team", TEAM, "sentiment", SENTIMENT, "urgent", URGENT));

        assertFalse(gate.shouldEscalate(response(0.9, 0.85)));
        assertTrue(gate.shouldEscalate(response(0.9, 0.81)));
        assertEquals(0.81, gate.rowConfidence(response(0.9, 0.81)));
        assertTrue(gate.shouldEscalate(response(0.9, Double.NaN)));
    }

    @Test
    void escalationNeedsAConfidenceForEveryGatedQuestion() throws Exception {
        ThresholdGate gate = fixture().pick(questions("team", TEAM));
        var noulOnly = new SystemOneResponse("jev-latest", Map.of("team", new NoulAnswer(0.9)), null);
        var empty = new SystemOneResponse("jev-latest", Map.of(), null);

        assertThrows(IllegalStateException.class, () -> gate.shouldEscalate(noulOnly));
        assertThrows(NoSuchElementException.class, () -> gate.shouldEscalate(empty));
    }

    @Test
    void malformedFilesAreRefused() {
        assertMessage("expected version 1", () -> JevThresholds.parse("{\"version\":2}"));
        assertMessage("expected a JSON object", () -> JevThresholds.parse("[]"));
        assertMessage("missing \"model\"", () -> JevThresholds.parse("{\"version\":1,\"questions\":{}}"));
        assertMessage("missing object \"questions\"", () -> JevThresholds.parse("{\"version\":1,\"model\":\"m\"}"));
        assertMessage("not an object", () -> JevThresholds.parse(file("\"team\":1", "")));
        assertMessage(
                "missing number \"accuracy\"",
                () -> JevThresholds.parse(file("\"team\":{\"threshold\":1,\"n\":1}", "")));
        assertMessage(
                "non-integer \"n\"", () -> JevThresholds.parse(file("\"team\":" + GATE.replace("10", "1.5"), "")));
        assertMessage(
                "list of question ids",
                () -> JevThresholds.parse(file("", ",\"composite\":" + GATE.replace("}", ",\"questions\":[1]}"))));
        assertThrows(IllegalArgumentException.class, () -> JevThresholds.parse("{"));
    }

    @Test
    void ignoresUnknownKeysAndAbsentDefinitions() {
        JevThresholds thresholds = JevThresholds.parse(file("\"team\":" + GATE, ",\"future\":{\"x\":[1]}"));

        assertEquals("jev-latest", thresholds.model());
        assertEquals(
                List.of(),
                thresholds.pick(questions("team", TEAM), "jev-latest").warnings());
    }

    private static SystemOneResponse response(double teamConfidence, double sentimentConfidence) {
        var answers = new LinkedHashMap<String, Answer>();
        answers.put("team", new ChoiceAnswer("billing", Map.of("billing", 0.9), teamConfidence));
        answers.put("sentiment", new ScoreAnswer(1.0, List.of(0.1, 0.8, 0.1), Map.of(), sentimentConfidence));
        answers.put("urgent", new NoulAnswer(0.5));
        return new SystemOneResponse("jev-latest", answers, null);
    }

    private static void assertMessage(String expected, org.junit.jupiter.api.function.Executable call) {
        var error = assertThrows(IllegalArgumentException.class, call);
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }
}
