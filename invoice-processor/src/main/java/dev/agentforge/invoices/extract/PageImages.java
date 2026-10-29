package dev.agentforge.invoices.extract;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Turns PDF pages into PNG images for the vision model. Used for scanned PDFs, whose pages
 * are pictures of paper with no text layer for PDFBox to read.
 */
public final class PageImages {

    /** 150 DPI keeps small invoice text readable while keeping images (and image tokens) small. */
    static final float DPI = 150;

    public static List<byte[]> render(byte[] pdf, int maxPages) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), maxPages);
            List<byte[]> images = new ArrayList<>(pages);
            for (int page = 0; page < pages; page++) {
                // Grayscale: colour doesn't help read an invoice, and the PNG gets much smaller
                BufferedImage image = renderer.renderImageWithDPI(page, DPI, ImageType.GRAY);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(image, "png", png);
                images.add(png.toByteArray());
            }
            return images;
        }
    }

    public static int pageCount(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private PageImages() {
    }
}
