package dev.agentforge.prreviewer.review;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.agentforge.prreviewer.review.ReviewReport.AgentRun;
import dev.agentforge.prreviewer.review.ReviewReport.DroppedFinding;
import dev.agentforge.prreviewer.review.agents.BugReviewAgent;
import dev.agentforge.prreviewer.review.agents.GeneralistReviewAgent;
import dev.agentforge.prreviewer.review.agents.JudgeAgent;
import dev.agentforge.prreviewer.review.agents.PerformanceReviewAgent;
import dev.agentforge.prreviewer.review.agents.SecurityReviewAgent;

/**
 * The orchestrator: sends the code to several agents at the same time, merges their answers,
 * and lets a judge agent filter them. Everything except the agent calls is plain Java,
 * so it is fast, free and testable.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** Any agent with this shape can join a review. The @AiService interfaces fit via method references. */
    @FunctionalInterface
    interface Reviewer {
        ReviewResult review(String language, String numberedCode);
    }

    record NamedReviewer(String name, Reviewer reviewer) {
    }

    /** A finding plus who found it. The id is what the judge refers to in its verdicts. */
    record Candidate(int id, String foundBy, ReviewFinding finding) {
    }

    record Judgement(List<ReviewFinding> findings, List<DroppedFinding> dropped, int added) {
    }

    private record Outcome(AgentRun run, List<ReviewFinding> findings) {
    }

    private final List<NamedReviewer> generalist;
    private final List<NamedReviewer> team;
    private final JudgeAgent judge;
    private final String judgeName;
    private final ObjectMapper objectMapper;

    public ReviewService(GeneralistReviewAgent generalist,
                         SecurityReviewAgent security,
                         PerformanceReviewAgent performance,
                         BugReviewAgent bugs,
                         JudgeAgent judge,
                         @Value("${agentforge.judge.model-name}") String judgeModelName,
                         ObjectMapper objectMapper) {
        this.generalist = List.of(new NamedReviewer("generalist", generalist::review));
        this.team = List.of(
                new NamedReviewer("security", security::review),
                new NamedReviewer("performance", performance::review),
                new NamedReviewer("bugs", bugs::review));
        this.judge = judge;
        this.judgeName = "judge (" + judgeModelName + ")";
        this.objectMapper = objectMapper;
    }

    public ReviewReport reviewWithTeam(String language, String code, boolean useJudge) {
        return review(team, useJudge, language, code);
    }

    public ReviewReport reviewWithGeneralist(String language, String code) {
        return review(generalist, false, language, code);
    }

    private ReviewReport review(List<NamedReviewer> reviewers, boolean useJudge, String language, String code) {
        long start = System.nanoTime();
        String numbered = numberLines(code);
        String[] lines = code.split("\\R", -1);

        // Step 1: one virtual thread per agent, all LLM calls in flight at once,
        // so this takes roughly as long as the slowest agent, not the sum of all of them.
        List<Outcome> outcomes;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Outcome>> futures = reviewers.stream()
                    .map(r -> CompletableFuture.supplyAsync(() -> runAgent(r, language, numbered), executor))
                    .toList();
            outcomes = futures.stream().map(CompletableFuture::join).toList();
        }
        List<AgentRun> runs = new ArrayList<>(outcomes.stream().map(Outcome::run).toList());

        // Step 2: merge, fix line numbers, drop exact duplicates, number them for the judge
        List<Candidate> candidates = toCandidates(outcomes, lines);

        // Step 3: the judge (runs after the team, because it needs all their findings)
        Judgement judgement;
        if (useJudge && !candidates.isEmpty()) {
            long judgeStart = System.nanoTime();
            String error = null;
            try {
                JudgeResult result = judge.judge(language, numbered, objectMapper.writeValueAsString(candidates));
                judgement = applyVerdicts(candidates, result, lines);
            } catch (JsonProcessingException | RuntimeException e) {
                // If the judge fails, fall back to the unfiltered findings instead of failing the review
                log.warn("Judge failed, returning unfiltered findings", e);
                error = e.getMessage();
                judgement = new Judgement(candidates.stream().map(Candidate::finding).toList(), List.of(), 0);
            }
            runs.add(new AgentRun(judgeName, judgement.findings().size(), millisSince(judgeStart), error));
        } else {
            judgement = new Judgement(candidates.stream().map(Candidate::finding).toList(), List.of(), 0);
        }

        List<ReviewFinding> findings = judgement.findings().stream()
                .sorted(Comparator.comparing(ReviewFinding::severity).thenComparingInt(ReviewFinding::line))
                .toList();
        String summary = summarize(findings, runs.size());
        if (useJudge && !candidates.isEmpty()) {
            summary += " Judge dropped " + judgement.dropped().size() + " of " + candidates.size()
                    + " candidates and added " + judgement.added() + ".";
        }
        return new ReviewReport(summary, findings, judgement.dropped(), runs, millisSince(start));
    }

    private Outcome runAgent(NamedReviewer reviewer, String language, String numberedCode) {
        long start = System.nanoTime();
        try {
            ReviewResult result = reviewer.reviewer().review(language, numberedCode);
            List<ReviewFinding> findings = result.findings() == null ? List.of() : result.findings();
            return new Outcome(new AgentRun(reviewer.name(), findings.size(), millisSince(start), null), findings);
        } catch (RuntimeException e) {
            // One failing agent should not sink the whole review
            log.warn("Agent '{}' failed", reviewer.name(), e);
            return new Outcome(new AgentRun(reviewer.name(), 0, millisSince(start), e.getMessage()), List.of());
        }
    }

    private static List<Candidate> toCandidates(List<Outcome> outcomes, String[] lines) {
        List<Candidate> unnumbered = outcomes.stream()
                .flatMap(o -> o.findings().stream().map(f -> new Candidate(0, o.run().agent(), fixLine(f, lines))))
                .toList();
        List<Candidate> unique = dedupe(unnumbered, Candidate::finding);
        List<Candidate> numbered = new ArrayList<>();
        for (int i = 0; i < unique.size(); i++) {
            numbered.add(new Candidate(i + 1, unique.get(i).foundBy(), unique.get(i).finding()));
        }
        return numbered;
    }

    /**
     * Turns the judge's verdicts into the final list. Java stays in charge: a candidate the judge
     * forgot to mention is kept, and the judge's extra findings get the same line check as everyone else's.
     */
    static Judgement applyVerdicts(List<Candidate> candidates, JudgeResult result, String[] lines) {
        Map<Integer, JudgeResult.Verdict> verdictById = result.verdicts() == null ? Map.of()
                : result.verdicts().stream().collect(Collectors.toMap(JudgeResult.Verdict::id, v -> v, (a, b) -> a));

        List<ReviewFinding> kept = new ArrayList<>();
        List<DroppedFinding> dropped = new ArrayList<>();
        for (Candidate c : candidates) {
            JudgeResult.Verdict verdict = verdictById.get(c.id());
            if (verdict == null) {
                kept.add(c.finding());
            } else if (verdict.keep()) {
                kept.add(verdict.severity() == null ? c.finding() : c.finding().withSeverity(verdict.severity()));
            } else {
                dropped.add(new DroppedFinding(c.foundBy(), c.finding().line(), c.finding().title(), verdict.reason()));
            }
        }

        List<ReviewFinding> missed = result.missed() == null ? List.of()
                : result.missed().stream().map(f -> fixLine(f, lines)).toList();
        kept.addAll(missed);
        List<ReviewFinding> unique = dedupe(kept, Function.identity());
        int added = unique.size() - (kept.size() - missed.size());
        return new Judgement(unique, dropped, Math.max(added, 0));
    }

    /** "a\nb" -> "1: a\n2: b\n". LLMs are bad at counting lines, so we number them ourselves. */
    static String numberLines(String code) {
        String[] lines = code.split("\\R", -1);
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            numbered.append(i + 1).append(": ").append(lines[i]).append('\n');
        }
        return numbered.toString();
    }

    /**
     * Models are often off by a line or two. The finding also quotes the code it is about,
     * so if the reported line doesn't contain that code, look up to 3 lines around it.
     */
    static ReviewFinding fixLine(ReviewFinding finding, String[] lines) {
        String quoted = firstLine(finding.code()).replaceFirst("^\\d+:\\s?", "").strip();
        if (quoted.isEmpty() || containsAt(lines, finding.line(), quoted)) {
            return finding;
        }
        for (int distance = 1; distance <= 3; distance++) {
            if (containsAt(lines, finding.line() - distance, quoted)) {
                return finding.withLine(finding.line() - distance);
            }
            if (containsAt(lines, finding.line() + distance, quoted)) {
                return finding.withLine(finding.line() + distance);
            }
        }
        return finding;
    }

    /** Two agents may report the same problem: keep one per line + category, the most severe. */
    static <T> List<T> dedupe(List<T> items, Function<T, ReviewFinding> findingOf) {
        Map<String, T> byKey = new LinkedHashMap<>();
        for (T item : items) {
            ReviewFinding f = findingOf.apply(item);
            byKey.merge(f.line() + ":" + f.category(), item,
                    (a, b) -> findingOf.apply(a).severity().compareTo(findingOf.apply(b).severity()) <= 0 ? a : b);
        }
        return List.copyOf(byKey.values());
    }

    private static String summarize(List<ReviewFinding> findings, int agentCount) {
        if (findings.isEmpty()) {
            return "No issues found by " + agentCount + " agent(s).";
        }
        Map<Severity, Long> counts = findings.stream()
                .collect(Collectors.groupingBy(ReviewFinding::severity, () -> new EnumMap<>(Severity.class), Collectors.counting()));
        String breakdown = counts.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey().name().toLowerCase())
                .collect(Collectors.joining(", "));
        return findings.size() + " issue(s) found by " + agentCount + " agent(s): " + breakdown + ".";
    }

    private static boolean containsAt(String[] lines, int lineNumber, String quoted) {
        return lineNumber >= 1 && lineNumber <= lines.length && lines[lineNumber - 1].contains(quoted);
    }

    private static String firstLine(String text) {
        return text == null ? "" : Arrays.stream(text.split("\\R")).filter(s -> !s.isBlank()).findFirst().orElse("");
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
