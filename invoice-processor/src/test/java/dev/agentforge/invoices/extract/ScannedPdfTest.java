package dev.agentforge.invoices.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

class ScannedPdfTest {

    @Test
    void scannedPdfHasNoTextLayerButRendersToOnePngPerPage() throws IOException {
        byte[] scan = scannedPdf(2);

        assertThat(PdfText.hasTextLayer(PdfText.extract(scan))).isFalse();

        List<byte[]> pages = PageImages.render(scan, 3);
        assertThat(pages).hasSize(2);
        assertThat(pages).allSatisfy(png -> assertThat(FileType.detect(png)).contains(FileType.PNG));
    }

    @Test
    void rendersAtMostMaxPages() throws IOException {
        assertThat(PageImages.render(scannedPdf(5), 3)).hasSize(3);
    }

    @Test
    void detectsFileTypeFromMagicBytesOnly() {
        assertThat(FileType.detect("%PDF-1.7 ...".getBytes())).contains(FileType.PDF);
        assertThat(FileType.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D})).contains(FileType.PNG);
        assertThat(FileType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).contains(FileType.JPEG);
        assertThat(FileType.detect("# README".getBytes())).isEmpty();
        assertThat(FileType.detect(new byte[]{'%'})).isEmpty();
    }

    /** Like a scanner's output: every page is just a picture, the "text" is pixels. */
    private static byte[] scannedPdf(int pageCount) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                BufferedImage picture = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = picture.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 600, 800);
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
                g.drawString("INVOICE page " + (i + 1) + "   Total: 100.00 EUR", 40, 80);
                g.dispose();

                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(doc, picture);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
