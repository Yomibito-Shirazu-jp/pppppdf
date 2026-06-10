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

import stirling.software.SPDF.model.api.general.AiImpositionRequest;
import stirling.software.common.annotations.AutoJobPostMapping;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.GeneralUtils;
import stirling.software.common.util.WebResponseUtils;

/**
 * Signature-based ("折面付") imposition: pages are grouped into signatures of {@code signaturePages}
 * (4/8/16/32) and saddle-stitched within each signature. The output PDF holds all signatures
 * concatenated, ready for printing, folding, and gathering.
 *
 * <p>Differs from {@link BookletImpositionController}, which treats the entire document as one
 * saddle-stitched booklet. For thick books this becomes impractical (paper bulk distorts the
 * gutter); signature-based imposition is the standard production workflow.
 */
@RestController
@RequestMapping("/api/v1/general")
@Tag(name = "General", description = "General APIs")
@RequiredArgsConstructor
public class AiImpositionController {

    private final CustomPDFDocumentFactory pdfDocumentFactory;

    @AutoJobPostMapping(value = "/ai-imposition", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Signature-based PDF imposition (折面付)",
            description =
                    "Imposes a PDF for printing with proper signature-based page ordering. Pages "
                            + "are split into signatures of N pages (4/8/16/32), each saddle-stitched "
                            + "within. Suitable for production booklet/book printing where multiple "
                            + "signatures are gathered and bound. Input:PDF Output:PDF Type:SISO")
    public ResponseEntity<byte[]> createAiImposition(@ModelAttribute AiImpositionRequest request)
            throws IOException {

        MultipartFile file = request.getFileInput();
        int signaturePages = request.getSignaturePages();
        boolean addBorder = Boolean.TRUE.equals(request.getAddBorder());
        String spineLocation =
                request.getSpineLocation() != null ? request.getSpineLocation() : "LEFT";
        boolean addGutter = Boolean.TRUE.equals(request.getAddGutter());
        float gutterSize = request.getGutterSize();
        boolean doubleSided = Boolean.TRUE.equals(request.getDoubleSided());
        String duplexPass = request.getDuplexPass() != null ? request.getDuplexPass() : "BOTH";
        boolean flipOnShortEdge = Boolean.TRUE.equals(request.getFlipOnShortEdge());

        if (signaturePages < 4 || signaturePages % 4 != 0) {
            throw new IllegalArgumentException(
                    "signaturePages must be a positive multiple of 4 (4, 8, 16, 32, ...)");
        }

        try (PDDocument sourceDocument = pdfDocumentFactory.load(file)) {
            int totalPages = sourceDocument.getNumberOfPages();

            try (PDDocument newDocument =
                    createSignatureImposition(
                            sourceDocument,
                            totalPages,
                            signaturePages,
                            addBorder,
                            spineLocation,
                            addGutter,
                            gutterSize,
                            doubleSided,
                            duplexPass,
                            flipOnShortEdge)) {

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                newDocument.save(baos);

                byte[] result = baos.toByteArray();
                return WebResponseUtils.bytesToWebResponse(
                        result,
                        GeneralUtils.generateFilename(
                                Filenames.toSimpleFileName(file.getOriginalFilename()),
                                "_imposed.pdf"));
            }
        }
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

    /**
     * Compute imposed sides for a signature-grouped saddle stitch. Pages are divided into
     * signatures of {@code signaturePages}, then each signature is independently saddle-stitched.
     * Output order: signature-0 sheet-0 front, signature-0 sheet-0 back, ..., signature-0 last
     * sheet back, signature-1 sheet-0 front, ...
     */
    private static List<Side> signatureImpositionSides(
            int totalPagesOriginal,
            int signaturePages,
            boolean doubleSided,
            String duplexPass,
            boolean flipOnShortEdge) {

        int paddedTotal = padToMultipleOf(totalPagesOriginal, signaturePages);
        int signatures = paddedTotal / signaturePages;
        int sheetsPerSignature = signaturePages / 4;

        boolean includeFront = "BOTH".equals(duplexPass) || "FIRST".equals(duplexPass);
        boolean includeBack = "BOTH".equals(duplexPass) || "SECOND".equals(duplexPass);

        List<Side> out = new ArrayList<>();

        for (int sig = 0; sig < signatures; sig++) {
            int sigStart = sig * signaturePages;

            for (int s = 0; s < sheetsPerSignature; s++) {
                // Local indices within this signature, then offset by sigStart
                int aLocal = signaturePages - 1 - (s * 2); // left, front
                int bLocal = (s * 2); // right, front
                int cLocal = (s * 2) + 1; // left, back
                int dLocal = signaturePages - 2 - (s * 2); // right, back

                int a = clampToBlank(sigStart + aLocal, totalPagesOriginal);
                int b = clampToBlank(sigStart + bLocal, totalPagesOriginal);
                int c = clampToBlank(sigStart + cLocal, totalPagesOriginal);
                int d = clampToBlank(sigStart + dLocal, totalPagesOriginal);

                if (includeFront) {
                    out.add(new Side(a, b));
                }

                if (includeBack) {
                    if (doubleSided && flipOnShortEdge) {
                        out.add(new Side(d, c));
                    } else {
                        out.add(new Side(c, d));
                    }
                }
            }
        }
        return out;
    }

    private static int clampToBlank(int absoluteIndex, int totalPagesOriginal) {
        return absoluteIndex < totalPagesOriginal ? absoluteIndex : -1;
    }

    private PDDocument createSignatureImposition(
            PDDocument src,
            int totalPages,
            int signaturePages,
            boolean addBorder,
            String spineLocation,
            boolean addGutter,
            float gutterSize,
            boolean doubleSided,
            String duplexPass,
            boolean flipOnShortEdge)
            throws IOException {

        PDDocument dst = pdfDocumentFactory.createNewDocumentBasedOnOldDocument(src);

        // Derive paper size from source first page; output is landscape (2-up portrait)
        PDRectangle srcBox = src.getPage(0).getCropBox();
        PDRectangle pageSize = new PDRectangle(srcBox.getHeight() * 2f, srcBox.getWidth());

        if (gutterSize < 0) gutterSize = 0;
        if (gutterSize >= pageSize.getWidth() / 2f) gutterSize = pageSize.getWidth() / 2f - 1f;

        List<Side> sides =
                signatureImpositionSides(
                        totalPages, signaturePages, doubleSided, duplexPass, flipOnShortEdge);

        boolean rtl = "RIGHT".equalsIgnoreCase(spineLocation);
        int leftCol = rtl ? 1 : 0;
        int rightCol = rtl ? 0 : 1;

        for (Side side : sides) {
            PDPage out = new PDPage(pageSize);
            dst.addPage(out);

            float cellW = pageSize.getWidth() / 2f;
            float cellH = pageSize.getHeight();

            float g = addGutter ? gutterSize : 0f;
            float leftCellX = leftCol * cellW + (g / 2f);
            float rightCellX = rightCol * cellW - (g / 2f);
            float leftCellW = cellW - (g / 2f);
            float rightCellW = cellW - (g / 2f);

            LayerUtility layerUtility = new LayerUtility(dst);

            try (PDPageContentStream cs =
                    new PDPageContentStream(
                            dst, out, PDPageContentStream.AppendMode.APPEND, true, true)) {

                if (addBorder) {
                    cs.setLineWidth(1.5f);
                    cs.setStrokingColor(Color.BLACK);
                }

                drawCell(
                        src,
                        dst,
                        cs,
                        layerUtility,
                        side.left,
                        leftCellX,
                        0f,
                        leftCellW,
                        cellH,
                        addBorder);
                drawCell(
                        src,
                        dst,
                        cs,
                        layerUtility,
                        side.right,
                        rightCellX,
                        0f,
                        rightCellW,
                        cellH,
                        addBorder);
            }
        }
        return dst;
    }

    private void drawCell(
            PDDocument src,
            PDDocument dst,
            PDPageContentStream cs,
            LayerUtility layerUtility,
            int pageIndex,
            float cellX,
            float cellY,
            float cellW,
            float cellH,
            boolean addBorder)
            throws IOException {

        if (pageIndex < 0) {
            if (addBorder) {
                cs.addRect(cellX, cellY, cellW, cellH);
                cs.stroke();
            }
            return;
        }

        PDPage srcPage = src.getPage(pageIndex);
        PDRectangle r = srcPage.getCropBox();
        int rot = (srcPage.getRotation() + 360) % 360;

        float sx = cellW / r.getWidth();
        float sy = cellH / r.getHeight();
        if (rot == 90 || rot == 270) {
            sx = cellW / r.getHeight();
            sy = cellH / r.getWidth();
        }
        float s = Math.min(sx, sy);

        float drawnW = (rot == 90 || rot == 270) ? r.getHeight() * s : r.getWidth() * s;
        float drawnH = (rot == 90 || rot == 270) ? r.getWidth() * s : r.getHeight() * s;

        float tx = cellX + (cellW - drawnW) / 2f - r.getLowerLeftX() * s;
        float ty = cellY + (cellH - drawnH) / 2f - r.getLowerLeftY() * s;

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
                // 0°: no-op
        }

        PDFormXObject form = layerUtility.importPageAsForm(src, pageIndex);
        cs.drawForm(form);
        cs.restoreGraphicsState();

        if (addBorder) {
            cs.addRect(cellX, cellY, cellW, cellH);
            cs.stroke();
        }
    }
}
