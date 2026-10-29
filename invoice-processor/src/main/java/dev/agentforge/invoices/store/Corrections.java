package dev.agentforge.invoices.store;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.agentforge.invoices.model.ExpenseCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.model.Invoice.LineItem;

/**
 * Applies a reviewer's corrections to an invoice. Pure Java with no database, so it is easy to test:
 * the service loads the invoice, calls this, re-runs the validator, and saves.
 */
public final class Corrections {

    /** What the reviewer sends. Any field left null stays as it is. */
    public record Correction(String vendorName, String invoiceNumber, String issueDate, String dueDate, String currency,
                             BigDecimal subtotal, BigDecimal tax, BigDecimal total,
                             List<LineCorrection> lines, String note) {
    }

    /** line is 1-based, the same numbering as the CSV export and what a person sees on the invoice. */
    public record LineCorrection(int line, String description, BigDecimal quantity, BigDecimal unitPrice,
                                 BigDecimal amount, ExpenseCategory category) {
    }

    /** One changed value, for the audit trail. */
    public record Change(String field, String oldValue, String newValue) {
    }

    public record Result(Invoice invoice, Map<Integer, ExpenseCategory> newCategories, List<Change> changes) {
    }

    public static class InvalidCorrection extends RuntimeException {
        InvalidCorrection(String message) {
            super(message);
        }
    }

    public static Result apply(Invoice current, List<ExpenseCategory> currentCategories, Correction c) {
        List<Change> changes = new ArrayList<>();

        String vendor = text("vendorName", current.vendorName(), c.vendorName(), changes);
        String number = text("invoiceNumber", current.invoiceNumber(), c.invoiceNumber(), changes);
        String issued = text("issueDate", current.issueDate(), c.issueDate(), changes);
        String due = text("dueDate", current.dueDate(), c.dueDate(), changes);
        String currency = text("currency", current.currency(), c.currency(), changes);
        BigDecimal subtotal = number("subtotal", current.subtotal(), c.subtotal(), changes);
        BigDecimal tax = number("tax", current.tax(), c.tax(), changes);
        BigDecimal total = number("total", current.total(), c.total(), changes);

        List<LineItem> lines = new ArrayList<>(current.lineItems() == null ? List.of() : current.lineItems());
        Map<Integer, ExpenseCategory> newCategories = new LinkedHashMap<>();
        for (LineCorrection lc : c.lines() == null ? List.<LineCorrection>of() : c.lines()) {
            int index = lc.line() - 1;
            if (index < 0 || index >= lines.size()) {
                throw new InvalidCorrection("Line " + lc.line() + " doesn't exist; this invoice has lines 1 to " + lines.size());
            }
            LineItem old = lines.get(index);
            String prefix = "line " + lc.line() + " ";
            lines.set(index, new LineItem(
                    text(prefix + "description", old.description(), lc.description(), changes),
                    number(prefix + "quantity", old.quantity(), lc.quantity(), changes),
                    number(prefix + "unitPrice", old.unitPrice(), lc.unitPrice(), changes),
                    number(prefix + "amount", old.amount(), lc.amount(), changes)));
            ExpenseCategory oldCategory = index < currentCategories.size() ? currentCategories.get(index) : null;
            if (lc.category() != null && lc.category() != oldCategory) {
                changes.add(new Change(prefix + "category", Objects.toString(oldCategory, null), lc.category().name()));
                newCategories.put(index, lc.category());
            }
        }

        Invoice updated = new Invoice(vendor, number, issued, due, currency, List.copyOf(lines), subtotal, tax, total);
        return new Result(updated, newCategories, changes);
    }

    private static String text(String field, String old, String requested, List<Change> changes) {
        if (requested == null || requested.equals(old)) {
            return old;
        }
        changes.add(new Change(field, old, requested));
        return requested;
    }

    /** 5 and 5.0000 are the same number, so compare with compareTo, not equals. */
    private static BigDecimal number(String field, BigDecimal old, BigDecimal requested, List<Change> changes) {
        if (requested == null || (old != null && old.compareTo(requested) == 0)) {
            return old;
        }
        changes.add(new Change(field, old == null ? null : old.toPlainString(), requested.toPlainString()));
        return requested;
    }

    private Corrections() {
    }
}
