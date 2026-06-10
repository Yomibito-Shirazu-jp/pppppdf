package stirling.software.SPDF.controller.api;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.pixee.security.Filenames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.model.api.general.PrintImpositionRequest;
import stirling.software.common.annotations.AutoJobPostMapping;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.GeneralUtils;
import stirling.software.common.util.WebResponseUtils;

/**
 * "印刷面付" — produces a print-ready imposed PDF using the same Java/PDFBox
 * saddle-stitch logic as {@link BookletImpositionController}. Pages are grouped
 * into signatures, then pairs of pages are placed side-by-side on a landscape
 * output sheet, with optional registration marks (トンボ).
 */
@RestController
@RequestMapping("/api/v1/general")
@Tag(name = "General", description = "General APIs")
@RequiredArgsConstructor
public class PrintImpositionController {

    private final CustomPDFDocumentFactory pdfDocumentFactory;

    private static final float MARK_ARM = 14f;
    private static final float MARK_GAP = 3f;

    @AutoJobPostMapping(value = "/print-imposition", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Print-ready PDF imposition with optional registration marks",
            description =
                    "Imposes a PDF for professional printing using signature-based saddle-stitch "
                            + "page ordering (same engine as Booklet Imposition). "
                            + "Layouts: folio (4p/sig) / quarto (8p/sig) / octavo (16p/sig) / "
                            + "card-fold4 / card-fold8. Supports registration marks (トンボ), "
                            + "configurable paper size, and page range selection. "
                            + "Input:PDF Output:PDF Type:SISO")
    public ResponseEntity<byte[]> createPrintImposition(
            @ModelAttribute PrintImpositionRequest request) throws IOException {

        MultipartFile file = request.getFileInput();
        String layout = request.getLayout() != null ? request.getLayout() : "quarto";
        boolean addMarks = Boolean.TRUE.equals(request.getMarks());
        boolean mirrorBackSide = Boolean.TRUE.equals(request.getMirrorBackSide());
        float margin = Math.max(0f, request.getMargin());
        int startIdx = Math.max(0, request.getStartPage() - 1); // convert to 0-indexed
        int endPage = request.getEndPage();

        int baseSignaturePages = layoutToSignaturePages(layout);
        int forms = request.getForms();
        int signaturePages = (forms > 0) ? 4 * forms : baseSignaturePages;
        if (signaturePages < 4 || signaturePages % 4 != 0) signaturePages = baseSignaturePages;

        try (PDDocument sourceDocument = pdfDocumentFactory.load(file)) {
            int totalPages = sourceDocument.getNumberOfPages();
            int endIdx = (endPage <= 0 || endPage > totalPages) ? totalPages : endPage;
            if (startIdx >= endIdx) startIdx = 0;

            PDRectangle trimSize = resolveOutputPageSize(
                    request.getDimensions(), request.getOrientation(), sourceDocument);

            try (PDDocument result = createImposition(
                    sourceDocument, startIdx, endIdx, signaturePages, trimSize,
                    addMarks, margin, mirrorBackSide)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                result.save(baos);
                return WebResponseUtils.bytesToWebResponse(
                        baos.toByteArray(),
                        GeneralUtils.generateFilename(
                                Filenames.toSimpleFileName(file.getOriginalFilename()),
                                "_print_imposed.pdf"));
            }
        }
    }

    private static int layoutToSignaturePages(String layout) {
        return switch (layout.toLowerCase()) {
            case "folio" -> 4;
            case "octavo" -> 16;
            case "card-fold4" -> 4;
            case "card-fold8" -> 8;
            default -> 8; // quarto
        };
    }

    private static PDRectangle resolveOutputPageSize(
            String dimensions, String orientation, PDDocument src) {
        PDRectangle base = null;

        if (dimensions != null && !dimensions.isBlank()) {
            base = switch (dimensions.trim().toUpperCase()) {
                case "A3" -> PDRectangle.A3;
                case "A4" -> PDRectangle.A4;
                case "A5" -> PDRectangle.A5;
                case "LETTER" -> PDRectangle.LETTER;
                case "LEGAL" -> PDRectangle.LEGAL;
                case "B4" -> new PDRectangle(708.66f, 1000.63f);
                case "B5" -> new PDRectangle(498.90f, 708.66f);
                default -> parseWxH(dimensions);
            };
        }

        if (base == null) {
            PDRectangle srcBox = src.getPage(0).getCropBox();
            // Default: landscape sheet wide enough for 2 source pages side-by-side
            base = new PDRectangle(srcBox.getHeight() * 2f, srcBox.getWidth());
        } else {
            // Force landscape (2 pages side-by-side)
            if (base.getWidth() < base.getHeight()) {
                base = new PDRectangle(base.getHeight(), base.getWidth());
            }
        }

        if ("portrait".equalsIgnoreCase(orientation) && base.getWidth() > base.getHeight()) {
            base = new PDRectangle(base.getHeight(), base.getWidth());
        }

        return base;
    }

