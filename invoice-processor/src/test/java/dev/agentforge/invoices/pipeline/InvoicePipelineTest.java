package dev.agentforge.invoices.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.agentforge.invoices.model.Categorization;
import dev.agentforge.invoices.model.Categorization.LineCategory;
import dev.agentforge.invoices.model.ExpenseCategory;

class InvoicePipelineTest {

    @Test
    void alignsCategoriesToLineOrderAndFillsGapsWithOther() {
        Categorization fromModel = new Categorization(List.of(
                new LineCategory(2, ExpenseCategory.TRAVEL, "flight"),
                new LineCategory(0, ExpenseCategory.SOFTWARE_AND_SUBSCRIPTIONS, "SaaS plan")));

        List<LineCategory> aligned = InvoicePipeline.alignToLines(fromModel, 3);

        assertThat(aligned).extracting(LineCategory::index).containsExactly(0, 1, 2);
        assertThat(aligned).extracting(LineCategory::category).containsExactly(
                ExpenseCategory.SOFTWARE_AND_SUBSCRIPTIONS, ExpenseCategory.OTHER, ExpenseCategory.TRAVEL);
    }
}
