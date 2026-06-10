package stirling.software.SPDF.model.api.general;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * Request for AI image comparison (Gemini Vision). Accepts two image files (PNG / JPG / WebP) or
 * single-page PDFs (server rasterizes them) and returns a Japanese diff report.
 */
@Data
public class ImageCompareRequest {

    @Schema(
            description = "Base image (original)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            type = "string",
            format = "binary")
    private MultipartFile baseImage;

    @Schema(
            description = "Comparison image (modified)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            type = "string",
            format = "binary")
    private MultipartFile comparisonImage;
}
