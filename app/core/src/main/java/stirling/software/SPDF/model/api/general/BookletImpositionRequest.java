package stirling.software.SPDF.model.api.general;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

import stirling.software.common.model.api.PDFFile;

@Data
@EqualsAndHashCode(callSuper = true)
public class BookletImpositionRequest extends PDFFile {

    @Schema(
            description =
                    "Pages per press sheet for the imposition. 2 = saddle-stitch booklet,"
                            + " 4/8/16/32 = signature folds for perfect / Smyth-sewn binding.",
            type = "number",
            defaultValue = "2",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"2", "4", "8", "16", "32"})
    private int pagesPerSheet = 2;

    @Schema(description = "Boolean for if you wish to add border around the pages")
    private Boolean addBorder = false;

    @Schema(
            description = "The spine location for the booklet.",
            type = "string",
            defaultValue = "LEFT",
            allowableValues = {"LEFT", "RIGHT"})
    private String spineLocation = "LEFT";

    @Schema(description = "Add gutter margin (inner margin for binding)")
    private Boolean addGutter = false;

    @Schema(
            description = "Gutter margin size in points (used when addGutter is true)",
            type = "number",
            defaultValue = "12")
    private float gutterSize = 12f;

    @Schema(description = "Generate both front and back sides (double-sided printing)")
    private Boolean doubleSided = true;

    @Schema(
            description = "For manual duplex: which pass to generate",
            type = "string",
            defaultValue = "BOTH",
            allowableValues = {"BOTH", "FIRST", "SECOND"})
    private String duplexPass = "BOTH";

    @Schema(description = "Flip back sides for short-edge duplex printing (default is long-edge)")
    private Boolean flipOnShortEdge = false;

    // ── ドブ (bleed) ── independent 4-side bleed in mm; falls back to gutterSize when off
    @Schema(description = "Specify bleed independently per edge (top/bottom/inside/outside)")
    private Boolean bleedIndependent = false;

    @Schema(description = "Bleed at the top edge (mm)", defaultValue = "3")
    private float bleedTopMm = 3f;

    @Schema(description = "Bleed at the bottom edge (mm)", defaultValue = "3")
    private float bleedBottomMm = 3f;

    @Schema(description = "Bleed at the inside (binding) edge (mm)", defaultValue = "3")
    private float bleedInsideMm = 3f;

    @Schema(description = "Bleed at the outside (open) edge (mm)", defaultValue = "3")
    private float bleedOutsideMm = 3f;

    // ── 咥え (gripper edge) ── press gripper margin
    @Schema(description = "Reserve a gripper edge on the plate top")
    private Boolean gripperEnabled = false;

    @Schema(description = "Gripper edge size in mm", defaultValue = "10")
    private float gripperMm = 10f;

    // ── 背丁 (spine signature) ── text placed on the spine for signature collation
    @Schema(description = "Print spine signature text on the binding edge")
    private Boolean spineSignatureEnabled = false;

    @Schema(
            description =
                    "Spine signature text. Supports placeholders %JobName% / %SignatureNo% /"
                            + " %ColorName% / %FB%.",
            defaultValue = "%JobName% %SignatureNo%")
    private String spineSignatureText = "%JobName% %SignatureNo%";

    // ── トンボ／見当 (crop / registration marks)
    @Schema(
            description =
                    "Crop mark style. NONE / CENTER / CUTTING (trim) / FOLDING / RECT.",
            type = "string",
            defaultValue = "NONE",
            allowableValues = {"NONE", "CENTER", "CUTTING", "FOLDING", "RECT"})
    private String cropMarkType = "NONE";

    @Schema(description = "Add registration (cross) marks at the four edges")
    private Boolean registrationMarks = false;

    // ── FACILIS .lay template (optional) — when set, overrides manual settings
    @Schema(
            description =
                    "Optional FACILIS .lay template id. When set, the imposition follows the"
                            + " template definition and the manual fields above are ignored.")
    private String templateId;
}
