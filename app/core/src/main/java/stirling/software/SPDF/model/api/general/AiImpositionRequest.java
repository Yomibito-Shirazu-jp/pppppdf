package stirling.software.SPDF.model.api.general;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

import stirling.software.common.model.api.PDFFile;

/**
 * Request for signature-based ("折面付") imposition. Like Booklet Imposition, but pages are grouped
 * into signatures of {@code signaturePages} (4 / 8 / 16 / 32) and saddle-stitched within each
 * signature. Multiple signatures can then be gathered and bound (perfect binding / sewn binding).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiImpositionRequest extends PDFFile {

    @Schema(
            description =
                    "Number of pages per signature (折ページ数). Must be a multiple of 4. Typical"
                            + " values: 4, 8, 16, 32. Larger = thicker signature, fewer signatures"
                            + " to bind; smaller = thinner signature, easier to fold but more"
                            + " signatures.",
            type = "number",
            defaultValue = "16",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"4", "8", "16", "32"})
    private int signaturePages = 16;

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
}
