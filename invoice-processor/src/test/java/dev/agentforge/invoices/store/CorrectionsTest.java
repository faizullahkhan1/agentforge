package dev.agentforge.invoices.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.agentforge.invoices.model.ExpenseCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.store.Corrections.Change;
import dev.agentforge.invoices.store.Corrections.Correction;
import dev.agentforge.invoices.store.Corrections.LineCorrection;
import dev.agentforge.invoices.store.Corrections.Result;

class CorrectionsTest {

    // Real case: the photo of the CloudNest invoice was read with quantity 0.5 instead of 5
    private static final Invoice MISREAD = new Invoice("CloudNest Software Inc.", "CN-2026-0417", "2026-10-03",
            "2026-11-02", "USD", List.of(
            new Invoice.LineItem("Pro plan", new BigDecimal("0.5"), new BigDecimal("240.00"), new BigDecimal("1200.00")),
            new Invoice.LineItem("Onboarding", new BigDecimal("6"), new BigDecimal("95.00"), new BigDecimal("570.00"))),
            new BigDecimal("1770.00"), BigDecimal.ZERO, new BigDecimal("1770.00"));

    private static final List<ExpenseCategory> CATEGORIES =
            List.of(ExpenseCategory.SOFTWARE_AND_SUBSCRIPTIONS, ExpenseCategory.OTHER);

    @Test
    void changesOnlyTheGivenFieldsAndRecordsOldAndNewValues() {
        Result result = Corrections.apply(MISREAD, CATEGORIES, correction(
                List.of(new LineCorrection(1, null, new BigDecimal("5"), null, null, null)), null));

        assertThat(result.invoice().lineItems().get(0).quantity()).isEqualByComparingTo("5");
        assertThat(result.invoice().lineItems().get(0).amount()).isEqualByComparingTo("1200.00"); // untouched
        assertThat(result.invoice().vendorName()).isEqualTo("CloudNest Software Inc.");
        assertThat(result.changes()).containsExactly(new Change("line 1 quantity", "0.5", "5"));
    }

    @Test
    void sameValueInADifferentFormatIsNotAChange() {
        Result result = Corrections.apply(MISREAD, CATEGORIES, correction(
                List.of(new LineCorrection(2, "Onboarding", new BigDecimal("6.0000"), null, null, null)), "2026-10-03"));

        assertThat(result.changes()).isEmpty();
    }

    @Test
    void categoryCorrectionIsRecordedSeparately() {
        Result result = Corrections.apply(MISREAD, CATEGORIES, correction(
                List.of(new LineCorrection(2, null, null, null, null, ExpenseCategory.PROFESSIONAL_SERVICES)), null));

        assertThat(result.newCategories()).containsEntry(1, ExpenseCategory.PROFESSIONAL_SERVICES);
        assertThat(result.changes()).containsExactly(new Change("line 2 category", "OTHER", "PROFESSIONAL_SERVICES"));
    }

    @Test
    void rejectsLinesThatDoNotExist() {
        assertThatThrownBy(() -> Corrections.apply(MISREAD, CATEGORIES, correction(
                List.of(new LineCorrection(3, null, BigDecimal.ONE, null, null, null)), null)))
                .isInstanceOf(Corrections.InvalidCorrection.class)
                .hasMessageContaining("lines 1 to 2");
    }

    private static Correction correction(List<LineCorrection> lines, String issueDate) {
        return new Correction(null, null, issueDate, null, null, null, null, null, lines, "fixing a misread");
    }
}
