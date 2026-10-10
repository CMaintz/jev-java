# jev-java

A small, dependency-free Java client for [TypeSafe AI's](https://typesafe.ai) Jev,
a "System One" model that returns typed judgments instead of free text. You send a
piece of *state* and a set of typed questions; you get back typed answers with
calibrated confidence, which your code can act on directly.

> This is my own unofficial SDK, not published or endorsed by TypeSafe. It's built
> against the public API at `https://api.typesafe.ai`.

## Why

A chat LLM hands you a paragraph you have to parse and second-guess. Jev hands you a
value with a shape: an enum choice, a number on a scale, or a yes/no probability, each
with a confidence you can threshold on. The idea is to run Jev on everything and only
escalate the low-confidence cases to a person or a bigger model.

## Requirements

Java 21 or newer. No runtime dependencies (built on `java.net.http` and a small
internal JSON codec).

## Installation

Not yet published to Maven Central (see [Roadmap](#roadmap)), so there are no
coordinates to add yet. Build from source and put the jar on your classpath:

```bash
git clone https://github.com/CMaintz/jev-java.git && cd jev-java
./gradlew jar          # -> build/libs/jev-0.1.0.jar
```

or consume it as a Gradle composite build (`includeBuild("../jev-java")` in your
`settings.gradle.kts`, then `implementation("io.github.cmaintz:jev:0.1.0")`).

## Quick start

```java
import io.github.cmaintz.jev.*;
import java.util.List;
import java.util.Map;

var client = TypeSafeClient.fromEnvironment(); // reads TYPESAFE_API_KEY

Map<String, Question> questions = Map.of(
    "team", new Choice(
        "Which team should handle this ticket",
        Map.of(
            "billing", "Payment or subscription issues",
            "technical", "Bugs or integration problems",
            "sales", "Pricing or account questions")),
    "anger", new Score(
        "How frustrated the customer appears",
        List.of("Calm, just stating facts", "Frustrated but civil", "Very angry")),
    "refund", new Noul("Does the customer ask for a refund?"));

SystemOneResponse response = client.systemOne(
    Map.of("subject", "Charged twice!", "body", "I want my money back."),
    questions);

ChoiceAnswer team = response.choice("team");
if (team.isConfident(0.7)) {
    route(team.choice());            // "billing"
} else {
    escalateToHuman();               // distribution was spread out
}

double anger = response.score("anger").score();          // e.g. 1.8
boolean wantsRefund = response.noul("refund").isTrue(0.5);
```

The three questions above are answered in a single request. Independent questions are
evaluated in parallel, so batching them is close to free. For non-blocking calls, use
`client.systemOneAsync(state, questions)`, which returns a `CompletableFuture`; it uses
`HttpClient.sendAsync` and schedules retry backoff without holding a thread.

## The three primitives

| Question | Ask when | Answer | Answer fields |
| --- | --- | --- | --- |
| `Choice` | one of a defined set | `ChoiceAnswer` | `choice()`, `probabilities()` (per option), `confidence()` |
| `Score` | a position on an ordered scale | `ScoreAnswer` | `score()`, `probabilities()` (per level), `legend()`, `confidence()` |
| `Noul` | a yes/no condition | `NoulAnswer` | `probability()` (0..1); no confidence |

Each answer mirrors the question asked under its id. Fetch it with the typed accessors
`response.choice(id)` / `score(id)` / `noul(id)`, or switch over the sealed `Answer`
returned by `response.get(id)`:

```java
switch (response.get(id)) {
    case ChoiceAnswer c -> ...
    case ScoreAnswer s -> ...
    case NoulAnswer n -> ...
}
```

`Choice` criteria is a map of option to description (1 to 255 options). `Score` criteria
is an ordered list of 2 to 10 level descriptions, low to high. The model cannot pick an
option you did not give it, so include a no-match option when nothing may fit.

## Confidence

`ChoiceAnswer` and `ScoreAnswer` are both `CalibratedAnswer`s. They carry a
`confidence()` in `[0, 1]` derived from how peaked the probability distribution is, and
`isConfident(threshold)` gates either kind:

```java
if (answer instanceof CalibratedAnswer c && c.isConfident(0.7)) { ... }
```

`isConfident` is false when the service omitted the confidence (`NaN`). A `Noul` has no
confidence, so gate it on the probability itself with `NoulAnswer.isTrue(threshold)`;
near 0.5 means genuinely uncertain, not "medium yes". Both helpers pass at or above the
threshold. A confidence threshold is not one number: use a
stricter bar for consequential actions than for harmless ones, and tune it on your data.

### Measured thresholds from jev-eval

Rather than guessing a threshold, measure one on labeled data with
[jev-eval](https://github.com/CMaintz/jev-eval) and load its `thresholds.json`
(contract version 1). `pick` returns the gate for the questions you send: a single
Choice/Score question gets its own gate; several get the row-level `composite` gate,
which must have been measured over exactly those questions. Nouls are ignored.

```java
ThresholdGate gate = JevThresholds.load(Path.of("thresholds.json"))
    .pick(questions, "jev-latest");     // model is optional; null skips the check
gate.warnings().forEach(log::warn);    // model changed, or a question was reworded
log.info("gate {} ({} accurate at {} coverage, n={}, model {})",
    gate.threshold(), gate.accuracy(), gate.coverage(), gate.n(), gate.model());

if (gate.shouldEscalate(response)) {   // lowest Choice/Score confidence < threshold
    escalateToHuman();
}
```

A file that is not version 1, or that has no gate for your questions, throws
`IllegalArgumentException` with the reason. Warnings never throw: they are returned on
`gate.warnings()` for you to log.

## Errors

All service failures derive from `JevException`, which carries `statusCode()` and
`responseBody()`. A network error, an interruption, or a malformed response body also
surfaces as a `JevException` (status `0`), as does a missing API key at `build()`.
Invalid arguments, such as an empty question map, an unsupported state type, or a
base URL that is not an absolute http(s) URL, throw `IllegalArgumentException`
before any request is sent.

| Exception | HTTP | Meaning |
| --- | --- | --- |
| `JevAuthException` | 401 | missing or invalid API key |
| `JevValidationException` | 422 | the request was rejected as malformed |
| `JevRateLimitException` | 429 | rate limited; retries exhausted |
| `JevOverloadedException` | 529 | service overloaded; retries exhausted |

`429` and `529` are retried automatically with exponential backoff and jitter, honoring
a `Retry-After` header given in seconds; each wait is capped at 30 seconds.
`maxRetries` is configurable on the builder. If the calling thread is interrupted
during backoff, the call stops and throws `JevException` with the interrupt flag
restored.

## Configuration

```java
var client = TypeSafeClient.builder()
    .apiKey("sk-...")                      // or omit to read TYPESAFE_API_KEY
    .model("jev-latest")                   // tracks the recommended model
    .timeout(Duration.ofSeconds(30))
    .maxRetries(3)
    .baseUrl("https://api.typesafe.ai")    // e.g. a proxy
    .transport(customTransport)            // inject an HttpTransport to test offline
    .build();
```

State may be a `String`, or any tree of `Map` / `List` / `String` / `Number` /
`Boolean` / `null`; a `Map` gives exact control over field names. Keep the API key
server-side. The client is thread-safe: build one and reuse it.

## Roadmap

- Publish to Maven Central under `io.github.cmaintz:jev`.
- Response caching for repeated states; a live end-to-end sample against a real key.

## License

MIT. See [LICENSE](LICENSE).
