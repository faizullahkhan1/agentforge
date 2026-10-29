package dev.agentforge.invoices.model;

import java.math.BigDecimal;
import java.util.List;

import dev.langchain4j.model.output.structured.Description;

/**
 * What the Extractor agent fills in from the invoice text. Strict JSON schema mode makes
 * every field required, so "missing" is expressed as an empty string or 0 instead of null.
 * Dates stay Strings here and are parsed (and checked) in Java by the validator.
 */
public record Invoice(
        @Description("company that issued the invoice")
        String vendorName,
        @Description("invoice number exactly as printed")
        String invoiceNumber,
        @Description("issue date as YYYY-MM-DD, empty string if not shown")
        String issueDate,
        @Description("payment due date as YYYY-MM-DD, empty string if not shown")
        String dueDate,
        @Description("ISO 4217 currency code such as USD, EUR, PKR")
        String currency,
        List<LineItem> lineItems,
        @Description("subtotal before tax exactly as printed, 0 if not shown")
        BigDecimal subtotal,
        @Description("total tax amount exactly as printed, 0 if no tax")
        BigDecimal tax,
        @Description("final amount to pay exactly as printed")
        BigDecimal total
) {

    public record LineItem(
            String description,
            @Description("quantity, 1 if not shown")
            BigDecimal quantity,
            @Description("price per unit exactly as printed")
            BigDecimal unitPrice,
            @Description("line amount exactly as printed; do not recompute it")
            BigDecimal amount
    ) {
    }
}
