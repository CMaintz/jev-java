package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BoxTest {

    private static final String IMAGES_HEADER = "TypeSafe-Danger-Zone-Images";
    private static final Map<String, Question> MUG = Map.of("mug", new Box("the coffee mug in `photo`"));

    @Test
    void aBoxIsSentWithoutCriteriaAndTurnsOnTheImagesHeader() {
        var transport = new StubTransport();
        transport.client(0).systemOne("s", MUG);

        var sent = (Map<?, ?>)
                ((Map<?, ?>) ((Map<?, ?>) JsonParser.parse(transport.lastBody)).get("questions")).get("mug");
        assertEquals(Map.of("type", "box", "instructions", "the coffee mug in `photo`"), sent);
        assertEquals("true", transport.lastHeaders.get(IMAGES_HEADER));
    }

    @Test
    void requestsWithoutABoxOrImageLeaveTheImagesHeaderOff() {
        var transport = new StubTransport();
        transport.client(0).systemOne("s", Map.of("q", new Noul("Refund?")));

        assertFalse(transport.lastHeaders.containsKey(IMAGES_HEADER));
    }

    @Test
    void readsTheEdgesAndConfidence() {
        var response = new StubTransport()
                .enqueue(
                        200,
                        "{\"answers\":{\"mug\":{\"type\":\"box\","
                                + "\"box\":{\"top\":0.25,\"bottom\":0.82,\"left\":0.44,\"right\":0.84},\"confidence\":0.92}}}")
                .client(0)
                .systemOne("s", MUG);

        var mug = response.box("mug");
        assertEquals(new BoxAnswer(0.25, 0.82, 0.44, 0.84, 0.92), mug);
        assertTrue(mug.isConfident(0.9));
        assertEquals(Map.of("mug", mug), response.boxes());
    }

    @Test
    void aMissingConfidenceIsNeverConfident() {
        var mug = new StubTransport()
                .enqueue(
                        200,
                        "{\"answers\":{\"mug\":{\"box\":{\"top\":0,\"bottom\":1,\"left\":0,\"right\":1},\"confidence\":null}}}")
                .client(0)
                .systemOne("s", MUG)
                .box("mug");

        assertTrue(Double.isNaN(mug.confidence()));
        assertFalse(mug.isConfident(0.0));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"answers\":{\"mug\":{\"confidence\":0.9}}}",
                "{\"answers\":{\"mug\":{\"box\":[0,1,0,1]}}}",
                "{\"answers\":{\"mug\":{\"box\":{\"top\":0,\"bottom\":1,\"left\":0}}}}"
            })
    void aMalformedBoxIsAJevException(String body) {
        var client = new StubTransport().enqueue(200, body).client(0);

        var error = assertThrows(JevException.class, () -> client.systemOne("s", MUG));

        assertEquals(body, error.responseBody());
    }

    @Test
    void thresholdsNeverGateABox() {
        var thresholds = JevThresholds.parse("{\"version\":1,\"model\":\"jev-latest\",\"questions\":{"
                + "\"team\":{\"type\":\"choice\",\"threshold\":0.8,\"accuracy\":0.9,\"coverage\":0.5,\"n\":10}}}");
        var questions = new LinkedHashMap<String, Question>();
        questions.put("team", new Choice("Which team?", Map.of("billing", "Billing")));
        questions.put("mug", new Box("the mug"));

        var gate = thresholds.pick(questions);

        assertEquals("team", gate.source());
    }
}
