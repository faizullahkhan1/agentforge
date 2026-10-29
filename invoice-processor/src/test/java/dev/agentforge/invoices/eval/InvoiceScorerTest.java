package dev.agentforge.invoices.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.agentforge.invoices.eval.InvoiceScorer.ExpectedInvoice;
import dev.agentforge.invoices.eval.InvoiceScorer.ExpectedLine;
import dev.agentforge.invoices.eval.InvoiceScorer.RunScore;
import dev.agentforge.invoices.model.Categorization.LineCategory;
import dev.agentforge.invoices.model.ExpenseCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.pipeline.ProcessedInvoice;
import dev.agentforge.invoices.pipeline.ProcessedInvoice.ExtractedFrom;
import dev.agentforge.invoices.pipeline.ProcessedInvoice.Status;
import dev.agentforge.invoices.validate.InvoiceValidator;

/** Normal unit test (runs in every mvn test): checks the grading logic, not the AI. */
class InvoiceScorerTest {

    private static final ExpectedInvoice KEY = new ExpectedInvoice(
            Map.of("vendorName", "Acme Ltd", "invoiceNumber", "A-1", "issueDate", "2026-09-15", "dueDate", "2026-10-15",
                    "currency", "EUR", "subtotal", "200.00", "tax", "0", "total", "200.00"),
            List.of(new ExpectedLine("2", "100.00", "200.00", "OFFICE_SUPPLIES")),
            "READY", List.of());

    @Test
    void perfectReadScoresEverythingCorrect() {
        RunScore score = score(invoice("acme ltd ", "2026-09-15", "2", "200.00"));

        assertThat(score.wrongValues()).isEmpty();
        assertThat(score.statusCorrect()).isTrue();
        assertThat(score.misread()).isFalse();
    }

    @Test
    void misreadThatBreaksTheMathIsCaughtNotSilent() {
        RunScore score = score(invoice("Acme Ltd", "2026-09-15", "0.5", "200.00")); // 0.5 x 100 != 200

        assertThat(score.wrongValues()).containsExactly("line 1 quantity: got 0.5, expected 2");
        assertThat(score.unexpectedErrors()).isNotEmpty();
        assertThat(score.silent()).isFalse();
        assertThat(score.statusCorrect()).isFalse();
    }

    @Test
    void misreadThatStillAddsUpIsSilent() {
        RunScore score = score(invoice("Acme Ltd", "2026-09-16", "2", "200.00")); // plausible wrong date

        assertThat(score.wrongValues()).containsExactly("issueDate: got '2026-09-16', expected '2026-09-15'");
        assertThat(score.silent()).isTrue();
    }

    /** Runs the real validator, like the pipeline does, so status and issues are realistic. */
    private static RunScore score(Invoice invoice) {
        List<InvoiceValidator.Issue> issues = InvoiceValidator.validate(invoice);
        Status status = issues.stream().anyMatch(i -> i.level() == InvoiceValidator.Level.ERROR) ? Status.NEEDS_REVIEW : Status.READY;
        ProcessedInvoice processed = new ProcessedInvoice(null, "f.pdf", status, ExtractedFrom.TEXT, invoice,
                List.of(new LineCategory(0, ExpenseCategory.OFFICE_SUPPLIES, "")), issues, List.of(), 0);
        return InvoiceScorer.score(processed, KEY);
    }

    private static Invoice invoice(String vendor, String issueDate, String quantity, String amount) {
        return new Invoice(vendor, "A-1", issueDate, "2026-10-15", "EUR",
                List.of(new Invoice.LineItem("Paper", new BigDecimal(quantity), new BigDecimal("100.00"), new BigDecimal(amount))),
                new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("200.00"));
    }
}
