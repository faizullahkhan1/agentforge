package dev.agentforge.prreviewer.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.agentforge.core.ai.OpenAiModels;
import dev.agentforge.core.eval.EvalSupport;
import dev.agentforge.prreviewer.eval.ReviewScorer.Expected;
import dev.agentforge.prreviewer.eval.ReviewScorer.RunScore;
import dev.agentforge.prreviewer.review.ReviewFinding;
import dev.agentforge.prreviewer.review.ReviewReport;
import dev.agentforge.prreviewer.review.ReviewService;
import dev.agentforge.prreviewer.review.Severity;
import dev.agentforge.prreviewer.review.agents.BugReviewAgent;
import dev.agentforge.prreviewer.review.agents.GeneralistReviewAgent;
import dev.agentforge.prreviewer.review.agents.JudgeAgent;
import dev.agentforge.prreviewer.review.agents.PerformanceReviewAgent;
import dev.agentforge.prreviewer.review.agents.SecurityReviewAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Runs the real reviewer (real OpenAI calls) on samples with known problems, several times,
 * and scores every run against samples/eval/pr-reviewer-key.json.
 *
 * <pre>
 * mvn -Peval test -pl pr-reviewer                                   # 3 runs, team mode
 * mvn -Peval test -pl pr-reviewer -Deval.runs=5 -Deval.modes=team,team-nojudge,single
 * </pre>
 *
 * No Spring context and no database: the agents are built directly, with the same models and
 * settings as application.yml. It reports; it doesn't fail on low scores (LLM output varies,
 * so a hard threshold would make the build flaky). Compare reports over time instead.
 */
@Tag("eval")
class ReviewEval {

    private static final List<String> MODES = List.of("team", "team-nojudge", "single");

    @Test
    void scoreReviewerAgainstAnswerKey() throws Exception {
        int runs = EvalSupport.intProperty("eval.runs", 3);
        List<String> modes = Arrays.stream(System.getProperty("eval.modes", "team").split(","))
                .map(String::strip).toList();
        modes.forEach(m -> {
            if (!MODES.contains(m)) {
                throw new IllegalArgumentException("Unknown mode " + m + ", use " + MODES);
            }
        });

        String model = EvalSupport.setting("OPENAI_MODEL", "gpt-4o-mini");
        String judgeModel = EvalSupport.setting("OPENAI_JUDGE_MODEL", "gpt-4.1");
        ReviewService reviewer = buildReviewer(EvalSupport.requiredSetting("OPENAI_API_KEY"), model, judgeModel);

        Path root = EvalSupport.repoRoot();
        ObjectMapper json = new ObjectMapper();
        JsonNode key = json.readTree(root.resolve("samples/eval/pr-reviewer-key.json").toFile());

        StringBuilder report = new StringBuilder("# PR reviewer eval\n\n")
                .append("- Date: ").append(LocalDateTime.now().withNano(0)).append('\n')
                .append("- Models: specialists `").append(model).append("`, judge `").append(judgeModel).append("`\n")
                .append("- Runs per sample and mode: ").append(runs).append("\n\n");

        for (JsonNode sample : key.path("samples")) {
            String file = sample.path("file").asText();
            String code = Files.readString(root.resolve(file));
            List<Expected> expected = new ArrayList<>();
            for (JsonNode e : sample.path("expected")) {
                expected.add(new Expected(e.path("id").asText(),
                        List.of(e.path("lines").get(0).asInt(), e.path("lines").get(1).asInt()),
                        json.convertValue(e.path("keywords"), json.getTypeFactory().constructCollectionType(List.class, String.class))));
            }

            for (String mode : modes) {
                List<RunScore> scores = new ArrayList<>();
                long totalMillis = 0;
                int totalFindings = 0;
                int agentErrors = 0;
                for (int run = 1; run <= runs; run++) {
                    System.out.printf("[eval] %s, %s, run %d/%d%n", file, mode, run, runs);
                    ReviewReport result = switch (mode) {
                        case "single" -> reviewer.reviewWithGeneralist(sample.path("language").asText(), code);
                        case "team-nojudge" -> reviewer.reviewWithTeam(sample.path("language").asText(), code, false);
                        default -> reviewer.reviewWithTeam(sample.path("language").asText(), code, true);
                    };
                    scores.add(ReviewScorer.score(result.findings(), expected));
                    totalMillis += result.totalMillis();
                    totalFindings += result.findings().size();
                    agentErrors += (int) result.agents().stream().filter(a -> a.error() != null).count();
                }
                report.append(section(file, mode, expected, scores, runs, totalFindings, totalMillis, agentErrors));
            }
        }

        Path written = EvalSupport.writeReport("pr-reviewer", report.toString());
        System.out.println(report);
        System.out.println("[eval] Report written to " + written);
    }

