package stirling.software.SPDF.controller.api;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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

import stirling.software.SPDF.model.api.general.BookletImpositionRequest;
import stirling.software.SPDF.service.facilis.FacilisDbService;
import stirling.software.SPDF.service.facilis.FacilisLayout;
import stirling.software.SPDF.service.facilis.FacilisLayoutExecutor;
import stirling.software.common.annotations.AutoJobPostMapping;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.GeneralUtils;
import stirling.software.common.util.WebResponseUtils;

@RestController
@RequestMapping("/api/v1/general")
@Tag(name = "General", description = "General APIs")
@RequiredArgsConstructor
public class BookletImpositionController {

    private final CustomPDFDocumentFactory pdfDocumentFactory;
    private final FacilisDbService facilisDbService;
    private final FacilisLayoutExecutor facilisLayoutExecutor;

    @AutoJobPostMapping(
            value = "/booklet-imposition",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Create a booklet with proper page imposition",
            description =
                    "This operation combines page reordering for booklet printing with multi-page layout. "
                            + "It rearranges pages in the correct order for booklet printing and places multiple pages "
                            + "on each sheet for proper folding and binding. Input:PDF Output:PDF Type:SISO")
    public ResponseEntity<byte[]> createBookletImposition(
            @ModelAttribute BookletImpositionRequest request) throws IOException {

        MultipartFile file = request.getFileInput();
        int pagesPerSheet = request.getPagesPerSheet();
        boolean addBorder = Boolean.TRUE.equals(request.getAddBorder());
        String spineLocation =
                request.getSpineLocation() != null ? request.getSpineLocation() : "LEFT";
        boolean addGutter = Boolean.TRUE.equals(request.getAddGutter());
        float gutterSize = request.getGutterSize();
        boolean doubleSided = Boolean.TRUE.equals(request.getDoubleSided());
        String duplexPass = request.getDuplexPass() != null ? request.getDuplexPass() : "BOTH";
        boolean flipOnShortEdge = Boolean.TRUE.equals(request.getFlipOnShortEdge());

        // 2/4/8/16/32-up only. Non-2 needs a FACILIS .lay template since signature folding
        // patterns vary by press / fold scheme and we don't algorithmically generate them.
        if (pagesPerSheet != 2
                && pagesPerSheet != 4
                && pagesPerSheet != 8
                && pagesPerSheet != 16
                && pagesPerSheet != 32) {
            throw new IllegalArgumentException(
                    "pagesPerSheet must be one of 2, 4, 8, 16, 32.");
        }

        // ── Template-driven path: FACILIS .lay specifies the entire imposition. Manual fields
        // are ignored when a templateId is supplied. ──
        String templateId = request.getTemplateId();
        if (templateId != null && !templateId.isBlank()) {
            FacilisLayout layout = facilisDbService.getLayout(templateId);
            if (layout == null) {
                throw new IllegalArgumentException(
                        "Unknown FACILIS templateId: '" + templateId + "'. Upload a DB.zip first"
                                + " or pick from /api/v1/general/facilis-db/layouts.");
            }
            try (PDDocument source = pdfDocumentFactory.load(file);
                    PDDocument out = facilisLayoutExecutor.execute(source, layout)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                out.save(baos);
                return WebResponseUtils.bytesToWebResponse(
                        baos.toByteArray(),
                        GeneralUtils.generateFilename(
                                Filenames.toSimpleFileName(file.getOriginalFilename()),
                                "_imposed.pdf"));
            }
        }

        if (pagesPerSheet != 2) {
            throw new IllegalArgumentException(
                    "pagesPerSheet > 2 requires a FACILIS .lay templateId (signature folding"
                            + " patterns are template-driven, not algorithmic). Upload a DB.zip"
                            + " and select a template, or fall back to pagesPerSheet=2.");
        }

        ImpositionExtras extras =
                ImpositionExtras.from(
                        Boolean.TRUE.equals(request.getBleedIndependent()),
                        request.getBleedTopMm(),
                        request.getBleedBottomMm(),
                        request.getBleedInsideMm(),
                        request.getBleedOutsideMm(),
                        Boolean.TRUE.equals(request.getGripperEnabled()),
                        request.getGripperMm(),
                        Boolean.TRUE.equals(request.getSpineSignatureEnabled()),
                        request.getSpineSignatureText(),
                        request.getCropMarkType() != null ? request.getCropMarkType() : "NONE",
                        Boolean.TRUE.equals(request.getRegistrationMarks()),
                        Filenames.toSimpleFileName(file.getOriginalFilename()));

        try (PDDocument sourceDocument = pdfDocumentFactory.load(file)) {
            int totalPages = sourceDocument.getNumberOfPages();

            // Create proper booklet with signature-based page ordering
            try (PDDocument newDocument =
                    createSaddleBooklet(
                            sourceDocument,
                            totalPages,
                            addBorder,
                            spineLocation,
                            addGutter,
                            gutterSize,
                            doubleSided,
                            duplexPass,
                            flipOnShortEdge,
                            extras)) {

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                newDocument.save(baos);

                byte[] result = baos.toByteArray();
                return WebResponseUtils.bytesToWebResponse(
                        result,
                        GeneralUtils.generateFilename(
                                Filenames.toSimpleFileName(file.getOriginalFilename()),
                                "_booklet.pdf"));
            }
        }
    }

