package dev.agentforge.invoices.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.model.Invoice.LineItem;
import dev.agentforge.invoices.validate.InvoiceValidator.Issue;
import dev.agentforge.invoices.validate.InvoiceValidator.Level;

class InvoiceValidatorTest {

    @Test
    void correctInvoiceHasNoIssues() {
        assertThat(InvoiceValidator.validate(invoice("2026-09-01", "2026-10-01",
                List.of(line("Laptop", "2", "500.00", "1000.00"), line("Mouse", "1", "25.50", "25.50")),
                "1025.50", "205.10", "1230.60"))).isEmpty();
    }

    @Test
    void catchesWrongLineAmountWrongSubtotalAndWrongTotal() {
        List<Issue> issues = InvoiceValidator.validate(invoice("2026-09-01", "",
                List.of(line("Laptop", "2", "500.00", "1100.00")), // 2 x 500 is 1000, not 1100
                "1000.00",                                           // and the lines add up to 1100
                "200.00", "1250.00"));                               // 1000 + 200 is 1200

        assertThat(issues).extracting(Issue::field)
                .containsExactlyInAnyOrder("lineItems[0].amount", "subtotal", "total");
        assertThat(issues).allMatch(i -> i.level() == Level.ERROR);
    }

    @Test
    void allowsOneCentRoundingDifference() {
        assertThat(InvoiceValidator.validate(invoice("2026-09-01", "",
                List.of(line("Pen", "3", "0.333", "1.00")), "1.00", "0", "1.00"))).isEmpty();
    }

    @Test
    void catchesMissingFieldsAndBadDates() {
        Invoice bad = new Invoice("", "", "31/09/2026", "2026-01-01", "dollars",
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(InvoiceValidator.validate(bad)).extracting(Issue::field)
                .contains("vendorName", "invoiceNumber", "currency", "issueDate", "lineItems", "total");
    }

    @Test
    void dueDateBeforeIssueDateIsAnError() {
        assertThat(InvoiceValidator.validate(invoice("2026-09-10", "2026-09-01",
                List.of(line("Hosting", "1", "10", "10")), "10", "0", "10")))
                .extracting(Issue::field).containsExactly("dueDate");
    }

    private static Invoice invoice(String issued, String due, List<LineItem> lines,
                                   String subtotal, String tax, String total) {
        return new Invoice("Acme Ltd", "INV-1", issued, due, "USD", lines,
                new BigDecimal(subtotal), new BigDecimal(tax), new BigDecimal(total));
    }

    private static LineItem line(String description, String qty, String price, String amount) {
        return new LineItem(description, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(amount));
    }
}
