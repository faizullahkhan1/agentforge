package dev.agentforge.invoices.agents;

import java.util.List;

import dev.agentforge.invoices.model.Invoice;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/**
 * Step 1: turns an invoice into a typed Invoice record. It only reads, it never calculates.
 * Two ways in, same rules and same output: text from a digital PDF, or images of a scan or photo.
 */
@AiService
public interface ExtractorAgent {

    String RULES = """
            You extract data from invoices. Copy values exactly as they are printed.
            Never calculate, round or correct numbers, even if they look wrong:
            checking the math is somebody else's job, and they need the original values.
            Write numbers without currency symbols or thousands separators (1,250.00 -> 1250.00).
            Convert dates to YYYY-MM-DD. Use an empty string or 0 for anything not on the invoice.
            Never invent values.
            """;

    @SystemMessage(RULES)
    @dev.langchain4j.service.UserMessage("""
            Invoice text:

            {{text}}
            """)
    Invoice extract(@V("text") String invoiceText);

    /**
     * For scans and photos: an instruction plus the page images (built in InvoicePipeline).
     * The parameter MUST be Content / List&lt;Content&gt; annotated with @UserMessage: then LangChain4j
     * sends the images as real image parts. Passing a whole UserMessage object instead does NOT work:
     * it gets turned into text with toString(), and the model receives base64 gibberish, not a picture.
     */
    @SystemMessage(RULES + """
            The invoice is given as images of its pages. If a value is unreadable, use an empty string or 0.
            """)
    Invoice extractFromImages(@dev.langchain4j.service.UserMessage List<Content> instructionAndPages);
}