    /**
     * Imposition extras (bleed / gripper / spine signature / marks). Sizes converted from mm to
     * PDF points (1 mm = 2.834645669 pt) at parse time so renderers stay in PDF units.
     */
    private record ImpositionExtras(
            boolean bleedIndependent,
            float bleedTopPt,
            float bleedBottomPt,
            float bleedInsidePt,
            float bleedOutsidePt,
            boolean gripperEnabled,
            float gripperPt,
            boolean spineSignatureEnabled,
            String spineSignatureText,
            String cropMarkType,
            boolean registrationMarks,
            String jobName) {

        private static final float MM_TO_PT = 2.834645669f;

        static ImpositionExtras from(
                boolean bleedIndependent,
                float bleedTopMm,
                float bleedBottomMm,
                float bleedInsideMm,
                float bleedOutsideMm,
                boolean gripperEnabled,
                float gripperMm,
                boolean spineSignatureEnabled,
                String spineSignatureText,
                String cropMarkType,
                boolean registrationMarks,
                String jobName) {
            return new ImpositionExtras(
                    bleedIndependent,
                    Math.max(0f, bleedTopMm) * MM_TO_PT,
                    Math.max(0f, bleedBottomMm) * MM_TO_PT,
                    Math.max(0f, bleedInsideMm) * MM_TO_PT,
                    Math.max(0f, bleedOutsideMm) * MM_TO_PT,
                    gripperEnabled,
                    Math.max(0f, gripperMm) * MM_TO_PT,
                    spineSignatureEnabled,
                    spineSignatureText == null ? "" : spineSignatureText,
                    cropMarkType == null ? "NONE" : cropMarkType,
                    registrationMarks,
                    jobName == null ? "" : jobName);
        }
    }

    private static int padToMultipleOf4(int n) {
        return (n + 3) / 4 * 4;
    }

    private static class Side {
        final int left, right;
        final boolean isBack;

        Side(int left, int right, boolean isBack) {
            this.left = left;
            this.right = right;
            this.isBack = isBack;
        }
    }

