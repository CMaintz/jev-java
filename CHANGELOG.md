# Changelog

All notable changes to this project are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **v0.1:** `TypeSafeClient` for TypeSafe AI's Jev System One model. `systemOne(state, questions)` sends a state plus typed questions and returns typed answers with calibrated confidence; `systemOneAsync` returns a `CompletableFuture`.
- Three question primitives as a sealed `Question` hierarchy: `Choice` (map criteria), `Score` (2 to 10 ordered levels), `Noul` (yes/no). Hand-written JSON codec so the `criteria` map/array and `probabilities` map/array shapes are handled exactly.
- Confidence helper `Answer.isConfident(threshold)`; type-filtered views `SystemOneResponse.choices()` / `scores()` / `nouls()`.
- Zero runtime dependencies (`java.net.http` plus an internal JSON reader/writer). API key from `TYPESAFE_API_KEY` or the builder; injectable `HttpTransport` seam for offline tests (20 tests).
- Automatic retry with exponential backoff on `429` / `529`; typed exceptions for `401` / `422` / `429` / `529`.
- Foundry Java gate: Spotless format check, `compileJava`, JUnit 5 tests with an 80% jacoco floor, osv-scanner dependency audit.

### Roadmap

- Publish to Maven Central under `io.github.cmaintz:jev`; response caching; a live-key end-to-end sample.
