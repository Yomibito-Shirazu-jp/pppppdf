package stirling.software.SPDF.service.facilis;

/**
 * One {@code <Page>} entry inside a FACILIS {@code <LayoutItem_Pages>} block — i.e. the
 * specification "place document page N at this position on the plate, scaled and rotated thus".
 *
 * <p>Coordinates: origin is bottom-left of the plate (FACILIS uses {@code item_ref="BL"} with
 * {@code base_type="V_Left"} / {@code "H_Bottom"}). Length values are in PDF points after parser
 * conversion.
 */
public record FacilisPageSlot(
        /* 1-based document page number FACILIS expects to be placed in this slot. May exceed the
         * uploaded PDF's page count, in which case the slot is rendered blank. */
        int pageNum,
        /* Grid position (1-based) within the SLayout pages_h × pages_v grid. */
        int posH,
        int posV,
        /* Slot size in pt (the page area before bleed). */
        float widthPt,
        float heightPt,
        /* Bottom-left offset on the plate in pt. */
        float offsetXPt,
        float offsetYPt,
        /* Independent scale factors. Usually 1.0; FACILIS allows non-uniform scaling. */
        float scaleH,
        float scaleV,
        /* "Up" / "Down" / "Left" / "Right" — orientation of the page within the slot. */
        String orientation,
        /* Optional per-slot bleed override; falls back to layout-level bleed when null. */
        Float bleedTopPt,
        Float bleedBottomPt,
        Float bleedLeftPt,
        Float bleedRightPt) {}