    private static List<Side> saddleStitchSides(
            int totalPagesOriginal,
            boolean doubleSided,
            String duplexPass,
            boolean flipOnShortEdge) {
        int N = padToMultipleOf4(totalPagesOriginal);
        List<Side> out = new ArrayList<>();
        int sheets = N / 4;

        for (int s = 0; s < sheets; s++) {
            int a = N - 1 - (s * 2); // left, front
            int b = (s * 2); // right, front
            int c = (s * 2) + 1; // left, back
            int d = N - 2 - (s * 2); // right, back

            // clamp to -1 (blank) if >= totalPagesOriginal
            a = (a < totalPagesOriginal) ? a : -1;
            b = (b < totalPagesOriginal) ? b : -1;
            c = (c < totalPagesOriginal) ? c : -1;
            d = (d < totalPagesOriginal) ? d : -1;

            // Handle duplex pass selection
            boolean includeFront = "BOTH".equals(duplexPass) || "FIRST".equals(duplexPass);
            boolean includeBack = "BOTH".equals(duplexPass) || "SECOND".equals(duplexPass);

            if (includeFront) {
                out.add(new Side(a, b, false)); // front side
            }

            if (includeBack) {
                // For short-edge duplex, swap back-side left/right
                // Note: flipOnShortEdge is ignored in manual duplex mode since users physically
                // flip the stack
                if (doubleSided && flipOnShortEdge) {
                    out.add(new Side(d, c, true)); // swapped back side (automatic duplex only)
                } else {
                    out.add(new Side(c, d, true)); // normal back side
                }
            }
        }
        return out;
    }

    private PDDocument createSaddleBooklet(
            PDDocument src,
            int totalPages,
            boolean addBorder,
            String spineLocation,
            boolean addGutter,
            float gutterSize,
            boolean doubleSided,
            String duplexPass,
            boolean flipOnShortEdge,
            ImpositionExtras extras)
            throws IOException {

        PDDocument dst = pdfDocumentFactory.createNewDocumentBasedOnOldDocument(src);

        // Derive paper size from source document's first page CropBox
        PDRectangle srcBox = src.getPage(0).getCropBox();
        PDRectangle portraitPaper = new PDRectangle(srcBox.getWidth(), srcBox.getHeight());
        // Force landscape for booklet (Acrobat booklet uses landscape paper to fold to portrait)
        PDRectangle landscape =
                new PDRectangle(portraitPaper.getHeight(), portraitPaper.getWidth());

        // Margin envelope for marks/gripper. The press sheet expands so marks land outside
        // the trimmed area instead of overprinting the content. The trim box is what the
        // user originally asked for (landscape).
        float marginTop = 0f;
        float marginBottom = 0f;
        float marginLeft = 0f;
        float marginRight = 0f;
        boolean wantsMarks = !"NONE".equalsIgnoreCase(extras.cropMarkType()) || extras.registrationMarks();
        if (wantsMarks) {
            float bleedT = extras.bleedIndependent() ? extras.bleedTopPt() : (addGutter ? gutterSize : 0f);
            float bleedB = extras.bleedIndependent() ? extras.bleedBottomPt() : (addGutter ? gutterSize : 0f);
            float bleedI = extras.bleedIndependent() ? extras.bleedInsidePt() : (addGutter ? gutterSize : 0f);
            float bleedO = extras.bleedIndependent() ? extras.bleedOutsidePt() : (addGutter ? gutterSize : 0f);
            // Outer mark area = bleed + ~14pt for crop ticks
            float markPad = 14f;
            marginTop = bleedT + markPad;
            marginBottom = bleedB + markPad;
            marginLeft = bleedO + markPad;
            marginRight = bleedI + markPad;
        }
        if (extras.gripperEnabled()) {
            marginTop = Math.max(marginTop, extras.gripperPt());
        }

        PDRectangle pageSize =
                new PDRectangle(
                        landscape.getWidth() + marginLeft + marginRight,
                        landscape.getHeight() + marginTop + marginBottom);

        // Trim box (where the actual booklet content sits). Origin is shifted by left/bottom
        // margin so that (trimX, trimY) is the bottom-left of the original landscape spread.
        float trimX = marginLeft;
        float trimY = marginBottom;
        float trimW = landscape.getWidth();
        float trimH = landscape.getHeight();

        // Validate and clamp gutter size
        if (gutterSize < 0) gutterSize = 0;
        if (gutterSize >= trimW / 2f) gutterSize = trimW / 2f - 1f;

        List<Side> sides = saddleStitchSides(totalPages, doubleSided, duplexPass, flipOnShortEdge);

        for (Side side : sides) {
            PDPage out = new PDPage(pageSize);
            // Tag the trim/bleed boxes so downstream tools (PRINERGY etc.) know what to cut
            PDRectangle trimBox = new PDRectangle(trimX, trimY, trimW, trimH);
            out.setTrimBox(trimBox);
            if (extras.bleedIndependent()) {
                out.setBleedBox(
                        new PDRectangle(
                                trimX - extras.bleedOutsidePt(),
                                trimY - extras.bleedBottomPt(),
                                trimW + extras.bleedOutsidePt() + extras.bleedInsidePt(),
                                trimH + extras.bleedBottomPt() + extras.bleedTopPt()));
            }
            dst.addPage(out);

            float cellW = trimW / 2f;
            float cellH = trimH;

            // For RIGHT spine (RTL), swap left/right placements
            boolean rtl = "RIGHT".equalsIgnoreCase(spineLocation);
            int leftCol = rtl ? 1 : 0;
            int rightCol = rtl ? 0 : 1;

            // Apply gutter margins with centered gap option
            float g = addGutter ? gutterSize : 0f;
            float leftCellX = trimX + leftCol * cellW + (g / 2f);
            float rightCellX = trimX + rightCol * cellW - (g / 2f);
            float leftCellW = cellW - (g / 2f);
            float rightCellW = cellW - (g / 2f);

            // Create LayerUtility once per page for efficiency
            LayerUtility layerUtility = new LayerUtility(dst);

            try (PDPageContentStream cs =
                    new PDPageContentStream(
                            dst, out, PDPageContentStream.AppendMode.APPEND, true, true)) {

                if (addBorder) {
                    cs.setLineWidth(1.5f);
                    cs.setStrokingColor(Color.BLACK);
                }

                // draw left cell
                drawCell(
                        src,
                        dst,
                        cs,
                        layerUtility,
                        side.left,
                        leftCellX,
                        trimY,
                        leftCellW,
                        cellH,
                        addBorder);
                // draw right cell
                drawCell(
                        src,
                        dst,
                        cs,
                        layerUtility,
                        side.right,
                        rightCellX,
                        trimY,
                        rightCellW,
                        cellH,
                        addBorder);

                // Marks and signature overlays. Drawn last so they sit on top of content.
                drawMarks(cs, trimX, trimY, trimW, trimH, extras);
                drawSpineSignature(cs, trimX, trimY, trimW, trimH, side, extras);
            }
        }
        return dst;
    }

