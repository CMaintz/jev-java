package io.github.cmaintz.jev;

/**
 * One typed judgment returned by Jev, mirroring the {@link Question} that asked for it:
 * a {@link Choice} yields a {@link ChoiceAnswer}, a {@link Score} a {@link ScoreAnswer},
 * and a {@link Noul} a {@link NoulAnswer}. Choice and Score answers are also
 * {@link CalibratedAnswer}s, carrying a confidence. Switch over it exhaustively, or use
 * the typed accessors on {@link SystemOneResponse}.
 */
public sealed interface Answer permits CalibratedAnswer, NoulAnswer {}
