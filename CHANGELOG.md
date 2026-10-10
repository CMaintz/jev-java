# Changelog

All notable changes to this project are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **v0.1:** `TypeSafeClient` for TypeSafe AI's Jev System One model. `systemOne(state, questions)` sends a state plus typed questions and returns typed answers with calibrated confidence; `systemOneAsync` returns a `CompletableFuture` that never holds a thread while waiting (it uses `HttpClient.sendAsync` and schedules retry backoff on a delayed executor). `HttpTransport` has a default `sendAsync` that runs a blocking `send` on a virtual thread for custom transports.
- Three question primitives as a sealed `Question` hierarchy: `Choice` (map criteria, 1 to 255 options, sent in order), `Score` (2 to 10 ordered levels), `Noul` (yes/no), validated on construction.
- A matching sealed `Answer` hierarchy (`ChoiceAnswer`, `ScoreAnswer`, `NoulAnswer`), shaped by the question asked under each id. Typed accessors `SystemOneResponse.choice/score/noul(id)`, type-filtered views `choices()` / `scores()` / `nouls()`, and gating helpers `CalibratedAnswer.isConfident(threshold)` (Choice and Score) / `NoulAnswer.isTrue(threshold)`.
- Zero runtime dependencies (`java.net.http` plus a package-private JSON reader/writer). API key from `TYPESAFE_API_KEY` or the builder; injectable `HttpTransport` seam for offline tests.
- Automatic retry on `429` / `529` with exponential backoff, jitter, and `Retry-After` support, capped at 30 seconds per wait. An interrupt during backoff stops retrying and restores the interrupt flag. Typed exceptions for `401` / `422` / `429` / `529`; network errors and malformed responses also surface as `JevException`.
- `JevThresholds.load(Path)` / `parse(String)` read a jev-eval `thresholds.json` (contract version 1). `pick(questions, model)` returns a `ThresholdGate` (threshold plus measured accuracy, coverage, n, and model) using the same rule as jev-sort: a lone Choice/Score question's own gate, else the composite row gate. A model change or a reworded question is reported on `warnings()`. `ThresholdGate.shouldEscalate(response)` gates on the lowest Choice/Score confidence and escalates (never throws) when a gated answer or its confidence is missing.
- Foundry Java gate: Spotless format check, `compileJava`, `javadoc` with doclint warnings as errors, JUnit 5 tests with an 80% jacoco floor, osv-scanner dependency audit.

### Roadmap

- Publish to Maven Central under `io.github.cmaintz:jev`; response caching; a live-key end-to-end sample.
