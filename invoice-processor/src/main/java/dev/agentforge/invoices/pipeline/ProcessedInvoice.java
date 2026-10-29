package dev.agentforge.invoices.pipeline;

import java.util.ArrayList;
import java.util.List;

import dev.agentforge.invoices.model.Categorization.LineCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.validate.InvoiceValidator.Issue;
import dev.agentforge.invoices.validate.InvoiceValidator.Level;

/**
 * The pipeline's result. READY = all checks passed; NEEDS_REVIEW = a human must look at
 * the issues before this invoice goes into the books. id is null until it is saved.
 */
public record ProcessedInvoice(
        Long id,
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

    public ProcessedInvoice withId(long newId) {
        return new ProcessedInvoice(newId, fileName, status, extractedFrom, invoice, categories, issues, steps, totalMillis);
    }

    /** Adds an issue found after the pipeline (e.g. a duplicate); an ERROR also sends it to review. */
    public ProcessedInvoice withIssue(Issue issue) {
        List<Issue> all = new ArrayList<>(issues);
        all.add(issue);
        Status newStatus = issue.level() == Level.ERROR ? Status.NEEDS_REVIEW : status;
        return new ProcessedInvoice(id, fileName, newStatus, extractedFrom, invoice, categories, List.copyOf(all),
                steps, totalMillis);
    }
}
