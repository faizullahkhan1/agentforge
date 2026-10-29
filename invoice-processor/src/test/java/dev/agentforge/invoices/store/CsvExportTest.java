package dev.agentforge.invoices.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.agentforge.invoices.store.InvoiceRepository.ExportRow;

class CsvExportTest {

    @Test
    void writesHeaderAndOneRowPerLineWithOneBasedLineNumbers() {
        String csv = CsvExport.write(List.of(new ExportRow(7, "PrintHub GmbH", "RE-88231", LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 29), "EUR", 0, "Flyer A5, 4/4-farbig", new BigDecimal("5000.0000"),
                new BigDecimal("0.0800"), new BigDecimal("400.00"), "MARKETING_AND_ADVERTISING",
                new BigDecimal("613.00"), new BigDecimal("116.47"), new BigDecimal("729.47"))));

        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo(CsvExport.HEADER);
        assertThat(lines[1]).isEqualTo("7,PrintHub GmbH,RE-88231,2026-09-15,2026-09-29,EUR,1,\"Flyer A5, 4/4-farbig\","
                + "5000.0000,0.0800,400.00,MARKETING_AND_ADVERTISING,613.00,116.47,729.47");
    }

    @Test
    void escapesQuotesAndLineBreaks() {
        assertThat(CsvExport.field("Banner \"XL\"")).isEqualTo("\"Banner \"\"XL\"\"\"");
        assertThat(CsvExport.field("line one\nline two")).isEqualTo("\"line one\nline two\"");
        assertThat(CsvExport.field(null)).isEmpty();
    }

    @Test
    void neutralisesSpreadsheetFormulasButNotNegativeNumbers() {
        assertThat(CsvExport.field("=HYPERLINK(\"http://evil\")")).startsWith("\"'=HYPERLINK");
        assertThat(CsvExport.field("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvExport.field(new BigDecimal("-15.00"))).isEqualTo("-15.00"); // a credit note line stays a number
    }
}
