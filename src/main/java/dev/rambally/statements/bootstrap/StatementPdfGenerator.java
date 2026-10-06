package dev.rambally.statements.bootstrap;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.StatementPeriod;

/**
 * Renders a plausible-looking demo statement so reviewers have something real to download. Used only by
 * the demo seeder; never parses uploads. Deterministic for a given account and period.
 */
public class StatementPdfGenerator {

    private static final String[] MERCHANTS = {"Grocer & Co", "City Fuel", "Rent", "Salary", "Mobile Airtime", "Coffee House",
            "Pharmacy", "Streaming Service", "Electricity", "Transfer to savings"};

    public record Transaction(String day, String description, BigDecimal amount) {
    }

    public byte[] generate(CustomerId customerId, AccountNumber account, StatementPeriod period) {
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font specimen = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, Color.LIGHT_GRAY);

        Paragraph watermark = new Paragraph("SPECIMEN - DEMO DATA", specimen);
        watermark.setAlignment(Element.ALIGN_CENTER);
        document.add(watermark);
        document.add(new Paragraph("Secure Statement Delivery - Demo Bank", title));
        document.add(new Paragraph("Account statement for " + period, normal));
        document.add(new Paragraph("Customer: " + customerId + "    Account: " + account.masked(), normal));
        document.add(new Paragraph(" "));

        List<Transaction> transactions = transactionsFor(account, period);
        BigDecimal opening = new BigDecimal("12500.00");
        BigDecimal closing = transactions.stream().map(Transaction::amount).reduce(opening, BigDecimal::add);

        PdfPTable table = new PdfPTable(new float[] {1.2f, 4f, 1.8f});
        table.setWidthPercentage(100);
        header(table, "Date", normal);
        header(table, "Description", normal);
        header(table, "Amount (ZAR)", normal);
        for (Transaction t : transactions) {
            table.addCell(new Phrase(period + "-" + t.day(), normal));
            table.addCell(new Phrase(t.description(), normal));
            PdfPCell amount = new PdfPCell(new Phrase(t.amount().toPlainString(), normal));
            amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(amount);
        }
        document.add(table);
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Opening balance: " + opening.toPlainString(), normal));
        document.add(new Paragraph("Closing balance: " + closing.setScale(2, RoundingMode.HALF_UP).toPlainString(), normal));
        document.close();
        return out.toByteArray();
    }

    private static void header(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(230, 230, 230));
        table.addCell(cell);
    }

    static List<Transaction> transactionsFor(AccountNumber account, StatementPeriod period) {
        Random random = new Random((account.value() + period).hashCode());
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int day = 2 + i * 3 + random.nextInt(2);
            String merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];
            BigDecimal amount = BigDecimal.valueOf(random.nextInt(250_000) / 100.0).setScale(2, RoundingMode.HALF_UP);
            if (!merchant.equals("Salary")) {
                amount = amount.negate();
            } else {
                amount = amount.add(new BigDecimal("18000.00"));
            }
            transactions.add(new Transaction(String.format("%02d", Math.min(day, 28)), merchant, amount));
        }
        return transactions;
    }
}
