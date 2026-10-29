package dev.agentforge.invoices.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.agentforge.core.ai.OpenAiModels;
import dev.agentforge.core.eval.EvalSupport;
import dev.agentforge.invoices.agents.CategorizerAgent;
import dev.agentforge.invoices.agents.ExtractorAgent;
import dev.agentforge.invoices.eval.InvoiceScorer.ExpectedInvoice;
import dev.agentforge.invoices.eval.InvoiceScorer.ExpectedLine;
import dev.agentforge.invoices.eval.InvoiceScorer.RunScore;
import dev.agentforge.invoices.extract.FileType;
import dev.agentforge.invoices.pipeline.InvoicePipeline;
import dev.agentforge.invoices.pipeline.ProcessedInvoice;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Runs the real invoice pipeline (real OpenAI calls, text and vision) on the sample invoices several
 * times and scores every run against samples/eval/invoices-key.json.
 *
 * <pre>
 * mvn -Peval test -pl invoice-processor              # 3 runs per invoice
 * mvn -Peval test -pl invoice-processor -Deval.runs=5
 * </pre>
 *
 * The pipeline only, with no Spring context or database: duplicate checks don't get in the way of
 * processing the same file many times.
 */
@Tag("eval")
class InvoiceEval {

    @Test
    void scorePipelineAgainstAnswerKey() throws Exception {
        int runs = EvalSupport.intProperty("eval.runs", 3);
        String model = EvalSupport.setting("OPENAI_MODEL", "gpt-4o-mini");
        InvoicePipeline pipeline = buildPipeline(EvalSupport.requiredSetting("OPENAI_API_KEY"), model);

        Path root = EvalSupport.repoRoot();
        ObjectMapper json = new ObjectMapper();
        JsonNode key = json.readTree(root.resolve("samples/eval/invoices-key.json").toFile());

        StringBuilder report = new StringBuilder("# Invoice processor eval\n\n")
                .append("- Date: ").append(LocalDateTime.now().withNano(0)).append('\n')
                .append("- Model: `").append(model).append("` (text and vision)\n")
                .append("- Runs per invoice: ").append(runs).append("\n\n")
                .append("A **misread** is any value that differs from what's printed. It is **caught** when the checks flag ")
                .append("something beyond the invoice's real (planted) errors, so a human looks at it; it is **silent** when ")
                .append("nothing flags it, and wrong data would be booked.\n\n")
                .append("| Invoice | Values read correctly | Categories correct | Status correct | Planted error found | Runs with misreads | Caught | **Silent** | Avg time |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        StringBuilder details = new StringBuilder();
        long totalSilent = 0;

        for (JsonNode sample : key.path("samples")) {
            String file = sample.path("file").asText();
            byte[] bytes = Files.readAllBytes(root.resolve(file));
            FileType type = FileType.detect(bytes).orElseThrow();
            ExpectedInvoice expected = new ExpectedInvoice(
                    json.convertValue(sample.path("fields"), new TypeReference<Map<String, String>>() { }),
                    json.convertValue(sample.path("lines"), new TypeReference<List<ExpectedLine>>() { }),
                    sample.path("expectedStatus").asText(),
                    json.convertValue(sample.path("expectedErrors"), new TypeReference<List<String>>() { }));

            List<RunScore> scores = new ArrayList<>();
            long totalMillis = 0;
            for (int run = 1; run <= runs; run++) {
                System.out.printf("[eval] %s, run %d/%d%n", file, run, runs);
                ProcessedInvoice result = pipeline.process(Path.of(file).getFileName().toString(), bytes, type);
                scores.add(InvoiceScorer.score(result, expected));
                totalMillis += result.totalMillis();
            }

            long valuesChecked = scores.stream().mapToLong(RunScore::valuesChecked).sum();
            long valuesWrong = scores.stream().mapToLong(s -> s.wrongValues().size()).sum();
            long categoriesChecked = scores.stream().mapToLong(RunScore::categoriesChecked).sum();
            long categoriesWrong = scores.stream().mapToLong(s -> s.wrongCategories().size()).sum();
            long misreads = scores.stream().filter(RunScore::misread).count();
            long silent = scores.stream().filter(RunScore::silent).count();
            totalSilent += silent;
            String name = Path.of(file).getFileName().toString();
            report.append("| `").append(name).append("` | ")
                    .append(EvalSupport.percent(valuesChecked - valuesWrong, valuesChecked)).append(" | ")
                    .append(EvalSupport.percent(categoriesChecked - categoriesWrong, categoriesChecked)).append(" | ")
                    .append(scores.stream().filter(RunScore::statusCorrect).count()).append('/').append(runs).append(" | ")
                    .append(expected.errors().isEmpty() ? "n/a" : scores.stream().filter(RunScore::plantedErrorsFound).count() + "/" + runs).append(" | ")
                    .append(misreads).append('/').append(runs).append(" | ")
                    .append(misreads - silent).append('/').append(misreads).append(" | ")
                    .append(silent == 0 ? "0" : "**" + silent + " ❌**").append(" | ")
                    .append(totalMillis / runs).append(" ms |\n");

            Map<String, Integer> wrongCounts = new TreeMap<>();
            scores.forEach(s -> s.wrongValues().forEach(w -> wrongCounts.merge(w, 1, Integer::sum)));
            scores.forEach(s -> s.wrongCategories().forEach(w -> wrongCounts.merge(w, 1, Integer::sum)));
            if (!wrongCounts.isEmpty()) {
                details.append("### `").append(name).append("`\n\n");
                wrongCounts.forEach((w, n) -> details.append("- ").append(w).append(" (").append(n).append('/').append(runs).append(" runs)\n"));
                details.append('\n');
            }
        }

        report.append("\n**Silent misreads across all runs: ").append(totalSilent).append("**\n\n");
        if (!details.isEmpty()) {
            report.append("## What was read wrong\n\n").append(details);
        }
        Path written = EvalSupport.writeReport("invoices", report.toString());
        System.out.println(report);
        System.out.println("[eval] Report written to " + written);
    }

    /** Same agents and settings as the app (application.yml), built without Spring. */
    private static InvoicePipeline buildPipeline(String apiKey, String model) {
        ChatModel chat = OpenAiModels.structuredOutput(apiKey, model, 0.0);
        return new InvoicePipeline(AiServices.create(ExtractorAgent.class, chat),
                AiServices.create(CategorizerAgent.class, chat), new ObjectMapper(), 3);
    }
}