    /**
     * Draws crop / registration marks around the trim box. CUTTING / FOLDING / RECT all share the
     * same corner-tick geometry; only mark lengths differ. CENTER adds center marks at each edge
     * midpoint. NONE skips entirely.
     */
    private void drawMarks(
            PDPageContentStream cs,
            float trimX,
            float trimY,
            float trimW,
            float trimH,
            ImpositionExtras extras)
            throws IOException {
        String type = extras.cropMarkType();
        boolean drawCorners =
                "CUTTING".equalsIgnoreCase(type)
                        || "FOLDING".equalsIgnoreCase(type)
                        || "RECT".equalsIgnoreCase(type);
        boolean drawCenter = "CENTER".equalsIgnoreCase(type);
        if (!drawCorners && !drawCenter && !extras.registrationMarks()) {
            return;
        }

        cs.setStrokingColor(Color.BLACK);
        cs.setLineWidth(0.25f);

        float bleedT = extras.bleedIndependent() ? extras.bleedTopPt() : 0f;
        float bleedB = extras.bleedIndependent() ? extras.bleedBottomPt() : 0f;
        float bleedI = extras.bleedIndependent() ? extras.bleedInsidePt() : 0f;
        float bleedO = extras.bleedIndependent() ? extras.bleedOutsidePt() : 0f;
        float tickLen = "FOLDING".equalsIgnoreCase(type) ? 18f : 14f; // pt
        float gap = 3f; // gap between trim edge and start of tick

        float left = trimX;
        float right = trimX + trimW;
        float top = trimY + trimH;
        float bottom = trimY;

        if (drawCorners) {
            // Corner ticks (4 corners × 2 ticks each)
            // Bottom-left
            cs.moveTo(left - bleedO - gap, bottom);
            cs.lineTo(left - bleedO - gap - tickLen, bottom);
            cs.moveTo(left, bottom - bleedB - gap);
            cs.lineTo(left, bottom - bleedB - gap - tickLen);
            // Bottom-right
            cs.moveTo(right + bleedI + gap, bottom);
            cs.lineTo(right + bleedI + gap + tickLen, bottom);
            cs.moveTo(right, bottom - bleedB - gap);
            cs.lineTo(right, bottom - bleedB - gap - tickLen);
            // Top-left
            cs.moveTo(left - bleedO - gap, top);
            cs.lineTo(left - bleedO - gap - tickLen, top);
            cs.moveTo(left, top + bleedT + gap);
            cs.lineTo(left, top + bleedT + gap + tickLen);
            // Top-right
            cs.moveTo(right + bleedI + gap, top);
            cs.lineTo(right + bleedI + gap + tickLen, top);
            cs.moveTo(right, top + bleedT + gap);
            cs.lineTo(right, top + bleedT + gap + tickLen);

            if ("RECT".equalsIgnoreCase(type)) {
                // Add a rectangle around the bleed area for RECT mode
                cs.addRect(
                        left - bleedO,
                        bottom - bleedB,
                        trimW + bleedO + bleedI,
                        trimH + bleedT + bleedB);
            }
            cs.stroke();
        }

        if (drawCenter) {
            float cx = trimX + trimW / 2f;
            float cy = trimY + trimH / 2f;
            cs.moveTo(cx, top + bleedT + gap);
            cs.lineTo(cx, top + bleedT + gap + tickLen);
            cs.moveTo(cx, bottom - bleedB - gap);
            cs.lineTo(cx, bottom - bleedB - gap - tickLen);
            cs.moveTo(left - bleedO - gap, cy);
            cs.lineTo(left - bleedO - gap - tickLen, cy);
            cs.moveTo(right + bleedI + gap, cy);
            cs.lineTo(right + bleedI + gap + tickLen, cy);
            cs.stroke();
        }

        if (extras.registrationMarks()) {
            // Cross + circle at the four edge midpoints (outside bleed)
            float cx = trimX + trimW / 2f;
            float cy = trimY + trimH / 2f;
            float r = 4f;
            float armOff = bleedT + gap + 6f;
            // top
            drawRegistrationCross(cs, cx, top + armOff, r);
            drawRegistrationCross(cs, cx, bottom - bleedB - gap - 6f, r);
            drawRegistrationCross(cs, left - bleedO - gap - 6f, cy, r);
            drawRegistrationCross(cs, right + bleedI + gap + 6f, cy, r);
        }
    }

