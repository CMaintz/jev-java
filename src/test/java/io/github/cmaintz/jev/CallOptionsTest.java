package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CallOptionsTest {

    private static final Map<String, Question> ONE = Map.of("q", new Noul("Refund?"));
    private static final String LONG_CONTEXT = "TypeSafe-Danger-Zone-Long-Context";

    private static Object sentModel(StubTransport transport) {
        return ((Map<?, ?>) JsonParser.parse(transport.lastBody)).get("model");
    }

    @Test
    void defaultsUseTheClientModelWithoutLongContext() {
        var transport = new StubTransport();
        transport.client(0).systemOne("s", ONE);

        assertEquals("jev-latest", sentModel(transport));
        assertFalse(transport.lastHeaders.containsKey(LONG_CONTEXT));
    }

    @Test
    void aCallCanOverrideTheModelAndTurnOnLongContext() {
        var transport = new StubTransport();
        var options = SystemOneOptions.DEFAULT.withModel("jev-preview").withLongContext(true);
        transport.client(0).systemOne("s", ONE, options);

        assertEquals("jev-preview", sentModel(transport));
        assertEquals("true", transport.lastHeaders.get(LONG_CONTEXT));
    }

    @Test
    void theAsyncCallHonorsOptionsToo() throws Exception {
        var transport = new StubTransport();
        var options = SystemOneOptions.DEFAULT.withModel("jev-1.13.0").withLongContext(true);
        transport.client(0).systemOneAsync("s", ONE, options).get(10, TimeUnit.SECONDS);

        assertEquals("jev-1.13.0", sentModel(transport));
        assertEquals("true", transport.lastHeaders.get(LONG_CONTEXT));
    }

    @Test
    void aBlankModelOverrideIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SystemOneOptions.DEFAULT.withModel(" "));
        assertThrows(
                NullPointerException.class, () -> new StubTransport().client(0).systemOne("s", ONE, null));
    }
}
