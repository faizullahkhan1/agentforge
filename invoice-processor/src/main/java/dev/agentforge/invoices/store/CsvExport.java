package dev.agentforge.invoices.store;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.agentforge.invoices.store.InvoiceRepository.ExportRow;

/**
 * One row per line item of every approved invoice, the shape spreadsheet and bookkeeping
 * imports expect. Written by hand because the format is small, but escaped properly.
 */
public final class CsvExport {

    static final String HEADER = "invoice_id,vendor,invoice_number,issue_date,due_date,currency,line,description,"
            + "quantity,unit_price,amount,category,invoice_subtotal,invoice_tax,invoice_total";

    public static String write(List<ExportRow> rows) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n"); // RFC 4180 uses CRLF line endings
        for (ExportRow r : rows) {
            csv.append(Stream.of(r.invoiceId(), r.vendorName(), r.invoiceNumber(), r.issueDate(), r.dueDate(),
                            r.currency(), r.lineIndex() + 1, r.description(), r.quantity(), r.unitPrice(), r.amount(),
                            r.category(), r.invoiceSubtotal(), r.invoiceTax(), r.invoiceTotal())
                    .map(CsvExport::field)
                    .collect(Collectors.joining(",")))
                    .append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Quote a field if it contains a comma, quote, or line break, doubling any quotes inside.
     * Also neutralises values starting with = + - @ so spreadsheet apps don't run them as formulas
     * (a vendor named "=HYPERLINK(...)" on an uploaded invoice could otherwise do exactly that).
     */
    static String field(Object value) {
        if (value == null) {
            return "";
        }
        String s = value instanceof BigDecimal d ? d.toPlainString() : Objects.toString(value);
        if (!(value instanceof Number) && !s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private CsvExport() {
    }
}