    private static PDRectangle parseWxH(String s) {
        try {
            String[] parts = s.split("[xX]");
            if (parts.length == 2) {
                float w = Float.parseFloat(parts[0].trim());
                float h = Float.parseFloat(parts[1].trim());
                if (w > 0 && h > 0) return new PDRectangle(w, h);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static int padToMultipleOf(int n, int multiple) {
        return (n + multiple - 1) / multiple * multiple;
    }

    private static class Side {
        final int left, right;

        Side(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    private static List<Side> signatureImpositionSides(
            int contentPages, int signaturePages, int pageOffset, boolean mirrorBackSide) {
        int paddedTotal = padToMultipleOf(contentPages, signaturePages);
        int signatures = paddedTotal / signaturePages;
        int sheetsPerSignature = signaturePages / 4;
        List<Side> out = new ArrayList<>();

        for (int sig = 0; sig < signatures; sig++) {
            int sigStart = sig * signaturePages;
            for (int s = 0; s < sheetsPerSignature; s++) {
                int aLocal = signaturePages - 1 - (s * 2);
                int bLocal = s * 2;
                int cLocal = s * 2 + 1;
                int dLocal = signaturePages - 2 - (s * 2);

                // front side
                out.add(new Side(resolveIdx(sigStart + aLocal, contentPages, pageOffset),
                                 resolveIdx(sigStart + bLocal, contentPages, pageOffset)));
                // back side: swap left/right when mirrorBackSide to correct short-edge plate reflection
                if (mirrorBackSide) {
                    out.add(new Side(resolveIdx(sigStart + dLocal, contentPages, pageOffset),
                                     resolveIdx(sigStart + cLocal, contentPages, pageOffset)));
                } else {
                    out.add(new Side(resolveIdx(sigStart + cLocal, contentPages, pageOffset),
                                     resolveIdx(sigStart + dLocal, contentPages, pageOffset)));
                }
            }
        }
        return out;
    }

    private static int resolveIdx(int localIdx, int contentPages, int pageOffset) {
        return localIdx < contentPages ? localIdx + pageOffset : -1;
    }

    private PDDocument createImposition(
            PDDocument src, int startIdx, int endIdx,
            int signaturePages, PDRectangle trimSize,
            boolean addMarks, float margin, boolean mirrorBackSide) throws IOException {

        PDDocument dst = pdfDocumentFactory.createNewDocumentBasedOnOldDocument(src);
        int contentPages = endIdx - startIdx;

        float bleed = addMarks ? (MARK_GAP + MARK_ARM + 2f) : 0f;
        PDRectangle pageSize = addMarks
                ? new PDRectangle(trimSize.getWidth() + 2 * bleed,
                                  trimSize.getHeight() + 2 * bleed)
                : trimSize;

        float cellW = trimSize.getWidth() / 2f;
        float cellH = trimSize.getHeight();

        List<Side> sides = signatureImpositionSides(contentPages, signaturePages, startIdx, mirrorBackSide);

        for (Side side : sides) {
            PDPage outPage = new PDPage(pageSize);
            dst.addPage(outPage);

            LayerUtility layerUtility = new LayerUtility(dst);

            try (PDPageContentStream cs = new PDPageContentStream(
                    dst, outPage, PDPageContentStream.AppendMode.APPEND, true, true)) {

                drawCell(src, dst, cs, layerUtility, side.left,
                        bleed, bleed, cellW, cellH, margin);
                drawCell(src, dst, cs, layerUtility, side.right,
                        bleed + cellW, bleed, cellW, cellH, margin);

                if (addMarks) {
                    drawRegistrationMarks(cs, trimSize, bleed);
                }
            }
        }
        return dst;
    }

    private void drawCell(
            PDDocument src, PDDocument dst, PDPageContentStream cs,
            LayerUtility layerUtility, int pageIndex,
            float cellX, float cellY, float cellW, float cellH, float margin)
            throws IOException {

        if (pageIndex < 0) return;

        PDPage srcPage = src.getPage(pageIndex);
        PDRectangle r = srcPage.getCropBox();
        int rot = (srcPage.getRotation() + 360) % 360;

        float availW = cellW - 2f * margin;
        float availH = cellH - 2f * margin;

        float sx, sy;
        if (rot == 90 || rot == 270) {
            sx = availW / r.getHeight();
            sy = availH / r.getWidth();
        } else {
            sx = availW / r.getWidth();
            sy = availH / r.getHeight();
        }
        float s = Math.min(sx, sy);

        float drawnW = (rot == 90 || rot == 270) ? r.getHeight() * s : r.getWidth() * s;
        float drawnH = (rot == 90 || rot == 270) ? r.getWidth() * s : r.getHeight() * s;

        float tx = cellX + margin + (availW - drawnW) / 2f - r.getLowerLeftX() * s;
        float ty = cellY + margin + (availH - drawnH) / 2f - r.getLowerLeftY() * s;

        cs.saveGraphicsState();
        cs.transform(Matrix.getTranslateInstance(tx, ty));
        cs.transform(Matrix.getScaleInstance(s, s));

        switch (rot) {
            case 90:
                cs.transform(Matrix.getRotateInstance(Math.PI / 2, 0, 0));
                cs.transform(Matrix.getTranslateInstance(0, -r.getWidth()));
                break;
            case 180:
                cs.transform(Matrix.getRotateInstance(Math.PI, 0, 0));
                cs.transform(Matrix.getTranslateInstance(-r.getWidth(), -r.getHeight()));
                break;
            case 270:
                cs.transform(Matrix.getRotateInstance(3 * Math.PI / 2, 0, 0));
                cs.transform(Matrix.getTranslateInstance(-r.getHeight(), 0));
                break;
            default:
                break;
        }

        PDFormXObject form = layerUtility.importPageAsForm(src, pageIndex);
        cs.drawForm(form);
        cs.restoreGraphicsState();
    }

    /**
     * Draws L-shaped corner marks and center spine marks just outside the trim box.
     * The page MediaBox is enlarged by {@code bleed} on all sides to accommodate the marks.
     */
    private void drawRegistrationMarks(
            PDPageContentStream cs, PDRectangle trim, float bleed) throws IOException {
        cs.setStrokingColor(Color.BLACK);
        cs.setLineWidth(0.5f);

        float ox = bleed;
        float oy = bleed;
        float tw = trim.getWidth();
        float th = trim.getHeight();
        float g = MARK_GAP;
        float a = MARK_ARM;

        // Bottom-left corner
        cs.moveTo(ox - g - a, oy); cs.lineTo(ox - g, oy);
        cs.moveTo(ox, oy - g - a); cs.lineTo(ox, oy - g);
        // Bottom-right corner
        cs.moveTo(ox + tw + g, oy); cs.lineTo(ox + tw + g + a, oy);
        cs.moveTo(ox + tw, oy - g - a); cs.lineTo(ox + tw, oy - g);
        // Top-left corner
        cs.moveTo(ox - g - a, oy + th); cs.lineTo(ox - g, oy + th);
        cs.moveTo(ox, oy + th + g); cs.lineTo(ox, oy + th + g + a);
        // Top-right corner
        cs.moveTo(ox + tw + g, oy + th); cs.lineTo(ox + tw + g + a, oy + th);
        cs.moveTo(ox + tw, oy + th + g); cs.lineTo(ox + tw, oy + th + g + a);

        // Center spine mark (top and bottom)
        float midX = ox + tw / 2f;
        cs.moveTo(midX - 4f, oy - g); cs.lineTo(midX + 4f, oy - g);
        cs.moveTo(midX, oy - g - a / 2f); cs.lineTo(midX, oy - g + a / 2f);
        cs.moveTo(midX - 4f, oy + th + g); cs.lineTo(midX + 4f, oy + th + g);
        cs.moveTo(midX, oy + th + g - a / 2f); cs.lineTo(midX, oy + th + g + a / 2f);

        cs.stroke();
    }
}
