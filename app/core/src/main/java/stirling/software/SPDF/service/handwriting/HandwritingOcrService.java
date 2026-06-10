package stirling.software.SPDF.service.handwriting;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.service.CustomPDFDocumentFactory;

/**
 * Orchestrates handwriting OCR with the chain Gemini → Document AI fallback.
 *
 * <p>For each page in the input PDF, Gemini is tried first. If Gemini is unavailable, throws, or
 * returns empty, the page is sent to Document AI. The final result is a plain-text transcript with
 * page separators — suitable as a sidecar text file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandwritingOcrService {

    private static final int RENDER_DPI = 300;

    private final GeminiOcrClient geminiOcrClient;
    private final DocumentAiOcrClient documentAiOcrClient;
    private final ApplicationProperties applicationProperties;
    private final CustomPDFDocumentFactory pdfDocumentFactory;

    public boolean isEnabled() {
        return applicationProperties.getSystem().getGoogleCloud().isEnabled()
                && (geminiOcrClient.isAvailable() || documentAiOcrClient.isAvailable());
    }

    /**
     * OCR the supplied PDF and return the full transcript with per-page markers like {@code ---
     * Page 1 ---}.
     */
    public String recognize(byte[] pdfBytes) throws IOException {
        try (PDDocument document = pdfDocumentFactory.load(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);

            List<String> pageTexts = new ArrayList<>(pageCount);
            int geminiFailures = 0;

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                String text = null;

                if (geminiOcrClient.isAvailable()) {
                    try {
                        byte[] pngBytes = renderPageToPng(renderer, pageIndex);
                        text = geminiOcrClient.recognize(pngBytes);
                        log.debug(
                                "Gemini OCR succeeded for page {} ({} chars)",
                                pageIndex + 1,
                                text.length());
                    } catch (Exception e) {
                        geminiFailures++;
                        log.warn(
                                "Gemini OCR failed on page {}: {}. Falling back to Document AI.",
                                pageIndex + 1,
                                e.getMessage());
                    }
                }

                if (text == null) {
                    if (!documentAiOcrClient.isAvailable()) {
                        throw new IOException(
                                "Gemini failed and Document AI is not configured. Enable"
                                        + " googleCloud.documentAi or set a valid processorId.");
                    }
                    byte[] singlePagePdf = extractSinglePageAsPdf(document, pageIndex);
                    text = documentAiOcrClient.recognizePdfChunk(singlePagePdf);
                    log.debug(
                            "Document AI OCR succeeded for page {} ({} chars)",
                            pageIndex + 1,
                            text.length());
                }

                pageTexts.add(text);
            }

            if (geminiFailures > 0) {
                log.info(
                        "Handwriting OCR done: {} pages, {} Gemini failures (handled by Document AI fallback)",
                        pageCount,
                        geminiFailures);
            }

            return formatTranscript(pageTexts);
        }
    }

    private byte[] renderPageToPng(PDFRenderer renderer, int pageIndex) throws IOException {
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }

    private byte[] extractSinglePageAsPdf(PDDocument source, int pageIndex) throws IOException {
        try (PDDocument single = new PDDocument();
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = source.getPage(pageIndex);
            single.addPage(page);
            single.save(baos);
            return baos.toByteArray();
        }
    }

    private String formatTranscript(List<String> pageTexts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pageTexts.size(); i++) {
            if (i > 0) sb.append("\n\n");
            sb.append("--- Page ").append(i + 1).append(" ---\n");
            sb.append(pageTexts.get(i));
        }
        return sb.toString();
    }
}
