package stirling.software.SPDF.controller.api.converters;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;

import io.github.pixee.security.Filenames;
import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.config.swagger.StandardPdfResponse;
import stirling.software.common.annotations.AutoJobPostMapping;
import stirling.software.common.annotations.api.ConvertApi;
import stirling.software.common.configuration.RuntimePathConfig;
import stirling.software.common.model.api.converters.HTMLToPdfRequest;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.*;

@ConvertApi
@RequiredArgsConstructor
@Slf4j
public class ConvertHtmlToPDF {

    private final CustomPDFDocumentFactory pdfDocumentFactory;

    private final RuntimePathConfig runtimePathConfig;

    private final TempFileManager tempFileManager;

    private final CustomHtmlSanitizer customHtmlSanitizer;

    @AutoJobPostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, value = "/html/pdf")
    @StandardPdfResponse
    @Operation(
            summary = "Convert an HTML or ZIP (containing HTML and CSS) to PDF",
            description =
                    "This endpoint takes an HTML or ZIP file input and converts it to a PDF format."
                            + " Input:HTML Output:PDF Type:SISO")
    public ResponseEntity<byte[]> HtmlToPdf(@ModelAttribute HTMLToPdfRequest request)
            throws Exception {
        MultipartFile fileInput = request.getFileInput();

        if (fileInput == null) {
            throw ExceptionUtils.createHtmlFileRequiredException();
        }

        String originalFilename = Filenames.toSimpleFileName(fileInput.getOriginalFilename());
        if (originalFilename == null
                || (!originalFilename.endsWith(".html") && !originalFilename.endsWith(".zip"))) {
            throw ExceptionUtils.createIllegalArgumentException(
                    "error.fileFormatRequired", "File must be in {0} format", ".html or .zip");
        }

        byte[] inputBytes = fileInput.getBytes();
        byte[] pdfBytes =
                renderHtmlToPdf(request, inputBytes, originalFilename);

        pdfBytes = pdfDocumentFactory.createNewBytesBasedOnOldDocument(pdfBytes);

        String outputFilename = GeneralUtils.generateFilename(originalFilename, ".pdf");

        return WebResponseUtils.bytesToWebResponse(pdfBytes, outputFilename);
    }

    /**
     * Routes HTML→PDF rendering between WeasyPrint and Vivliostyle. Vivliostyle is preferred when
     * the input declares a vertical CSS `writing-mode` (Japanese 縦書き etc.) since WeasyPrint's
     * vertical-flow support is limited. Falls back to WeasyPrint if the Vivliostyle CLI is not
     * installed, so deployments that haven't added it keep the existing behavior.
     */
    private byte[] renderHtmlToPdf(HTMLToPdfRequest request, byte[] inputBytes, String filename)
            throws Exception {
        boolean vertical = FileToPdf.containsVerticalWritingMode(inputBytes, filename);

        if (vertical && isVivliostyleAvailable()) {
            log.info(
                    "Vertical writing-mode detected in '{}'; rendering with Vivliostyle.",
                    filename);
            try {
                return FileToPdf.convertHtmlToPdfViaVivliostyle(
                        runtimePathConfig.getVivliostylePath(),
                        request,
                        inputBytes,
                        filename,
                        tempFileManager,
                        customHtmlSanitizer);
            } catch (Exception e) {
                log.warn(
                        "Vivliostyle render failed for '{}'; falling back to WeasyPrint: {}",
                        filename,
                        e.getMessage());
            }
        } else if (vertical) {
            log.warn(
                    "Vertical writing-mode detected in '{}' but Vivliostyle CLI not available;"
                            + " falling back to WeasyPrint (vertical layout may render poorly).",
                    filename);
        }

        return FileToPdf.convertHtmlToPdf(
                runtimePathConfig.getWeasyPrintPath(),
                request,
                inputBytes,
                filename,
                tempFileManager,
                customHtmlSanitizer);
    }

    private boolean isVivliostyleAvailable() {
        String path = runtimePathConfig.getVivliostylePath();
        if (path == null || path.isBlank()) {
            return false;
        }
        // If the configured path is absolute, just check the file exists.
        java.io.File asFile = new java.io.File(path);
        if (asFile.isAbsolute()) {
            return asFile.canExecute();
        }
        // Otherwise probe PATH for the binary (works for "vivliostyle" / "vivliostyle.cmd").
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return false;
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            if (dir.isEmpty()) {
                continue;
            }
            java.io.File candidate = new java.io.File(dir, path);
            if (candidate.canExecute()) {
                return true;
            }
            // Windows convenience
            java.io.File withCmd = new java.io.File(dir, path + ".cmd");
            if (withCmd.canExecute()) {
                return true;
            }
        }
        return false;
    }
}
