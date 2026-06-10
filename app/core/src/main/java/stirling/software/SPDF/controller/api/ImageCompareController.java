package stirling.software.SPDF.controller.api;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.model.api.general.ImageCompareRequest;
import stirling.software.SPDF.service.handwriting.GeminiImageCompareClient;
import stirling.software.common.annotations.AutoJobPostMapping;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.ExceptionUtils;
import stirling.software.common.util.WebResponseUtils;

/**
 * AI 画像比較 — sends two images to Gemini Vision and returns a ZIP containing the textual diff
 * report (Japanese), the two rasterised input images, a pixel-difference visualisation that
 * highlights all color/content changes in red, and a side-by-side composite for quick review.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/general")
@Tag(name = "General", description = "General APIs")
@RequiredArgsConstructor
public class ImageCompareController {

    private static final int PDF_RASTER_DPI = 200;
    /** Per-channel RGB delta below which a pixel is treated as unchanged (≈ JPEG noise floor). */
    private static final int DIFF_THRESHOLD = 24;

    private final GeminiImageCompareClient geminiImageCompareClient;
    private final CustomPDFDocumentFactory pdfDocumentFactory;

    @AutoJobPostMapping(value = "/image-compare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "AI image comparison via Gemini Vision + pixel diff",
            description =
                    "Compares two images (PNG/JPG/WebP) or first-page PDFs. Returns a ZIP with:"
                            + " original A, original B, a color-diff heatmap (changes highlighted"
                            + " in red over a grayscale base), a side-by-side composite, and a"
                            + " Japanese text report from Gemini Vision describing all differences."
                            + " Requires googleCloud.gemini.enabled=true. Input:Image+Image"
                            + " Output:ZIP Type:SISO")
    public ResponseEntity<byte[]> compareImages(@ModelAttribute ImageCompareRequest request)
            throws IOException {

        if (!geminiImageCompareClient.isAvailable()) {
            throw ExceptionUtils.createRuntimeException(
                    "error.imageCompareUnavailable",
                    "Image comparison requires Gemini. Set googleCloud.enabled=true and"
                            + " googleCloud.gemini.enabled=true with a valid project, and ensure"
                            + " the runtime service account has roles/aiplatform.user.",
                    null);
        }

        MultipartFile baseFile = request.getBaseImage();
        MultipartFile compFile = request.getComparisonImage();
        if (baseFile == null || compFile == null) {
            throw ExceptionUtils.createRuntimeException(
                    "error.imageCompareMissingFiles",
                    "Both baseImage and comparisonImage are required.",
                    null);
        }

        byte[] pngA = toPngBytes(baseFile);
        byte[] pngB = toPngBytes(compFile);

        log.info(
                "AI image compare: base={} ({} bytes), comparison={} ({} bytes)",
                baseFile.getOriginalFilename(),
                pngA.length,
                compFile.getOriginalFilename(),
                pngB.length);

        BufferedImage imgA = ImageIO.read(new ByteArrayInputStream(pngA));
        BufferedImage imgB = ImageIO.read(new ByteArrayInputStream(pngB));
        if (imgA == null || imgB == null) {
            throw ExceptionUtils.createRuntimeException(
                    "error.imageCompareInvalidImage",
                    "Could not decode one of the inputs as a raster image.",
                    null);
        }

        // Normalise both to the larger common canvas so pixel diff is well-defined.
        int targetW = Math.max(imgA.getWidth(), imgB.getWidth());
        int targetH = Math.max(imgA.getHeight(), imgB.getHeight());
        BufferedImage normA = resizeTo(imgA, targetW, targetH);
        BufferedImage normB = resizeTo(imgB, targetW, targetH);

        BufferedImage diff = buildColorDiff(normA, normB);
        BufferedImage sideBySide = buildSideBySide(normA, normB);

        String report;
        try {
            report = geminiImageCompareClient.compare(pngA, pngB, "image/png");
        } catch (RuntimeException | IOException ex) {
            log.warn("Gemini compare failed, returning pixel-diff only", ex);
            report =
                    "Gemini Vision での比較に失敗しました。色変更・追加・削除などのピクセル差分は "
                            + "color_diff.png をご覧ください。\n\n詳細: "
                            + ex.getMessage();
        }

        ByteArrayOutputStream zipBuf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipBuf)) {
            writeEntry(zip, "01_image_a.png", pngA);
            writeEntry(zip, "02_image_b.png", pngB);
            writeEntry(zip, "03_color_diff.png", toPngBytes(diff));
            writeEntry(zip, "04_side_by_side.png", toPngBytes(sideBySide));
            writeEntry(zip, "05_report.txt", report.getBytes(StandardCharsets.UTF_8));
        }

        return WebResponseUtils.bytesToWebResponse(
                zipBuf.toByteArray(),
                "image_compare_result.zip",
                MediaType.APPLICATION_OCTET_STREAM);
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    /**
     * If the file is a PDF, rasterise the first page to PNG. Otherwise re-encode as PNG so the
     * output ZIP has a uniform format the frontend can preview.
     */
    private byte[] toPngBytes(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        boolean isPdf =
                (name != null && name.toLowerCase().endsWith(".pdf"))
                        || "application/pdf".equalsIgnoreCase(file.getContentType());

        if (isPdf) {
            try (PDDocument doc = pdfDocumentFactory.load(file)) {
                PDFRenderer renderer = new PDFRenderer(doc);
                renderer.setSubsamplingAllowed(true);
                BufferedImage image = renderer.renderImageWithDPI(0, PDF_RASTER_DPI);
                return toPngBytes(image);
            }
        }

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
        if (img == null) {
            // Probably already PNG/JPEG/WebP that ImageIO can't decode (e.g. WebP without plugin).
            // Pass the original bytes through; Gemini still accepts them.
            return file.getBytes();
        }
        return toPngBytes(img);
    }

    private static byte[] toPngBytes(BufferedImage img) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        }
    }

    private static BufferedImage resizeTo(BufferedImage src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /**
     * Color-difference visualisation: pixels with no meaningful change are dimmed to a faint
     * grayscale of image B; pixels that changed are tinted red with opacity proportional to the
     * per-channel RGB delta. Lets the user see what changed *and* where on the page it sits.
     */
    private static BufferedImage buildColorDiff(BufferedImage a, BufferedImage b) {
        int w = a.getWidth();
        int h = a.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgbA = a.getRGB(x, y);
                int rgbB = b.getRGB(x, y);

                int rA = (rgbA >> 16) & 0xFF, gA = (rgbA >> 8) & 0xFF, bA = rgbA & 0xFF;
                int rB = (rgbB >> 16) & 0xFF, gB = (rgbB >> 8) & 0xFF, bB = rgbB & 0xFF;

                int dr = Math.abs(rA - rB);
                int dg = Math.abs(gA - gB);
                int db = Math.abs(bA - bB);
                int maxDelta = Math.max(dr, Math.max(dg, db));

                if (maxDelta <= DIFF_THRESHOLD) {
                    // Unchanged: faint grayscale of B (so changed pixels visually pop).
                    int gray = (int) (0.299 * rB + 0.587 * gB + 0.114 * bB);
                    int dim = (gray + 510) / 3; // brightens toward white
                    out.setRGB(x, y, (dim << 16) | (dim << 8) | dim);
                } else {
                    // Changed: emphasise. Red intensity ∝ delta, mixed over a darkened B.
                    float t = Math.min(1f, (maxDelta - DIFF_THRESHOLD) / 200f);
                    int baseR = (int) (rB * 0.4f);
                    int baseG = (int) (gB * 0.4f);
                    int baseB = (int) (bB * 0.4f);
                    int red = clamp((int) (baseR + (255 - baseR) * t));
                    int grn = clamp((int) (baseG * (1 - t)));
                    int blu = clamp((int) (baseB * (1 - t)));
                    out.setRGB(x, y, (red << 16) | (grn << 8) | blu);
                }
            }
        }
        return out;
    }

    private static BufferedImage buildSideBySide(BufferedImage a, BufferedImage b) {
        int gap = Math.max(8, a.getWidth() / 80);
        int w = a.getWidth() + b.getWidth() + gap;
        int h = Math.max(a.getHeight(), b.getHeight());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(a, 0, 0, null);
        g.drawImage(b, a.getWidth() + gap, 0, null);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(80, 80, 80));
        g.fillRect(a.getWidth(), 0, gap, h);
        g.dispose();
        return out;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
