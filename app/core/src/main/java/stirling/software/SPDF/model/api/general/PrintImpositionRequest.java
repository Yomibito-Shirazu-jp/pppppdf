package stirling.software.SPDF.model.api.general;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

import stirling.software.common.model.api.PDFFile;

@Data
@EqualsAndHashCode(callSuper = true)
public class PrintImpositionRequest extends PDFFile {

    @Schema(
            description =
                    "Imposition layout. 'folio' = 4p/sig, 'quarto' = 8p/sig (default), "
                            + "'octavo' = 16p/sig, 'card-fold4' / 'card-fold8' for simple pamphlets.",
            type = "string",
            defaultValue = "quarto",
            allowableValues = {"folio", "quarto", "octavo", "card-fold4", "card-fold8"})
    private String layout = "quarto";

    @Schema(
            description = "Sheet orientation: 'portrait' or 'landscape'.",
            type = "string",
            defaultValue = "portrait",
            allowableValues = {"portrait", "landscape"})
    private String orientation = "portrait";

    @Schema(
            description =
                    "Output sheet dimensions. A known paper size (A3, A4, A5, LETTER, LEGAL, B4, B5) "
                            + "or 'WxH' in points, e.g. '1190x842'. "
                            + "If blank, the sheet size is derived from the source document.",
            type = "string",
            defaultValue = "A4")
    private String dimensions = "A4";

    @Schema(
            description =
                    "Number of physical sheets per signature. 0 = use the layout default "
                            + "(folio/quarto/octavo determine signaturePages = 4 * forms).",
            type = "number",
            defaultValue = "0")
    private int forms = 0;

    @Schema(
            description = "Minimum margin around each page cell, in points. 36 ≈ 12.7 mm.",
            type = "number",
            defaultValue = "36")
    private int margin = 36;

    @Schema(description = "Include registration / crop marks (トンボ).", defaultValue = "true")
    private Boolean marks = true;

    @Schema(
            description = "Page number to start imposing at (1-indexed).",
            type = "number",
            defaultValue = "1")
    private int startPage = 1;

    @Schema(
            description = "Page number to stop imposing at (inclusive). 0 = end of document.",
            type = "number",
            defaultValue = "0")
    private int endPage = 0;

    @Schema(
            description =
                    "Mirror (swap) the left/right pages on back-side sheets. "
                            + "Enable when the plate or duplex workflow flips the sheet on the short edge "
                            + "and back pages appear reflected.",
            defaultValue = "false")
    private Boolean mirrorBackSide = false;
}