    private static String section(String file, String mode, List<Expected> expected, List<RunScore> scores, int runs,
                                  int totalFindings, long totalMillis, int agentErrors) {
        StringBuilder s = new StringBuilder("## `").append(file).append("`, mode `").append(mode).append("`\n\n");

        if (!expected.isEmpty()) {
            s.append("| Known problem | Found in |\n|---|---|\n");
            long hits = 0;
            for (Expected e : expected) {
                long found = scores.stream().filter(r -> r.found().contains(e.id())).count();
                hits += found;
                s.append("| ").append(e.id()).append(" | ").append(found).append("/").append(runs)
                        .append(found == runs ? " ✅" : found == 0 ? " ❌" : " ⚠️").append(" |\n");
            }
            s.append("\n**Recall: ").append(EvalSupport.percent(hits, (long) expected.size() * runs))
                    .append("** of known problems found, averaged over runs.\n\n");
        }

        // Unexpected findings, grouped by line + category, because titles are worded differently every run
        Map<String, Integer> grouped = new TreeMap<>();
        long severeUnexpected = 0;
        for (RunScore r : scores) {
            for (ReviewFinding f : r.unexpected()) {
                grouped.merge("line %3d %s %s: %s".formatted(f.line(), f.severity(), f.category(), f.title()), 1, Integer::sum);
                if (f.severity().compareTo(Severity.MEDIUM) <= 0) {
                    severeUnexpected++;
                }
            }
        }
        long unexpectedTotal = scores.stream().mapToLong(r -> r.unexpected().size()).sum();
        s.append("- Findings per run: ").append(String.format("%.1f", (double) totalFindings / runs)).append('\n')
                .append("- Not in the answer key: ").append(String.format("%.1f", (double) unexpectedTotal / runs))
                .append(" per run (").append(String.format("%.1f", (double) severeUnexpected / runs)).append(" of them MEDIUM or worse)")
                .append(expected.isEmpty() ? ": **this code has no known problems, so these are false positives**" : "")
                .append('\n')
                .append("- Average time: ").append(totalMillis / runs).append(" ms\n");
        if (agentErrors > 0) {
            s.append("- ⚠️ Agent errors: ").append(agentErrors).append('\n');
        }
        if (!grouped.isEmpty()) {
            s.append("\n<details><summary>Findings not in the key (check by hand: real issue the key missed, or false positive?)</summary>\n\n");
            Map<String, Integer> ordered = new LinkedHashMap<>(grouped);
            ordered.forEach((k, v) -> s.append("- `").append(k).append("` (").append(v).append("/").append(runs).append(" runs)\n"));
            s.append("\n</details>\n");
        }
        return s.append('\n').toString();
    }

    /** Same agents and settings as the app (application.yml + JudgeConfig), built without Spring. */
    private static ReviewService buildReviewer(String apiKey, String model, String judgeModel) {
        ChatModel specialists = OpenAiModels.structuredOutput(apiKey, model, 0.2);
        ChatModel judge = OpenAiModels.structuredOutput(apiKey, judgeModel, 0.0);
        return new ReviewService(
                AiServices.create(GeneralistReviewAgent.class, specialists),
                AiServices.create(SecurityReviewAgent.class, specialists),
                AiServices.create(PerformanceReviewAgent.class, specialists),
                AiServices.create(BugReviewAgent.class, specialists),
                AiServices.create(JudgeAgent.class, judge),
                judgeModel,
                new ObjectMapper());
    }
}
