package dev.agentforge.invoices.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.agentforge.invoices.model.Categorization.LineCategory;
import dev.agentforge.invoices.model.ExpenseCategory;
import dev.agentforge.invoices.model.Invoice;
import dev.agentforge.invoices.pipeline.ProcessedInvoice;
import dev.agentforge.invoices.pipeline.ProcessedInvoice.ExtractedFrom;
import dev.agentforge.invoices.pipeline.ProcessedInvoice.Status;
import dev.agentforge.invoices.store.InvoiceRepository.InvoiceDetail;
import dev.agentforge.invoices.store.InvoiceRepository.StoredStatus;
import dev.agentforge.invoices.validate.InvoiceValidator.Issue;
import dev.agentforge.invoices.validate.InvoiceValidator.Level;

/** Real Postgres via Testcontainers; Flyway creates the invoices schema first. Needs Docker. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(InvoiceRepository.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Testcontainers
class InvoiceRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    InvoiceRepository repository;

    @Test
    void savesInvoiceWithLinesCategoriesAndIssues() {
        long id = repository.save(processed("Acme Ltd", "INV-1", "2026-09-15", Status.NEEDS_REVIEW), "a".repeat(64));

        InvoiceDetail detail = repository.find(id).orElseThrow();
        assertThat(detail.summary().status()).isEqualTo("NEEDS_REVIEW");
        assertThat(detail.summary().issueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(detail.summary().total()).isEqualByComparingTo("120.00");
        assertThat(detail.lines()).extracting(InvoiceRepository.StoredLine::category)
                .containsExactly("SOFTWARE_AND_SUBSCRIPTIONS", "OTHER");
        assertThat(detail.issuesJson()).contains("Line math is off");
    }

    @Test
    void findsDuplicatesByFileHashAndByVendorPlusNumberIgnoringCase() {
        long id = repository.save(processed("PrintHub GmbH", "RE-88231", "2026-09-15", Status.READY), "b".repeat(64));

        assertThat(repository.findIdByFileHash("b".repeat(64))).contains(id);
        assertThat(repository.findIdByFileHash("c".repeat(64))).isEmpty();
        assertThat(repository.findIdByVendorAndNumber(" printhub gmbh", "re-88231 ")).contains(id);
        assertThat(repository.findIdByVendorAndNumber("PrintHub GmbH", "RE-99999")).isEmpty();
    }

    @Test
    void decisionsOnlyMoveOpenInvoicesAndAreFinal() {
        long id = repository.save(processed("Acme Ltd", "INV-2", "2026-09-15", Status.READY), "d".repeat(64));
        EnumSet<StoredStatus> open = EnumSet.of(StoredStatus.READY, StoredStatus.NEEDS_REVIEW);

        assertThat(repository.decide(id, StoredStatus.APPROVED, "looks fine", open)).isTrue();
        assertThat(repository.decide(id, StoredStatus.REJECTED, "changed my mind", open)).isFalse();
        assertThat(repository.find(id).orElseThrow().reviewNote()).isEqualTo("looks fine");
    }

    @Test
    void exportsOnlyApprovedLinesWithinTheDateRange() {
        EnumSet<StoredStatus> open = EnumSet.of(StoredStatus.READY, StoredStatus.NEEDS_REVIEW);
        long september = repository.save(processed("Exp Ltd", "E-1", "2030-09-10", Status.READY), "e".repeat(64));
        long october = repository.save(processed("Exp Ltd", "E-2", "2030-10-10", Status.READY), "f".repeat(64));
        repository.save(processed("Exp Ltd", "E-3", "2030-09-11", Status.READY), "0".repeat(64)); // never approved
        repository.decide(september, StoredStatus.APPROVED, null, open);
        repository.decide(october, StoredStatus.APPROVED, null, open);

        assertThat(repository.approvedLines(LocalDate.of(2030, 9, 1), LocalDate.of(2030, 9, 30)))
                .extracting(InvoiceRepository.ExportRow::invoiceId).containsExactly(september, september);
        assertThat(repository.approvedLines(LocalDate.of(2030, 1, 1), null))
                .extracting(InvoiceRepository.ExportRow::invoiceId).containsExactly(september, september, october, october);
    }

    @Test
    void invalidExtractedDatesAreStoredAsNull() {
        long id = repository.save(processed("Acme Ltd", "INV-3", "31/09/2026", Status.NEEDS_REVIEW), "9".repeat(64));
        assertThat(repository.find(id).orElseThrow().summary().issueDate()).isNull();
    }

    private static ProcessedInvoice processed(String vendor, String number, String issueDate, Status status) {
        Invoice invoice = new Invoice(vendor, number, issueDate, "", "EUR", List.of(
                new Invoice.LineItem("Pro plan", new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("100.00")),
                new Invoice.LineItem("Misc", new BigDecimal("1"), new BigDecimal("20"), new BigDecimal("20.00"))),
                new BigDecimal("120.00"), BigDecimal.ZERO, new BigDecimal("120.00"));
        return new ProcessedInvoice(null, number + ".pdf", status, ExtractedFrom.TEXT, invoice,
                List.of(new LineCategory(0, ExpenseCategory.SOFTWARE_AND_SUBSCRIPTIONS, "SaaS"),
                        new LineCategory(1, ExpenseCategory.OTHER, "unclear")),
                List.of(new Issue(Level.ERROR, "lineItems[0].amount", "Line math is off")), List.of(), 1000);
    }
}
