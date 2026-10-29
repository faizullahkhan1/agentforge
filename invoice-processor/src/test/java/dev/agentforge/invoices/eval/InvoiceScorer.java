package dev.agentforge.invoices.eval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.pipeline.ProcessedInvoice;
import dev.agentforge.invoices.validate.InvoiceValidator.Issue;
import dev.agentforge.invoices.validate.InvoiceValidator.Level;

/**
 * Grades one processed invoice against the true values. The key question isn't "was everything
 * read correctly?" but "if something was misread, did the checks notice?". A caught misread goes
 * to a human; a SILENT one would be booked with wrong numbers.
 */
final class InvoiceScorer {

    static final List<String> TEXT_FIELDS = List.of("vendorName", "invoiceNumber", "issueDate", "dueDate", "currency");
    static final List<String> NUMBER_FIELDS = List.of("subtotal", "tax", "total");

    record ExpectedLine(String quantity, String unitPrice, String amount, String category) {
    }

    record ExpectedInvoice(Map<String, String> fields, List<ExpectedLine> lines, String status, List<String> errors) {
    }

    /**
     * wrongValues: misread data (what booking would get wrong). wrongCategories: kept apart, since no
     * check can catch a wrong category. unexpectedErrors: ERROR issues besides the planted ones, i.e.
     * the checks noticing something. silent: values are wrong but nothing beyond the planted errors was flagged.
     */
    record RunScore(int valuesChecked, List<String> wrongValues, int categoriesChecked, List<String> wrongCategories,
                    boolean statusCorrect, boolean plantedErrorsFound, List<String> unexpectedErrors) {

        boolean misread() {
            return !wrongValues.isEmpty();
        }

        boolean silent() {
            return misread() && unexpectedErrors.isEmpty();
        }
    }

    static RunScore score(ProcessedInvoice result, ExpectedInvoice key) {
        Invoice inv = result.invoice();
        List<String> wrong = new ArrayList<>();
        int checked = 0;

        Map<String, String> actualText = Map.of("vendorName", nz(inv.vendorName()), "invoiceNumber", nz(inv.invoiceNumber()),
                "issueDate", nz(inv.issueDate()), "dueDate", nz(inv.dueDate()), "currency", nz(inv.currency()));
        for (String field : TEXT_FIELDS) {
            checked++;
            if (!normalize(actualText.get(field)).equals(normalize(key.fields().get(field)))) {
                wrong.add(field + ": got '" + actualText.get(field) + "', expected '" + key.fields().get(field) + "'");
            }
        }
        Map<String, BigDecimal> actualNumbers = Map.of("subtotal", nz(inv.subtotal()), "tax", nz(inv.tax()), "total", nz(inv.total()));
        for (String field : NUMBER_FIELDS) {
            checked++;
            compareNumber(field, actualNumbers.get(field), key.fields().get(field), wrong);
        }

        List<Invoice.LineItem> lines = inv.lineItems() == null ? List.of() : inv.lineItems();
        List<String> wrongCategories = new ArrayList<>();
        checked++;
        if (lines.size() != key.lines().size()) {
            wrong.add("line count: got " + lines.size() + ", expected " + key.lines().size());
        }
        for (int i = 0; i < Math.min(lines.size(), key.lines().size()); i++) {
            Invoice.LineItem a = lines.get(i);
            ExpectedLine e = key.lines().get(i);
            String prefix = "line " + (i + 1) + " ";
            checked += 3;
            compareNumber(prefix + "quantity", a.quantity(), e.quantity(), wrong);
            compareNumber(prefix + "unitPrice", a.unitPrice(), e.unitPrice(), wrong);
            compareNumber(prefix + "amount", a.amount(), e.amount(), wrong);
            String category = i < result.categories().size() ? result.categories().get(i).category().name() : "";
            if (!category.equals(e.category())) {
                wrongCategories.add(prefix + "category: got " + category + ", expected " + e.category());
            }
        }

        Set<String> errorFields = result.issues().stream().filter(i -> i.level() == Level.ERROR)
                .map(Issue::field).collect(Collectors.toSet());
        List<String> unexpectedErrors = result.issues().stream()
                .filter(i -> i.level() == Level.ERROR && !key.errors().contains(i.field()))
                .map(i -> i.field() + ": " + i.message())
                .toList();

        return new RunScore(checked, wrong, Math.min(lines.size(), key.lines().size()), wrongCategories,
                result.status().name().equals(key.status()), errorFields.containsAll(key.errors()), unexpectedErrors);
    }

    private static void compareNumber(String field, BigDecimal actual, String expected, List<String> wrong) {
        if (actual == null || actual.compareTo(new BigDecimal(expected)) != 0) {
            wrong.add(field + ": got " + (actual == null ? "null" : actual.toPlainString()) + ", expected " + expected);
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.strip().toLowerCase(Locale.ROOT);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static BigDecimal nz(BigDecimal d) {
        return d == null ? BigDecimal.ZERO : d;
    }

    private InvoiceScorer() {
    }
}
