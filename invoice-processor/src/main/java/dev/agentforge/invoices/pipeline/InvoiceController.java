package dev.agentforge.invoices.pipeline;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InvoiceController {

    private final InvoicePipeline pipeline;

    public InvoiceController(InvoicePipeline pipeline) {
        this.pipeline = pipeline;
    }

    /** Upload a PDF as multipart form field "file", e.g. curl -F "file=@invoice.pdf" ... */
    @PostMapping(value = "/api/invoices/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProcessedInvoice process(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
        }
        byte[] bytes = file.getBytes();
        // Check the file's first bytes ("%PDF"), not just its name or the type the client claims
        if (bytes.length < 4 || !new String(bytes, 0, 4).equals("%PDF")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF files are supported for now");
        }
        return pipeline.process(file.getOriginalFilename(), bytes);
    }
}
