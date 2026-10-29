package dev.agentforge.invoices.agents;

import dev.agentforge.invoices.model.Categorization;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/** Step 3: assigns a bookkeeping category to every line item. */
@AiService
public interface CategorizerAgent {

    @SystemMessage("""
            You are a bookkeeper. Assign an expense category to every line item of an invoice.
            Use the vendor name as a hint (a line "Pro plan" from a software company is a subscription),
            but categorize each line on its own: one invoice can contain several categories.
            Use OTHER only when nothing else fits.
            """)
    @UserMessage("""
            Vendor: {{vendor}}

            Line items (JSON):
            {{lines}}
            """)
    Categorization categorize(@V("vendor") String vendorName, @V("lines") String lineItemsJson);
}
