package dev.agentforge.invoices.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.agentforge.core.run.Steps;
import dev.agentforge.core.run.Steps.Step;
import dev.agentforge.invoices.agents.CategorizerAgent;
import dev.agentforge.invoices.agents.ExtractorAgent;
import dev.agentforge.invoices.extract.FileType;
import dev.agentforge.invoices.extract.PageImages;
import dev.agentforge.invoices.extract.PdfText;
import dev.agentforge.invoices.model.Categorization;
import dev.agentforge.invoices.model.Categorization.LineCategory;
import dev.agentforge.invoices.model.ExpenseCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.pipeline.ProcessedInvoice.ExtractedFrom;
import dev.agentforge.invoices.pipeline.ProcessedInvoice.Status;
import dev.agentforge.invoices.validate.InvoiceValidator;
import dev.agentforge.invoices.validate.InvoiceValidator.Issue;
import dev.agentforge.invoices.validate.InvoiceValidator.Level;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.TextContent;

/**
 * A SEQUENTIAL pipeline: read PDF -> extract (AI) -> validate (Java) -> categorize (AI).
 * Unlike the PR reviewer's parallel agents, each step here needs the previous step's output.
 */
@Service
public class InvoicePipeline {

    private final ExtractorAgent extractor;
    private final CategorizerAgent categorizer;
    private final ObjectMapper objectMapper;
    private final int maxPages;

    public InvoicePipeline(ExtractorAgent extractor, CategorizerAgent categorizer, ObjectMapper objectMapper,
                           @Value("${invoices.vision.max-pages}") int maxPages) {
        this.extractor = extractor;
        this.categorizer = categorizer;
        this.objectMapper = objectMapper;
        this.maxPages = maxPages;
    }

    public ProcessedInvoice process(String fileName, byte[] bytes, FileType type) {
        long start = System.nanoTime();
        List<Step> steps = new ArrayList<>();

        // Step 1: extraction. Text is cheaper and more exact, so use it whenever the PDF has a text layer;
        // scans, photos and image files go to the vision model instead.
        Invoice invoice;
        ExtractedFrom extractedFrom;
        try {
            if (type == FileType.PDF) {
                String text = Steps.time(steps, "read-pdf", false, () -> PdfText.extract(bytes));
                if (PdfText.hasTextLayer(text)) {
                    invoice = Steps.time(steps, "extractor (text)", true, () -> extractor.extract(text));
                    extractedFrom = ExtractedFrom.TEXT;
                } else {
                    int pageCount = Math.min(PageImages.pageCount(bytes), maxPages);
                    List<byte[]> pages = Steps.time(steps, "render-pages (" + pageCount + ")", false,
                            () -> PageImages.render(bytes, maxPages));
                    invoice = extractFromImages(pages, FileType.PNG, steps);
                    extractedFrom = ExtractedFrom.VISION;
                }
            } else {
                invoice = extractFromImages(List.of(bytes), type, steps);
                extractedFrom = ExtractedFrom.VISION;
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Not a readable PDF: " + e.getMessage());
        }

        Invoice extracted = invoice;
        List<Issue> issues = Steps.time(steps, "validator", false, () -> InvoiceValidator.validate(extracted));
        List<LineCategory> categories = Steps.time(steps, "categorizer", true, () -> categorize(extracted));

        Status status = issues.stream().anyMatch(i -> i.level() == Level.ERROR) ? Status.NEEDS_REVIEW : Status.READY;
        return new ProcessedInvoice(null, fileName, status, extractedFrom, invoice, categories, issues, steps,
                Steps.millisSince(start));
    }

    private Invoice extractFromImages(List<byte[]> images, FileType type, List<Step> steps) {
        return Steps.time(steps, "extractor (vision)", true, () -> extractor.extractFromImages(pageContents(images, type)));
    }

    /** An instruction followed by every page image. HIGH detail, because invoice text is small. */
    static List<Content> pageContents(List<byte[]> images, FileType type) {
        List<Content> contents = new ArrayList<>();
        contents.add(TextContent.from("Extract the invoice shown in "
                + (images.size() == 1 ? "this image." : "these " + images.size() + " page images, in page order.")));
        for (byte[] image : images) {
            contents.add(ImageContent.from(Base64.getEncoder().encodeToString(image), type.mimeType(), DetailLevel.HIGH));
        }
        return contents;
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

}
