package dev.agentforge.invoices.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.agentforge.invoices.extract.FileType;
import dev.agentforge.invoices.model.Categorization;
import dev.agentforge.invoices.model.Categorization.LineCategory;
import dev.agentforge.invoices.model.ExpenseCategory;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;

class InvoicePipelineTest {

    @Test
    void pageContentsHaveInstructionThenOneHighDetailImagePerPage() {
        List<Content> contents = InvoicePipeline.pageContents(List.of(new byte[]{1}, new byte[]{2}), FileType.PNG);

        assertThat(contents).hasSize(3);
        assertThat(((TextContent) contents.get(0)).text()).contains("2 page images");
        assertThat(contents.subList(1, 3)).allSatisfy(c -> {
            ImageContent image = (ImageContent) c;
            assertThat(image.image().mimeType()).isEqualTo("image/png");
            assertThat(image.detailLevel()).isEqualTo(ImageContent.DetailLevel.HIGH);
        });
    }

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
