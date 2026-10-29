package dev.agentforge.invoices.agents;

import dev.agentforge.invoices.model.Invoice;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/** Step 1: turns messy invoice text into a typed Invoice record. It only reads, it never calculates. */
@AiService
public interface ExtractorAgent {

    @SystemMessage("""
            You extract data from invoices. Copy values exactly as they are printed.
            Never calculate, round or correct numbers, even if they look wrong:
            checking the math is somebody else's job, and they need the original values.
            Write numbers without currency symbols or thousands separators (1,250.00 -> 1250.00).
            Convert dates to YYYY-MM-DD. Use an empty string or 0 for anything not on the invoice.
            Never invent values.
            """)
    @UserMessage("""
            Invoice text:

            {{text}}
            """)
    Invoice extract(@V("text") String invoiceText);
}
