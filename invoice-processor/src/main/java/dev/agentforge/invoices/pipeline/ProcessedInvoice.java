package dev.agentforge.invoices.pipeline;

import java.util.List;

import dev.agentforge.invoices.model.Categorization.LineCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.validate.InvoiceValidator.Issue;

/**
 * The pipeline's result. READY = all checks passed; NEEDS_REVIEW = a human must look at
 * the issues before this invoice goes into the books.
 */
public record ProcessedInvoice(
        String fileName,
        Status status,
        ExtractedFrom extractedFrom,
        Invoice invoice,
        List<LineCategory> categories,
        List<Issue> issues,
        List<Step> steps,
        long totalMillis
) {

    public enum Status { READY, NEEDS_REVIEW }

    /** TEXT = read from the PDF's text layer; VISION = read from page images (scan, photo, image file). */
    public enum ExtractedFrom { TEXT, VISION }

    /** Trace of one pipeline step: which one, whether it is an AI agent or plain Java, and how long it took. */
    public record Step(String name, boolean usesAi, long millis) {
    }
}
