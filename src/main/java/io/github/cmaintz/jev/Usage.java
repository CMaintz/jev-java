package io.github.cmaintz.jev;

/**
 * Token accounting for a single request.
 *
 * @param inputTokens tokens consumed by the state and questions
 * @param outputTokens tokens produced across all answers
 */
public record Usage(int inputTokens, int outputTokens) {}
