package dev.agentforge.invoices.store;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import dev.agentforge.invoices.extract.FileType;
import dev.agentforge.invoices.model.ExpenseCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.pipeline.InvoicePipeline;
import dev.agentforge.invoices.pipeline.ProcessedInvoice;
import dev.agentforge.invoices.store.Corrections.Correction;
import dev.agentforge.invoices.store.Corrections.Result;
import dev.agentforge.invoices.store.InvoiceRepository.InvoiceDetail;
import dev.agentforge.invoices.store.InvoiceRepository.StoredStatus;
import dev.agentforge.invoices.validate.InvoiceValidator;
import dev.agentforge.invoices.validate.InvoiceValidator.Issue;
import dev.agentforge.invoices.validate.InvoiceValidator.Level;

/**
 * The workflow around the pipeline: refuse exact duplicates before paying for AI calls,
 * flag likely duplicates, store the result, and let a human approve or reject it.
 */
@Service
public class InvoiceService {

    /** States in which an invoice can still be corrected, approved or rejected. */
    private static final Set<StoredStatus> OPEN = EnumSet.of(StoredStatus.READY, StoredStatus.NEEDS_REVIEW);

    private final InvoicePipeline pipeline;
    private final InvoiceRepository repository;

    public InvoiceService(InvoicePipeline pipeline, InvoiceRepository repository) {
        this.pipeline = pipeline;
        this.repository = repository;
    }

    public ProcessedInvoice upload(String fileName, byte[] bytes, FileType type) {
        // 1. Exact same file again: refuse right away. Checking a hash is free; the AI calls are not.
        String sha256 = sha256(bytes);
        Optional<Long> sameFile = repository.findIdByFileHash(sha256);
        if (sameFile.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This exact file was already uploaded as invoice " + sameFile.get());
        }

        ProcessedInvoice processed = pipeline.process(fileName, bytes, type);

        // 2. Different file, same vendor + invoice number: probably the same invoice sent twice
        //    (e.g. a PDF by email and a photo of the paper copy). Store it, but a human must decide.
        Optional<Long> sameInvoice = repository.findIdByVendorAndNumber(
                processed.invoice().vendorName(), processed.invoice().invoiceNumber());
        if (sameInvoice.isPresent()) {
            processed = processed.withIssue(new Issue(Level.ERROR, "invoiceNumber",
                    "Possible duplicate of invoice " + sameInvoice.get() + " (same vendor and invoice number)"));
        }

        long id = repository.save(processed, sha256);
        return processed.withId(id);
    }

    /**
     * A READY invoice can be approved as is. A NEEDS_REVIEW invoice can only be approved with a note,
     * so there is always a written reason why someone accepted an invoice that failed checks.
     */
    public InvoiceDetail approve(long id, String note) {
        InvoiceDetail current = get(id);
        if (StoredStatus.NEEDS_REVIEW.name().equals(current.summary().status()) && isBlank(note)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invoice " + id + " failed checks: approving it needs a note explaining why it's OK");
        }
        return decide(id, StoredStatus.APPROVED, note);
    }

    public InvoiceDetail reject(long id, String note) {
        if (isBlank(note)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejecting needs a note with the reason");
        }
        return decide(id, StoredStatus.REJECTED, note);
    }

    /**
     * A reviewer fixes values the AI misread. The validator and the duplicate check run again on the
     * corrected invoice (plain Java, no AI calls), so the status reflects the corrected data:
     * fixing the only problem turns NEEDS_REVIEW into READY.
     */
    public InvoiceDetail correct(long id, Correction correction) {
        if (correction.note() == null || correction.note().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corrections need a note saying what was wrong");
        }
        InvoiceDetail current = get(id);
        if (!OPEN.contains(StoredStatus.valueOf(current.summary().status()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invoice " + id + " is already " + current.summary().status() + " and can't be corrected");
        }

        Result result;
        try {
            result = Corrections.apply(toInvoice(current), categoriesOf(current), correction);
        } catch (Corrections.InvalidCorrection e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (result.changes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to change: all values are already like that");
        }

        List<Issue> issues = new ArrayList<>(InvoiceValidator.validate(result.invoice()));
        repository.findIdByVendorAndNumber(result.invoice().vendorName(), result.invoice().invoiceNumber(), id)
                .ifPresent(other -> issues.add(new Issue(Level.ERROR, "invoiceNumber",
                        "Possible duplicate of invoice " + other + " (same vendor and invoice number)")));
        StoredStatus status = issues.stream().anyMatch(i -> i.level() == Level.ERROR)
                ? StoredStatus.NEEDS_REVIEW : StoredStatus.READY;

        if (!repository.saveCorrection(id, result.invoice(), result.newCategories(), issues, status,
                result.changes(), correction.note())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice " + id + " was decided while you were correcting it");
        }
        return get(id);
    }

    /** Rebuilds the Invoice record from what's stored, so the same validator can check it again. */
    static Invoice toInvoice(InvoiceDetail d) {
        List<Invoice.LineItem> lines = d.lines().stream()
                .map(l -> new Invoice.LineItem(l.description(), l.quantity(), l.unitPrice(), l.amount()))
                .toList();
        return new Invoice(d.summary().vendorName(), d.summary().invoiceNumber(),
                d.summary().issueDate() == null ? "" : d.summary().issueDate().toString(),
                d.dueDate() == null ? "" : d.dueDate().toString(),
                d.summary().currency(), lines, d.subtotal(), d.tax(), d.summary().total());
    }

    private static List<ExpenseCategory> categoriesOf(InvoiceDetail d) {
        return d.lines().stream()
                .map(l -> l.category() == null ? null : ExpenseCategory.valueOf(l.category()))
                .toList();
    }

    public InvoiceDetail get(long id) {
        return repository.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No invoice " + id));
    }

    private InvoiceDetail decide(long id, StoredStatus to, String note) {
        boolean changed = repository.decide(id, to, note, OPEN);
        if (!changed) {
            InvoiceDetail current = get(id); // 404 if it doesn't exist
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invoice " + id + " is already " + current.summary().status() + "; decisions are final");
        }
        return get(id);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
