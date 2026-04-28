package stirling.software.common.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class FileToPdfTest {

    @Test
    void testSanitizeZipFilename_normalFilename() {
        String result = FileToPdf.sanitizeZipFilename("document.html");
        assertEquals("document.html", result);
    }

    @Test
    void testSanitizeZipFilename_pathTraversal() {
        String result = FileToPdf.sanitizeZipFilename("../../etc/passwd");
        // Should remove ../ sequences
        assertFalse(result.contains(".."), "Path traversal sequences should be removed");
    }

    @Test
    void testSanitizeZipFilename_driveLetterRemoved() {
        String result = FileToPdf.sanitizeZipFilename("C:\\Users\\test\\file.html");
        assertFalse(result.startsWith("C:"), "Drive letter should be removed");
    }

    @Test
    void testSanitizeZipFilename_backslashesNormalized() {
        String result = FileToPdf.sanitizeZipFilename("path\\to\\file.html");
        assertFalse(result.contains("\\"), "Backslashes should be normalized to forward slashes");
        assertTrue(result.contains("/") || !result.contains("\\"));
    }

    @Test
    void testSanitizeZipFilename_nullInput() {
        String result = FileToPdf.sanitizeZipFilename(null);
        assertEquals("", result, "Null input should return empty string");
    }

    @Test
    void testSanitizeZipFilename_emptyInput() {
        String result = FileToPdf.sanitizeZipFilename("");
        assertEquals("", result, "Empty input should return empty string");
    }

    @Test
    void testSanitizeZipFilename_whitespaceOnly() {
        String result = FileToPdf.sanitizeZipFilename("   ");
        assertEquals("", result, "Whitespace-only input should return empty string");
    }

    @Test
    void testSanitizeZipFilename_leadingSlashes() {
        String result = FileToPdf.sanitizeZipFilename("///path/to/file.html");
        assertFalse(result.startsWith("/"), "Leading slashes should be removed");
    }

    @Test
    void testSanitizeZipFilename_nestedDirectories() {
        String result = FileToPdf.sanitizeZipFilename("dir1/dir2/file.html");
        assertEquals("dir1/dir2/file.html", result, "Normal nested paths should be preserved");
    }

    @Test
    void testSanitizeZipFilename_mixedTraversal() {
        String result = FileToPdf.sanitizeZipFilename("dir/../../../etc/passwd");
        assertFalse(result.contains(".."), "Mixed path traversal should be removed");
    }

    @Test
    void testSanitizeZipFilename_backslashTraversal() {
        String result = FileToPdf.sanitizeZipFilename("dir\\..\\..\\etc\\passwd");
        assertFalse(result.contains(".."), "Backslash path traversal should be removed");
    }

    @Test
    void testContainsVerticalWritingMode_verticalRl() {
        byte[] html = ("<style>body { writing-mode: vertical-rl; }</style>")
                .getBytes(StandardCharsets.UTF_8);
        assertTrue(FileToPdf.containsVerticalWritingMode(html, "page.html"));
    }

    @Test
    void testContainsVerticalWritingMode_verticalLr() {
        byte[] html = ("p { writing-mode:vertical-lr ; }").getBytes(StandardCharsets.UTF_8);
        assertTrue(FileToPdf.containsVerticalWritingMode(html, "page.html"));
    }

    @Test
    void testContainsVerticalWritingMode_sidewaysRl() {
        byte[] html = ("h1 { writing-mode: sideways-rl; }").getBytes(StandardCharsets.UTF_8);
        assertTrue(FileToPdf.containsVerticalWritingMode(html, "page.html"));
    }

    @Test
    void testContainsVerticalWritingMode_inlineStyle() {
        byte[] html = ("<div style=\"writing-mode: vertical-rl;\">縦書き</div>")
                .getBytes(StandardCharsets.UTF_8);
        assertTrue(FileToPdf.containsVerticalWritingMode(html, "page.html"));
    }

    @Test
    void testContainsVerticalWritingMode_legacyTbRl() {
        byte[] html = ("body { writing-mode: tb-rl; }").getBytes(StandardCharsets.UTF_8);
        assertTrue(FileToPdf.containsVerticalWritingMode(html, "page.html"));
    }

    @Test
    void testContainsVerticalWritingMode_horizontalNotMatched() {
        byte[] html = ("body { writing-mode: horizontal-tb; }").getBytes(StandardCharsets.UTF_8);
        assertFalse(FileToPdf.containsVerticalWritingMode(html, "page.html"));
    }

    @Test
    void testContainsVerticalWritingMode_noWritingModeNotMatched() {
        byte[] html = ("<p>普通の横書きテキスト</p>").getBytes(StandardCharsets.UTF_8);
        assertFalse(FileToPdf.containsVerticalWritingMode(html, "page.html"));
    }

    @Test
    void testContainsVerticalWritingMode_cssFile() {
        byte[] css = ("body { writing-mode: vertical-rl; }").getBytes(StandardCharsets.UTF_8);
        assertTrue(FileToPdf.containsVerticalWritingMode(css, "style.css"));
    }

    @Test
    void testContainsVerticalWritingMode_inZipCss() throws Exception {
        byte[] zip = makeZip(
                "index.html", "<link rel=\"stylesheet\" href=\"style.css\"><p>あ</p>",
                "style.css", "body { writing-mode: vertical-rl; }");
        assertTrue(FileToPdf.containsVerticalWritingMode(zip, "bundle.zip"));
    }

    @Test
    void testContainsVerticalWritingMode_inZipNoVertical() throws Exception {
        byte[] zip = makeZip(
                "index.html", "<p>hello</p>",
                "style.css", "body { color: red; }");
        assertFalse(FileToPdf.containsVerticalWritingMode(zip, "bundle.zip"));
    }

    @Test
    void testContainsVerticalWritingMode_unsupportedExtension() {
        byte[] anything = "writing-mode: vertical-rl;".getBytes(StandardCharsets.UTF_8);
        // .pdf is not a supported input here; routing should not be triggered.
        assertFalse(FileToPdf.containsVerticalWritingMode(anything, "page.pdf"));
    }

    private static byte[] makeZip(String... pairs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < pairs.length; i += 2) {
                ZipEntry entry = new ZipEntry(pairs[i]);
                zos.putNextEntry(entry);
                zos.write(pairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
