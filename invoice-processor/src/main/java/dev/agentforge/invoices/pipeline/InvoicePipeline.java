package dev.agentforge.invoices.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.agentforge.invoices.agents.CategorizerAgent;
import dev.agentforge.invoices.agents.ExtractorAgent;
import dev.agentforge.invoices.extract.PdfText;
import dev.agentforge.invoices.model.Categorization;
import dev.agentforge.invoices.model.Categorization.LineCategory;
import dev.agentforge.invoices.model.ExpenseCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.pipeline.ProcessedInvoice.Status;
import dev.agentforge.invoices.pipeline.ProcessedInvoice.Step;
import dev.agentforge.invoices.validate.InvoiceValidator;
import dev.agentforge.invoices.validate.InvoiceValidator.Issue;
import dev.agentforge.invoices.validate.InvoiceValidator.Level;

/**
 * A SEQUENTIAL pipeline: read PDF -> extract (AI) -> validate (Java) -> categorize (AI).
 * Unlike the PR reviewer's parallel agents, each step here needs the previous step's output.
 */
@Service
public class InvoicePipeline {

    private final ExtractorAgent extractor;
    private final CategorizerAgent categorizer;
    private final ObjectMapper objectMapper;

    public InvoicePipeline(ExtractorAgent extractor, CategorizerAgent categorizer, ObjectMapper objectMapper) {
        this.extractor = extractor;
        this.categorizer = categorizer;
        this.objectMapper = objectMapper;
    }

    public ProcessedInvoice process(String fileName, byte[] pdf) {
        long start = System.nanoTime();
        List<Step> steps = new ArrayList<>();

        long t = System.nanoTime();
        String text;
        try {
            text = PdfText.extract(pdf);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Not a readable PDF: " + e.getMessage());
        }
        if (!PdfText.hasTextLayer(text)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This PDF has no text layer (probably a scan). Scanned invoices need the vision step, which isn't built yet.");
        }
        steps.add(new Step("read-pdf", false, millisSince(t)));

        t = System.nanoTime();
        Invoice invoice = extractor.extract(text);
        steps.add(new Step("extractor", true, millisSince(t)));

        t = System.nanoTime();
        List<Issue> issues = InvoiceValidator.validate(invoice);
        steps.add(new Step("validator", false, millisSince(t)));

        t = System.nanoTime();
        List<LineCategory> categories = categorize(invoice);
        steps.add(new Step("categorizer", true, millisSince(t)));

        Status status = issues.stream().anyMatch(i -> i.level() == Level.ERROR) ? Status.NEEDS_REVIEW : Status.READY;
        return new ProcessedInvoice(fileName, status, invoice, categories, issues, steps, millisSince(start));
    }

    private List<LineCategory> categorize(Invoice invoice) {
        List<Invoice.LineItem> lines = invoice.lineItems() == null ? List.of() : invoice.lineItems();
        if (lines.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> input = IntStream.range(0, lines.size())
                .mapToObj(i -> Map.<String, Object>of("index", i, "description", lines.get(i).description(),
                        "amount", lines.get(i).amount()))
                .toList();
        Categorization result;
        try {
            result = categorizer.categorize(invoice.vendorName(), objectMapper.writeValueAsString(input));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return alignToLines(result, lines.size());
    }

    /** One category per line, in line order. Lines the model skipped become OTHER instead of disappearing. */
    static List<LineCategory> alignToLines(Categorization result, int lineCount) {
        Map<Integer, LineCategory> byIndex = result == null || result.lines() == null ? Map.of()
                : result.lines().stream().collect(Collectors.toMap(LineCategory::index, Function.identity(), (a, b) -> a));
        return IntStream.range(0, lineCount)
                .mapToObj(i -> byIndex.getOrDefault(i, new LineCategory(i, ExpenseCategory.OTHER, "not categorized by the model")))
                .toList();
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
