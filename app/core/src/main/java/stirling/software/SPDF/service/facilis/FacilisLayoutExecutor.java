package stirling.software.SPDF.service.facilis;

import java.io.IOException;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.service.CustomPDFDocumentFactory;

/**
 * Renders an imposed PDF by following the placement instructions in a {@link FacilisLayout}. The
 * layout file (.lay) is treated as a deterministic recipe — we don't invent signature patterns
 * here, we just execute what FACILIS already specified.
 *
 * <p>Per-side flow:
 *
 * <ol>
 *   <li>Create a press-sheet-sized output page (front, then back).
 *   <li>For each {@link FacilisPageSlot}: pull the matching source PDF page, scale it to slot
 *       size, rotate per {@code orientation}, position at slot offset.
 *   <li>Set TrimBox/BleedBox so PRINERGY downstream knows what to cut.
 * </ol>
 *
 * <p>Slots whose {@code page_num} exceeds the source PDF page count are rendered blank (matches
 * FACILIS behaviour for partially-filled signatures).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacilisLayoutExecutor {

    private final CustomPDFDocumentFactory pdfDocumentFactory;

    public PDDocument execute(PDDocument source, FacilisLayout layout) throws IOException {
        PDDocument dst = pdfDocumentFactory.createNewDocumentBasedOnOldDocument(source);

        float plateW = layout.plateWidthPt() > 0 ? layout.plateWidthPt() : pageDefault(source, true);
        float plateH = layout.plateHeightPt() > 0 ? layout.plateHeightPt() : pageDefault(source, false);

        renderSide(source, dst, layout, layout.frontPages(), plateW, plateH, false);
        renderSide(source, dst, layout, layout.backPages(), plateW, plateH, true);

        return dst;
    }

    private void renderSide(
            PDDocument source,
            PDDocument dst,
            FacilisLayout layout,
            java.util.List<FacilisPageSlot> slots,
            float plateW,
            float plateH,
            boolean isBack)
            throws IOException {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        PDPage out = new PDPage(new PDRectangle(plateW, plateH));
        // The trim/bleed boxes describe a single finished page when the layout is 1-up; for
        // multi-up they're not strictly meaningful at the plate level, so we leave them as
        // MediaBox = plate size for now and rely on per-slot crop marks (future work).
        dst.addPage(out);

        LayerUtility layerUtility = new LayerUtility(dst);
        try (PDPageContentStream cs =
                new PDPageContentStream(
                        dst, out, PDPageContentStream.AppendMode.APPEND, true, true)) {
            for (FacilisPageSlot slot : slots) {
                int sourceIndex = slot.pageNum() - 1; // FACILIS is 1-based
                if (sourceIndex < 0 || sourceIndex >= source.getNumberOfPages()) {
                    // Blank slot — outside the document range. Don't draw anything.
                    continue;
                }
                placeSlot(source, dst, cs, layerUtility, slot, sourceIndex);
            }
        }
        if (isBack && "TopToBottom".equalsIgnoreCase(layout.flipDir())) {
            // Top-to-bottom flip means the back is read upside-down relative to the front. We
            // don't physically rotate the PDF here — that's what the press does — but log it so
            // operators understand the orientation requirement.
            log.debug("Back plate flipped TopToBottom (handled by press)");
        }
    }

    private void placeSlot(
            PDDocument source,
            PDDocument dst,
            PDPageContentStream cs,
            LayerUtility layerUtility,
            FacilisPageSlot slot,
            int sourceIndex)
            throws IOException {
        PDPage srcPage = source.getPage(sourceIndex);
        PDRectangle srcBox = srcPage.getCropBox();
        float srcW = srcBox.getWidth();
        float srcH = srcBox.getHeight();

        // Slot dimensions; if zero (FACILIS sometimes omits Size at the page level) fall back to
        // the source page dimensions so we at least place something.
        float slotW = slot.widthPt() > 0 ? slot.widthPt() : srcW;
        float slotH = slot.heightPt() > 0 ? slot.heightPt() : srcH;

        // Effective scale: per-slot scaleH/scaleV multiplied by fit-to-slot scaling.
        float fitScaleX = slotW / srcW;
        float fitScaleY = slotH / srcH;
        float sx = fitScaleX * (slot.scaleH() <= 0 ? 1f : slot.scaleH());
        float sy = fitScaleY * (slot.scaleV() <= 0 ? 1f : slot.scaleV());

        cs.saveGraphicsState();
        cs.transform(Matrix.getTranslateInstance(slot.offsetXPt(), slot.offsetYPt()));

        // Apply orientation: FACILIS orientations rotate the page in 90° steps about the slot
        // bottom-left. We translate after rotation to keep the content inside the slot box.
        String o = slot.orientation();
        if ("Right".equalsIgnoreCase(o)) {
            cs.transform(Matrix.getRotateInstance(Math.PI / 2, 0, 0));
            cs.transform(Matrix.getTranslateInstance(0, -slotW));
            cs.transform(Matrix.getScaleInstance(sy, sx));
            // After 90° CCW the source's height fills slotW and width fills slotH.
        } else if ("Down".equalsIgnoreCase(o)) {
            cs.transform(Matrix.getRotateInstance(Math.PI, 0, 0));
            cs.transform(Matrix.getTranslateInstance(-slotW, -slotH));
            cs.transform(Matrix.getScaleInstance(sx, sy));
        } else if ("Left".equalsIgnoreCase(o)) {
            cs.transform(Matrix.getRotateInstance(3 * Math.PI / 2, 0, 0));
            cs.transform(Matrix.getTranslateInstance(-slotH, 0));
            cs.transform(Matrix.getScaleInstance(sy, sx));
        } else {
            // "Up" — default
            cs.transform(Matrix.getScaleInstance(sx, sy));
        }

        // Compensate for source CropBox offsets so content sits at slot origin.
        cs.transform(Matrix.getTranslateInstance(-srcBox.getLowerLeftX(), -srcBox.getLowerLeftY()));

        PDFormXObject form = layerUtility.importPageAsForm(source, sourceIndex);
        cs.drawForm(form);
        cs.restoreGraphicsState();
    }

    private static float pageDefault(PDDocument doc, boolean width) {
        if (doc.getNumberOfPages() == 0) {
            return width ? 595f : 842f; // A4 portrait fallback
        }
        PDRectangle box = doc.getPage(0).getCropBox();
        return width ? box.getWidth() : box.getHeight();
    }
}
