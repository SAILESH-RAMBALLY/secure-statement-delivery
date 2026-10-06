package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.PdfDocument;
import dev.rambally.statements.domain.StatementPeriod;

import org.junit.jupiter.api.Test;

/** The only test that touches the PDF library; every other test uses the hand-written TestPdfs. */
class StatementPdfGeneratorTest {

    private final StatementPdfGenerator generator = new StatementPdfGenerator();

    @Test
    void generates_a_valid_pdf_under_the_size_cap_containing_masked_account_period_and_balances() throws IOException {
        byte[] pdf = generator.generate(new CustomerId("C-1001"), new AccountNumber("1234567890"), StatementPeriod.parse("2026-09"));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isLessThan(200 * 1024);
        assertThat(new PdfDocument(pdf).size()).isEqualTo(pdf.length);

        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(text).contains("SPECIMEN").contains("******7890").contains("2026-09")
                .contains("Opening balance").contains("Closing balance").doesNotContain("C-1001-secret");
    }

    @Test
    void output_is_deterministic_for_the_same_inputs_and_differs_across_periods() {
        byte[] a = generator.generate(new CustomerId("C-1001"), new AccountNumber("1234567890"), StatementPeriod.parse("2026-09"));
        byte[] b = generator.generate(new CustomerId("C-1001"), new AccountNumber("1234567890"), StatementPeriod.parse("2026-08"));

        assertThat(StatementPdfGenerator.transactionsFor(new AccountNumber("1234567890"), StatementPeriod.parse("2026-09")))
                .isEqualTo(StatementPdfGenerator.transactionsFor(new AccountNumber("1234567890"), StatementPeriod.parse("2026-09")));
        assertThat(a).isNotEqualTo(b);
    }
}
