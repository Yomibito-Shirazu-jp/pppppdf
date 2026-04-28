package stirling.software.SPDF.controller.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.service.facilis.FacilisDbService;
import stirling.software.SPDF.service.facilis.FacilisLayout;
import stirling.software.common.annotations.api.GeneralApi;

/**
 * Endpoints for managing the imported FACILIS DB and listing available signature templates.
 *
 * <p>Lifecycle: customer uploads a {@code DB.zip} once; the server extracts it under
 * {@code stirling.pdf.facilisDbDir} (default {@code /customFiles/facilis-db}) and indexes every
 * {@code .lay}. The booklet imposition tool consumes a layout id from this list to drive the
 * actual page placement (Phase 4 executor — coming next).
 */
@Slf4j
@GeneralApi
@RequiredArgsConstructor
public class FacilisDbController {

    private final FacilisDbService dbService;

    @PostMapping(value = "/facilis-db/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import a FACILIS DB.zip",
            description =
                    "Uploads a FACILIS DB.zip (with the directory layout produced by FACILIS"
                        + " Supremo) and replaces the server-side template store. Returns the"
                        + " number of .lay layouts indexed.")
    public ResponseEntity<Map<String, Object>> uploadFacilisDb(
            @RequestParam("fileInput") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No file uploaded"));
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File must be a .zip"));
        }
        FacilisDbService.ImportResult result = dbService.importDbZip(file.getBytes());
        log.info(
                "FACILIS DB imported from {} ({} bytes) — {} .lay layouts indexed (extracted {} files, ext counts: {}, parse failures: {})",
                name,
                file.getSize(),
                result.indexedLayouts(),
                result.totalFilesExtracted(),
                result.extensionCounts(),
                result.parseFailures());

        if (result.indexedLayouts() == 0) {
            // Build a friendly diagnostic explaining what was uploaded vs. what's expected.
            StringBuilder breakdown = new StringBuilder();
            result.extensionCounts()
                    .forEach((ext, n) -> breakdown.append(".").append(ext).append("=").append(n).append(" "));
            String message;
            if (result.totalFilesExtracted() == 0) {
                message = "Zip is empty after extraction.";
            } else if (result.parseFailures() > 0) {
                message =
                        "Found "
                                + result.parseFailures()
                                + " .lay file(s), but none could be parsed. Are they current FACILIS Supremo layouts?";
            } else {
                message =
                        "No .lay (SignatureLayout) files found in the zip. Extracted "
                                + result.totalFilesExtracted()
                                + " file(s): "
                                + breakdown.toString().trim()
                                + ". Re-export the FACILIS DB including the Layout/SignatureLayout/ folder.";
            }
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "error", "no_layouts",
                                    "message", message,
                                    "indexedLayouts", 0,
                                    "totalFilesExtracted", result.totalFilesExtracted(),
                                    "parseFailures", result.parseFailures(),
                                    "extensionCounts", result.extensionCounts()));
        }

        return ResponseEntity.ok(
                Map.of(
                        "status", "ok",
                        "indexedLayouts", result.indexedLayouts(),
                        "totalFilesExtracted", result.totalFilesExtracted()));
    }

    @GetMapping("/facilis-db/layouts")
    @Operation(
            summary = "List imported FACILIS layouts",
            description =
                    "Returns the indexed signature layouts. Optional category prefix and name"
                            + " substring filters are matched case-insensitively.")
    public ResponseEntity<List<FacilisLayout>> listLayouts(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "q", required = false) String query) {
        if ((category == null || category.isBlank()) && (query == null || query.isBlank())) {
            return ResponseEntity.ok(dbService.listLayouts());
        }
        return ResponseEntity.ok(dbService.searchLayouts(category, query));
    }

    @GetMapping("/facilis-db/layout")
    @Operation(
            summary = "Get a single FACILIS layout by id",
            description =
                    "Layout id is the relative path within the imported DB; passed as a query"
                            + " parameter so it can contain slashes and Japanese characters.")
    public ResponseEntity<FacilisLayout> getLayout(@RequestParam("id") String id) {
        FacilisLayout layout = dbService.getLayout(id);
        if (layout == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(layout);
    }

    @GetMapping("/facilis-db/status")
    @Operation(summary = "FACILIS DB load status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(
                Map.of(
                        "loaded", dbService.isLoaded(),
                        "count", dbService.listLayouts().size()));
    }
}
