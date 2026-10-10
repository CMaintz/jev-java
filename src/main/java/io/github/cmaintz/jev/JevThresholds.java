package io.github.cmaintz.jev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code thresholds.json} written by <a href="https://github.com/CMaintz/jev-eval">jev-eval</a>
 * (contract version 1): confidence gates measured on labeled data. Load it once, then
 * {@link #pick(Map, String)} the gate for the questions you send. Unknown keys are ignored.
 * A malformed file, or questions the file was not measured for, throw
 * {@link IllegalArgumentException}.
 *
 * <pre>{@code
 * ThresholdGate gate = JevThresholds.load(Path.of("thresholds.json")).pick(questions, "jev-latest");
 * gate.warnings().forEach(log::warn);
 * if (gate.shouldEscalate(response)) { ... }
 * }</pre>
 */
public final class JevThresholds {

    private static final String NO_QUESTION_GATE =
            "jev-eval refused it or found no gate meeting the goal; see its report";
    private static final String NO_ROW_GATE = "jev-eval refused or found the row gate unstable; see its report";

    private final String model;
    private final Map<String, Gate> questions;
    private final Gate composite;
    private final List<String> compositeQuestions;
    private final Map<?, ?> definitions;

    private JevThresholds(Map<?, ?> root) {
        this.model = requireString(root, "model");
        this.questions = parseQuestions(requireObject(root, "questions", "thresholds file"));
        Map<?, ?> compositeEntry = optionalObject(root, "composite");
        this.composite = compositeEntry == null ? null : Gate.parse(compositeEntry, "\"composite\"");
        this.compositeQuestions = compositeEntry == null ? List.of() : sortedStrings(compositeEntry.get("questions"));
        this.definitions = optionalObject(root, "definitions");
    }

    /**
     * Parse the text of a {@code thresholds.json}.
     *
     * @param json the file contents
     * @return the parsed thresholds
     * @throws IllegalArgumentException if it is not valid JSON or not a version 1 thresholds file
     */
    public static JevThresholds parse(String json) {
        Objects.requireNonNull(json, "json");
        if (!(JsonParser.parse(json) instanceof Map<?, ?> root)) {
            throw malformed("expected a JSON object");
        }
        if (!(root.get("version") instanceof Double version) || version != 1.0) {
            throw malformed("expected version 1, got " + root.get("version"));
        }
        return new JevThresholds(root);
    }

    /**
     * Read and parse a {@code thresholds.json} (UTF-8).
     *
     * @param path the file
     * @return the parsed thresholds
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if it is not a version 1 thresholds file
     */
    public static JevThresholds load(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /**
     * The model jev-eval measured on.
     *
     * @return the model name, e.g. {@code jev-latest}
     */
    public String model() {
        return model;
    }

    /**
     * The gate for {@code questions}, without the model check; see {@link #pick(Map, String)}.
     *
     * @param questions the questions you send, keyed by id
     * @return the gate with its measured provenance
     */
    public ThresholdGate pick(Map<String, Question> questions) {
        return pick(questions, null);
    }

    /**
     * The gate for {@code questions}. With one Choice/Score question it is that question's
     * own gate; with several it is the row-level {@code composite} gate, which must have been
     * measured over exactly those questions. Nouls are ignored. A model other than the one
     * measured, or a question reworded since it was measured, adds a warning.
     *
     * @param questions the questions you send, keyed by id
     * @param model the model you call, or null to skip the model check
     * @return the gate with its measured provenance and any warnings
     * @throws IllegalArgumentException if the file has no gate for these questions
     */
    public ThresholdGate pick(Map<String, Question> questions, String model) {
        List<String> ids = gatedIds(questions);
        var warnings = new ArrayList<String>();
        if (model != null && !model.equals(this.model)) {
            warnings.add("thresholds were measured on " + this.model + " but this run uses " + model
                    + "; re-measure after a model change");
        }
        ids.forEach(id -> rewordWarning(id, questions.get(id)).ifPresent(warnings::add));
        String source = ids.size() == 1 ? ids.get(0) : ThresholdGate.COMPOSITE;
        return gateFor(ids).toThresholdGate(source, ids, this.model, warnings);
    }

    private Gate gateFor(List<String> ids) {
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("thresholds: nothing to gate on (every question is a noul)");
        }
        if (ids.size() == 1) {
            return questionGate(ids.get(0));
        }
        if (composite == null) {
            throw new IllegalArgumentException(
                    "thresholds file has no composite gate for " + ids + " (" + NO_ROW_GATE + ")");
        }
        if (!compositeQuestions.equals(ids)) {
            throw new IllegalArgumentException("thresholds file gates " + compositeQuestions + " but this run gates "
                    + ids + "; re-run jev-eval thresholds with these questions");
        }
        return composite;
    }

    private Gate questionGate(String id) {
        Gate gate = questions.get(id);
        if (gate == null) {
            throw new IllegalArgumentException(
                    "thresholds file has no gate for \"" + id + "\" (" + NO_QUESTION_GATE + ")");
        }
        return gate;
    }

    private Optional<String> rewordWarning(String id, Question question) {
        if (definitions == null || !definitions.containsKey(id)) {
            return Optional.empty();
        }
        if (RequestEncoder.questionMap(question).equals(definitions.get(id))) {
            return Optional.empty();
        }
        return Optional.of(id + " was reworded since it was measured; re-measure");
    }

    /** The ids of the Choice and Score questions, sorted; a Noul carries no gate confidence. */
    private static List<String> gatedIds(Map<String, Question> questions) {
        Objects.requireNonNull(questions, "questions");
        return questions.entrySet().stream()
                .filter(e -> !(e.getValue() instanceof Noul))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static Map<String, Gate> parseQuestions(Map<?, ?> entries) {
        var out = new LinkedHashMap<String, Gate>();
        entries.forEach((id, entry) -> {
            if (!(entry instanceof Map<?, ?> map)) {
                throw malformed("question \"" + id + "\" is not an object");
            }
            out.put(String.valueOf(id), Gate.parse(map, "question \"" + id + "\""));
        });
        return Collections.unmodifiableMap(out);
    }

    private static List<String> sortedStrings(Object value) {
        if (!(value instanceof List<?> list) || !list.stream().allMatch(String.class::isInstance)) {
            throw malformed("\"composite\".questions must be a list of question ids");
        }
        return list.stream().map(String.class::cast).sorted().toList();
    }

    private static String requireString(Map<?, ?> map, String key) {
        if (map.get(key) instanceof String s) {
            return s;
        }
        throw malformed("missing \"" + key + "\"");
    }

    private static Map<?, ?> requireObject(Map<?, ?> map, String key, String context) {
        if (map.get(key) instanceof Map<?, ?> m) {
            return m;
        }
        throw malformed(context + " is missing object \"" + key + "\"");
    }

    private static Map<?, ?> optionalObject(Map<?, ?> map, String key) {
        return map.get(key) == null ? null : requireObject(map, key, "thresholds file");
    }

    static IllegalArgumentException malformed(String message) {
        return new IllegalArgumentException("thresholds file: " + message);
    }

    /** One measured gate: the cut-point and what it bought on n rows. */
    record Gate(double threshold, double accuracy, double coverage, int n) {

        static Gate parse(Map<?, ?> map, String context) {
            double n = number(map, "n", context);
            if (n != Math.rint(n) || n < 0) {
                throw malformed(context + " has a non-integer \"n\"");
            }
            return new Gate(
                    number(map, "threshold", context),
                    number(map, "accuracy", context),
                    number(map, "coverage", context),
                    (int) n);
        }

        private static double number(Map<?, ?> map, String key, String context) {
            if (map.get(key) instanceof Double d) {
                return d;
            }
            throw malformed(context + " is missing number \"" + key + "\"");
        }

        ThresholdGate toThresholdGate(String source, List<String> ids, String model, List<String> warnings) {
            return new ThresholdGate(threshold, accuracy, coverage, n, source, ids, model, warnings);
        }
    }
}
