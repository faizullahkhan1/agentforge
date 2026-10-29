package dev.agentforge.invoices.pipeline;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import dev.agentforge.invoices.extract.FileType;

@RestController
public class InvoiceController {

    private final InvoicePipeline pipeline;

    public InvoiceController(InvoicePipeline pipeline) {
        this.pipeline = pipeline;
    }

    /** Upload a PDF, PNG or JPEG as multipart form field "file", e.g. curl -F "file=@invoice.pdf" ... */
    @PostMapping(value = "/api/invoices/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProcessedInvoice process(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
        }
        byte[] bytes = file.getBytes();
        FileType type = FileType.detect(bytes).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Upload a PDF, PNG or JPEG file"));
        return pipeline.process(file.getOriginalFilename(), bytes, type);
    }
}
