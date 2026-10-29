package dev.agentforge.invoices.pipeline;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import dev.agentforge.invoices.extract.FileType;
import dev.agentforge.invoices.store.CsvExport;
import dev.agentforge.invoices.store.InvoiceRepository;
import dev.agentforge.invoices.store.InvoiceRepository.InvoiceDetail;
import dev.agentforge.invoices.store.InvoiceRepository.InvoiceSummary;
import dev.agentforge.invoices.store.InvoiceRepository.StoredStatus;
import dev.agentforge.invoices.store.InvoiceService;

@RestController
public class InvoiceController {

    private final InvoiceService service;
    private final InvoiceRepository repository;

    public InvoiceController(InvoiceService service, InvoiceRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    public record Decision(String note) {
    }

    /** Upload a PDF, PNG or JPEG as multipart form field "file". The result is stored and gets an id. */
    @PostMapping(value = "/api/invoices/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProcessedInvoice process(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
        }
        byte[] bytes = file.getBytes();
        FileType type = FileType.detect(bytes).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Upload a PDF, PNG or JPEG file"));
        return service.upload(file.getOriginalFilename(), bytes, type);
    }

    /** ?status=NEEDS_REVIEW gives the review queue. */
    @GetMapping("/api/invoices")
    public List<InvoiceSummary> list(@RequestParam(required = false) StoredStatus status) {
        return repository.list(status);
    }

    @GetMapping("/api/invoices/{id}")
    public InvoiceDetail get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/api/invoices/{id}/approve")
    public InvoiceDetail approve(@PathVariable long id, @RequestBody(required = false) Decision decision) {
        return service.approve(id, decision == null ? null : decision.note());
    }

    @PostMapping("/api/invoices/{id}/reject")
    public InvoiceDetail reject(@PathVariable long id, @RequestBody(required = false) Decision decision) {
        return service.reject(id, decision == null ? null : decision.note());
    }

    /** Approved invoices as CSV, one row per line item. Optional ?from=2026-09-01&to=2026-09-30 on issue date. */
    @GetMapping("/api/invoices/export.csv")
    public ResponseEntity<String> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = CsvExport.write(repository.approvedLines(from, to));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"approved-invoices.csv\"")
                .body(csv);
    }
}
