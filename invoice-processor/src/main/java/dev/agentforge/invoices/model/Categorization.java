package dev.agentforge.invoices.model;

import java.util.List;

import dev.langchain4j.model.output.structured.Description;

public record Categorization(
        @Description("exactly one entry for every line item index")
        List<LineCategory> lines
) {

    public record LineCategory(
            @Description("index of the line item, starting at 0")
            int index,
            ExpenseCategory category,
            @Description("a few words explaining the choice")
            String reason
    ) {
    }
}
