package dev.agentforge.invoices.validate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.model.Invoice.LineItem;

/**
 * Step 2, deliberately NOT an AI agent. Checking that numbers add up is exact, free and
 * instant in Java, while LLMs are unreliable at arithmetic. This is the same rule as in the
 * PR reviewer: the LLM does only what needs an LLM.
 */
public final class InvoiceValidator {

    public enum Level { ERROR, WARNING }

    public record Issue(Level level, String field, String message) {
    }

    /** Printed amounts are rounded to cents, so allow one cent of difference. */
    static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    /** Longer than any normal payment term (30/60/90 days are typical). */
    static final long MAX_PAYMENT_TERM_DAYS = 180;

    public static List<Issue> validate(Invoice invoice) {
        List<Issue> issues = new ArrayList<>();

        if (isBlank(invoice.vendorName())) {
            issues.add(new Issue(Level.ERROR, "vendorName", "Vendor name is missing"));
        }
        if (isBlank(invoice.invoiceNumber())) {
            issues.add(new Issue(Level.ERROR, "invoiceNumber", "Invoice number is missing"));
        }
        if (isBlank(invoice.currency()) || !invoice.currency().matches("[A-Z]{3}")) {
            issues.add(new Issue(Level.WARNING, "currency", "Currency '" + invoice.currency() + "' is not an ISO code"));
        }

        LocalDate issued = parseDate(invoice.issueDate(), "issueDate", true, issues);
        LocalDate due = parseDate(invoice.dueDate(), "dueDate", false, issues);
        if (issued != null && due != null && due.isBefore(issued)) {
            issues.add(new Issue(Level.ERROR, "dueDate", "Due date " + due + " is before issue date " + issued));
        }
        if (issued != null && issued.isAfter(LocalDate.now())) {
            issues.add(new Issue(Level.WARNING, "issueDate", "Issue date " + issued + " is in the future"));
        }
        // Plausibility, not just validity: a misread digit ("2026" read as "2022") still gives a valid date,
        // but it usually produces a payment term no real invoice has.
        if (issued != null && due != null && ChronoUnit.DAYS.between(issued, due) > MAX_PAYMENT_TERM_DAYS) {
            issues.add(new Issue(Level.ERROR, "issueDate", "Payment term of " + ChronoUnit.DAYS.between(issued, due)
                    + " days (" + issued + " to " + due + ") is implausible; check both dates"));
        }
        if (issued != null && issued.isBefore(LocalDate.now().minusYears(1))) {
            issues.add(new Issue(Level.WARNING, "issueDate", "Issue date " + issued + " is more than a year old"));
        }

        List<LineItem> lines = invoice.lineItems() == null ? List.of() : invoice.lineItems();
        if (lines.isEmpty()) {
            issues.add(new Issue(Level.WARNING, "lineItems", "No line items found"));
        }
        BigDecimal lineSum = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            LineItem line = lines.get(i);
            BigDecimal expected = nz(line.quantity()).multiply(nz(line.unitPrice()));
            if (differs(expected, nz(line.amount()))) {
                issues.add(new Issue(Level.ERROR, "lineItems[" + i + "].amount",
                        "'%s': %s x %s = %s, but the invoice says %s".formatted(line.description(),
                                plain(line.quantity()), plain(line.unitPrice()), plain(expected), plain(line.amount()))));
            }
            lineSum = lineSum.add(nz(line.amount()));
        }

        BigDecimal subtotal = nz(invoice.subtotal());
        if (!lines.isEmpty() && subtotal.signum() != 0 && differs(lineSum, subtotal)) {
            issues.add(new Issue(Level.ERROR, "subtotal",
                    "Line items add up to " + plain(lineSum) + ", but the subtotal says " + plain(subtotal)));
        }
        BigDecimal base = subtotal.signum() != 0 ? subtotal : lineSum;
        BigDecimal expectedTotal = base.add(nz(invoice.tax()));
        if (differs(expectedTotal, nz(invoice.total()))) {
            issues.add(new Issue(Level.ERROR, "total",
                    plain(base) + " + tax " + plain(invoice.tax()) + " = " + plain(expectedTotal)
                            + ", but the total says " + plain(invoice.total())));
        }
        if (nz(invoice.total()).signum() <= 0) {
            issues.add(new Issue(Level.ERROR, "total", "Total must be greater than zero"));
        }
        return issues;
    }

    private static LocalDate parseDate(String value, String field, boolean required, List<Issue> issues) {
        if (isBlank(value)) {
            if (required) {
                issues.add(new Issue(Level.ERROR, field, "Date is missing"));
            }
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            issues.add(new Issue(Level.ERROR, field, "'" + value + "' is not a valid YYYY-MM-DD date"));
            return null;
        }
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCE) > 0;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String plain(BigDecimal value) {
        return nz(value).stripTrailingZeros().toPlainString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private InvoiceValidator() {
    }
}
