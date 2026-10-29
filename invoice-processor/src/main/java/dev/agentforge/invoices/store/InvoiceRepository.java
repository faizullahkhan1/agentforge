package dev.agentforge.invoices.store;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.agentforge.invoices.model.Categorization.LineCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.pipeline.ProcessedInvoice;

/** Plain SQL through JdbcClient, like pr-reviewer. Every table is named with its schema: invoices.xxx */
@Repository
public class InvoiceRepository {

    /** Status values stored in the database. Decisions only move READY/NEEDS_REVIEW to APPROVED/REJECTED. */
    public enum StoredStatus { READY, NEEDS_REVIEW, APPROVED, REJECTED }

    public record InvoiceSummary(long id, String status, String vendorName, String invoiceNumber, LocalDate issueDate,
                                 String currency, BigDecimal total, String extractedFrom, OffsetDateTime createdAt) {
    }

    public record StoredLine(int lineIndex, String description, BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal amount, String category, String categoryReason) {
    }

    public record InvoiceDetail(InvoiceSummary summary, String fileName, LocalDate dueDate, BigDecimal subtotal,
                                BigDecimal tax, String issuesJson, String reviewNote, OffsetDateTime reviewedAt,
                                List<StoredLine> lines) {
    }

    /** One CSV row: an approved invoice's line item plus the invoice fields an accountant needs next to it. */
    public record ExportRow(long invoiceId, String vendorName, String invoiceNumber, LocalDate issueDate,
                            LocalDate dueDate, String currency, int lineIndex, String description, BigDecimal quantity,
                            BigDecimal unitPrice, BigDecimal amount, String category, BigDecimal invoiceSubtotal,
                            BigDecimal invoiceTax, BigDecimal invoiceTotal) {
    }

    private record DetailRow(String fileName, LocalDate dueDate, BigDecimal subtotal, BigDecimal tax,
                             String issuesJson, String reviewNote, OffsetDateTime reviewedAt) {
    }

    private static final String SUMMARY_COLUMNS =
            "id, status, vendor_name, invoice_number, issue_date, currency, total, extracted_from, created_at";

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public InvoiceRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<Long> findIdByFileHash(String sha256) {
        return jdbc.sql("SELECT id FROM invoices.invoice WHERE file_sha256 = ?").param(sha256).query(Long.class).optional();
    }

    /** Same vendor + same invoice number (ignoring case and spaces) = probably the same invoice, sent twice. */
    public Optional<Long> findIdByVendorAndNumber(String vendorName, String invoiceNumber) {
        if (vendorName == null || vendorName.isBlank() || invoiceNumber == null || invoiceNumber.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT id FROM invoices.invoice
                        WHERE lower(trim(vendor_name)) = lower(trim(?)) AND lower(trim(invoice_number)) = lower(trim(?))
                          AND status <> 'REJECTED'
                        ORDER BY id LIMIT 1
                        """)
                .params(vendorName, invoiceNumber).query(Long.class).optional();
    }

    @Transactional
    public long save(ProcessedInvoice processed, String sha256) {
        Invoice inv = processed.invoice();
        long id = jdbc.sql("""
                        INSERT INTO invoices.invoice (status, file_name, file_sha256, extracted_from, vendor_name,
                            invoice_number, issue_date, due_date, currency, subtotal, tax, total, issues)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        RETURNING id
                        """)
                .params(processed.status().name(), processed.fileName(), sha256, processed.extractedFrom().name(),
                        inv.vendorName(), inv.invoiceNumber(), dateOrNull(inv.issueDate()), dateOrNull(inv.dueDate()),
                        inv.currency(), inv.subtotal(), inv.tax(), inv.total(), toJson(processed.issues()))
                .query(Long.class).single();

        List<Invoice.LineItem> lines = inv.lineItems() == null ? List.of() : inv.lineItems();
        for (int i = 0; i < lines.size(); i++) {
            Invoice.LineItem line = lines.get(i);
            LineCategory category = i < processed.categories().size() ? processed.categories().get(i) : null;
            jdbc.sql("""
                            INSERT INTO invoices.invoice_line (invoice_id, line_index, description, quantity, unit_price,
                                amount, category, category_reason)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(id, i, line.description(), line.quantity(), line.unitPrice(), line.amount(),
                            category == null ? null : category.category().name(),
                            category == null ? null : category.reason())
                    .update();
        }
        return id;
    }

    public List<InvoiceSummary> list(StoredStatus status) {
        if (status == null) {
            return jdbc.sql("SELECT " + SUMMARY_COLUMNS + " FROM invoices.invoice ORDER BY id DESC LIMIT 200")
                    .query(InvoiceSummary.class).list();
        }
        return jdbc.sql("SELECT " + SUMMARY_COLUMNS + " FROM invoices.invoice WHERE status = ? ORDER BY id DESC LIMIT 200")
                .param(status.name()).query(InvoiceSummary.class).list();
    }

    public Optional<InvoiceDetail> find(long id) {
        return jdbc.sql("SELECT " + SUMMARY_COLUMNS + " FROM invoices.invoice WHERE id = ?").param(id)
                .query(InvoiceSummary.class).optional()
                .map(summary -> {
                    DetailRow d = jdbc.sql("""
                                    SELECT file_name, due_date, subtotal, tax, issues::text AS issues_json,
                                           review_note, reviewed_at
                                    FROM invoices.invoice WHERE id = ?
                                    """)
                            .param(id).query(DetailRow.class).single();
                    List<StoredLine> lines = jdbc.sql("""
                                    SELECT line_index, description, quantity, unit_price, amount, category, category_reason
                                    FROM invoices.invoice_line WHERE invoice_id = ? ORDER BY line_index
                                    """)
                            .param(id).query(StoredLine.class).list();
                    return new InvoiceDetail(summary, d.fileName(), d.dueDate(), d.subtotal(), d.tax(), d.issuesJson(),
                            d.reviewNote(), d.reviewedAt(), lines);
                });
    }

    /**
     * Moves an invoice to APPROVED or REJECTED, but only if it is currently in one of the allowed states.
     * The check is part of the UPDATE itself, so two people deciding at the same moment can't both win.
     *
     * @return true if the invoice was changed, false if it didn't exist or wasn't in an allowed state
     */
    public boolean decide(long id, StoredStatus to, String note, Set<StoredStatus> allowedFrom) {
        String[] from = allowedFrom.stream().map(Enum::name).toArray(String[]::new);
        return jdbc.sql("""
                        UPDATE invoices.invoice SET status = ?, review_note = ?, reviewed_at = now()
                        WHERE id = ? AND status = ANY (?)
                        """)
                .params(to.name(), note, id, from)
                .update() == 1;
    }

    public List<ExportRow> approvedLines(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT i.id AS invoice_id, i.vendor_name, i.invoice_number, i.issue_date, i.due_date, i.currency,
                               l.line_index, l.description, l.quantity, l.unit_price, l.amount, l.category,
                               i.subtotal AS invoice_subtotal, i.tax AS invoice_tax, i.total AS invoice_total
                        FROM invoices.invoice i
                        JOIN invoices.invoice_line l ON l.invoice_id = i.id
                        WHERE i.status = 'APPROVED'
                          AND (CAST(? AS DATE) IS NULL OR i.issue_date >= ?)
                          AND (CAST(? AS DATE) IS NULL OR i.issue_date <= ?)
                        ORDER BY i.issue_date, i.id, l.line_index
                        """)
                .params(from, from, to, to)
                .query(ExportRow.class).list();
    }

    /** The extractor returns dates as text; anything that isn't a real YYYY-MM-DD date is stored as NULL. */
    static LocalDate dateOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
