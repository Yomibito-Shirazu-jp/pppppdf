package stirling.software.SPDF.service.facilis;

import java.util.List;

/**
 * Parsed FACILIS .lay (signature layout) summary used for the imposition pipeline. Only fields the
 * executor and the listing API need are modelled; the rest of the .lay XML graph (color tables,
 * grid, mark EPS) is preserved verbatim on disk and will be loaded on demand by the executor.
 *
 * <p>All length values are in PDF points. FACILIS stores numbers as integer × 10000 (e.g. {@code
 * <Top int="269300" unit="point"/>} = 26.93 pt). Conversion happens in the parser so consumers
 * stay in standard units.
 */
public record FacilisLayout(
        /* Stable identifier used by API callers — the relative path within the imported DB
        * (e.g. "Layout/SignatureLayout/A版/その他/A2 2面（A4）16p7号機.lay"). Survives across uploads
        * as long as the customer does not rename/move templates. */
        String id,
        /* Display name from the .lay <LAYOUT name="..."> attribute. */
        String name,
        /* Category path derived from the directory hierarchy (e.g. "A版/その他", "四六版/中綴じ"). */
        String category,
        /* Press sheet dimensions in pt (front plate size; back is assumed identical for now). */
        float plateWidthPt,
        float plateHeightPt,
        /* Layout grid — pages_h × pages_v from <SLayout>. */
        int pagesH,
        int pagesV,
        /* Trim size of a single finished page in pt (from <FinishSize>). */
        float finishWidthPt,
        float finishHeightPt,
        /* Bleed in pt around each finished page (from <BleedWidths>). Single value when
         * same_all=true; otherwise the four values may differ. */
        float bleedTopPt,
        float bleedBottomPt,
        float bleedLeftPt,
        float bleedRightPt,
        /* Binding side from <SLayout bind_side="..."> ("Left"/"Right"/"Top"/"Bottom"/"Other"). */
        String bindSide,
        /* Flip direction for back plate: TopToBottom / LeftToRight / etc. */
        String flipDir,
        /* Per-page slots on the front plate. */
        List<FacilisPageSlot> frontPages,
        /* Per-page slots on the back plate. */
        List<FacilisPageSlot> backPages) {}