    private void drawRegistrationCross(PDPageContentStream cs, float x, float y, float r)
            throws IOException {
        cs.moveTo(x - r * 1.6f, y);
        cs.lineTo(x + r * 1.6f, y);
        cs.moveTo(x, y - r * 1.6f);
        cs.lineTo(x, y + r * 1.6f);
        // approximate circle with bezier (4-arc)
        float k = 0.5522847498f * r;
        cs.moveTo(x + r, y);
        cs.curveTo(x + r, y + k, x + k, y + r, x, y + r);
        cs.curveTo(x - k, y + r, x - r, y + k, x - r, y);
        cs.curveTo(x - r, y - k, x - k, y - r, x, y - r);
        cs.curveTo(x + k, y - r, x + r, y - k, x + r, y);
        cs.stroke();
    }

    /**
     * Draws spine signature text rotated 90° on the binding spine (between left/right cells in
     * 2-up). Placeholders %JobName% / %SignatureNo% / %ColorName% / %FB% expanded.
     */
    private void drawSpineSignature(
            PDPageContentStream cs,
            float trimX,
            float trimY,
            float trimW,
            float trimH,
            Side side,
            ImpositionExtras extras)
            throws IOException {
        if (!extras.spineSignatureEnabled()
                || extras.spineSignatureText() == null
                || extras.spineSignatureText().isBlank()) {
            return;
        }
        String text =
                extras.spineSignatureText()
                        .replace("%JobName%", extras.jobName())
                        .replace("%FB%", side.isBack ? "B" : "F")
                        .replace("%SignatureNo%", "")
                        .replace("%ColorName%", "");
        if (text.isBlank()) {
            return;
        }

        // Place near the spine (vertical center line) at the bottom edge, rotated 90°.
        float spineX = trimX + trimW / 2f;
        float baseY = trimY + 8f;
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7f);
        cs.setNonStrokingColor(Color.BLACK);
        cs.setTextMatrix(Matrix.getRotateInstance(Math.PI / 2f, spineX, baseY));
        cs.showText(text);
        cs.endText();
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
            // Draw border for blank cell if needed
            if (addBorder) {
                cs.addRect(cellX, cellY, cellW, cellH);
                cs.stroke();
            }
            return;
        }

        PDPage srcPage = src.getPage(pageIndex);
        PDRectangle r = srcPage.getCropBox(); // Use CropBox instead of MediaBox
        int rot = (srcPage.getRotation() + 360) % 360;

        // Calculate scale factors, accounting for rotation
        float sx = cellW / r.getWidth();
        float sy = cellH / r.getHeight();
        float s = Math.min(sx, sy);

        // If rotated 90/270 degrees, swap dimensions for fitting
        if (rot == 90 || rot == 270) {
            sx = cellW / r.getHeight();
            sy = cellH / r.getWidth();
            s = Math.min(sx, sy);
        }

        float drawnW = (rot == 90 || rot == 270) ? r.getHeight() * s : r.getWidth() * s;
        float drawnH = (rot == 90 || rot == 270) ? r.getWidth() * s : r.getHeight() * s;

        // Center in cell, accounting for CropBox offset
        float tx = cellX + (cellW - drawnW) / 2f - r.getLowerLeftX() * s;
        float ty = cellY + (cellH - drawnH) / 2f - r.getLowerLeftY() * s;

        cs.saveGraphicsState();
        cs.transform(Matrix.getTranslateInstance(tx, ty));
        cs.transform(Matrix.getScaleInstance(s, s));

        // Apply rotation if needed (rotate about origin), then translate to keep in cell
        switch (rot) {
            case 90:
                cs.transform(Matrix.getRotateInstance(Math.PI / 2, 0, 0));
                // After 90° CCW, the content spans x in [-r.getHeight(), 0] and y in [0,
                // r.getWidth()]
                cs.transform(Matrix.getTranslateInstance(0, -r.getWidth()));
                break;
            case 180:
                cs.transform(Matrix.getRotateInstance(Math.PI, 0, 0));
                cs.transform(Matrix.getTranslateInstance(-r.getWidth(), -r.getHeight()));
                break;
            case 270:
                cs.transform(Matrix.getRotateInstance(3 * Math.PI / 2, 0, 0));
                // After 270° CCW, the content spans x in [0, r.getHeight()] and y in
                // [-r.getWidth(), 0]
                cs.transform(Matrix.getTranslateInstance(-r.getHeight(), 0));
                break;
            default:
                // 0°: no-op
        }

        // Reuse LayerUtility passed from caller
        PDFormXObject form = layerUtility.importPageAsForm(src, pageIndex);
        cs.drawForm(form);

        cs.restoreGraphicsState();

        // Draw border on top of form to ensure visibility
        if (addBorder) {
            cs.addRect(cellX, cellY, cellW, cellH);
            cs.stroke();
        }
    }
}
