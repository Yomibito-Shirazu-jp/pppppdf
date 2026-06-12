package stirling.software.SPDF.controller.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
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
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.service.PdfJsonConversionService;
import stirling.software.SPDF.service.PdfToInDesignScriptService;
import stirling.software.common.annotations.AutoJobPostMapping;
import stirling.software.common.model.api.PDFFile;

/**
 * PDF → Adobe InDesign ExtendScript (.jsx) 変換エンドポイント。
 * Gemini (Vertex AI) でPDF構造JSONをInDesignスクリプトに変換する。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/general")
@Tag(name = "General", description = "General APIs")
@RequiredArgsConstructor
public class PdfToInDesignScriptController {

    private final PdfJsonConversionService pdfJsonConversionService;
    private final PdfToInDesignScriptService pdfToInDesignScriptService;

    @AutoJobPostMapping(value = "/pdf-to-indesign-script", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "PDF to Adobe InDesign Script (PDFからInDesignスクリプト生成)",
            description =
                    "Converts a PDF to an Adobe InDesign ExtendScript (.jsx) file using Gemini AI. "
                            + "The generated script recreates the PDF layout in InDesign, "
                            + "including vertical Japanese text (縦書き), CMYK colors, and text frames. "
                            + "Requires Google Cloud (Vertex AI) configuration. "
                            + "Input:PDF Output:JSX Type:SISO")
    public ResponseEntity<byte[]> convertPdfToInDesignScript(
            @ModelAttribute PDFFile request) throws Exception {

        MultipartFile inputFile = request.getFileInput();
        if (inputFile == null) {
            return ResponseEntity.badRequest().build();
        }

        if (!pdfToInDesignScriptService.isAvailable()) {
            return ResponseEntity.status(503)
                    .body("Gemini (Google Cloud) is not configured on this server.".getBytes(StandardCharsets.UTF_8));
        }

        // PDF → 軽量JSON（画像なし）
        byte[] jsonBytes = pdfJsonConversionService.convertPdfToJson(inputFile, true);
        String pdfJson = new String(jsonBytes, StandardCharsets.UTF_8);

        // Gemini でスクリプト生成
        String script = pdfToInDesignScriptService.generateScript(pdfJson);

        // ファイル名
        String originalName = inputFile.getOriginalFilename();
        String baseName = (originalName != null && !originalName.isBlank())
                ? Filenames.toSimpleFileName(originalName).replaceFirst("\\.[^.]+$", "")
                : "output";

        byte[] scriptBytes = script.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + baseName + "_indesign.jsx\"")
                .contentType(MediaType.parseMediaType("application/javascript"))
                .contentLength(scriptBytes.length)
                .body(scriptBytes);
    }
}
