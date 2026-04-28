package stirling.software.common.util;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.github.pixee.security.ZipSecurity;

import stirling.software.common.model.api.converters.HTMLToPdfRequest;
import stirling.software.common.util.ProcessExecutor.ProcessExecutorResult;

public class FileToPdf {

    // Matches CSS `writing-mode` declarations resolving to a vertical flow.
    // Covers vertical-rl / vertical-lr / sideways-rl / sideways-lr / tb / tb-rl.
    private static final Pattern VERTICAL_WRITING_MODE_PATTERN =
            Pattern.compile(
                    "writing-mode\\s*:\\s*(vertical-(?:rl|lr)|sideways-(?:rl|lr)|tb(?:-rl)?)",
                    Pattern.CASE_INSENSITIVE);

    public static byte[] convertHtmlToPdf(
            String weasyprintPath,
            HTMLToPdfRequest request,
            byte[] fileBytes,
            String fileName,
            TempFileManager tempFileManager,
            CustomHtmlSanitizer customHtmlSanitizer)
            throws IOException, InterruptedException {

        try (TempFile tempOutputFile = new TempFile(tempFileManager, ".pdf")) {
            try (TempFile tempInputFile =
                    new TempFile(
                            tempFileManager,
                            fileName.toLowerCase(Locale.ROOT).endsWith(".html")
                                    ? ".html"
                                    : ".zip")) {

                if (fileName.toLowerCase(Locale.ROOT).endsWith(".html")) {
                    String sanitizedHtml =
                            sanitizeHtmlContent(
                                    new String(fileBytes, StandardCharsets.UTF_8),
                                    customHtmlSanitizer);
                    Files.writeString(tempInputFile.getPath(), sanitizedHtml);
                } else if (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    Files.write(tempInputFile.getPath(), fileBytes);
                    sanitizeHtmlFilesInZip(
                            tempInputFile.getPath(), tempFileManager, customHtmlSanitizer);
                } else {
                    throw ExceptionUtils.createHtmlFileRequiredException();
                }

                List<String> command = new ArrayList<>();
                command.add(weasyprintPath);
                command.add("-e");
                command.add("utf-8");
                command.add("-v");
                command.add("--pdf-forms");
                command.add(tempInputFile.getAbsolutePath());
                command.add(tempOutputFile.getAbsolutePath());

                ProcessExecutorResult returnCode =
                        ProcessExecutor.getInstance(ProcessExecutor.Processes.WEASYPRINT)
                                .runCommandWithOutputHandling(command);

                byte[] pdfBytes = Files.readAllBytes(tempOutputFile.getPath());
                try {
                    return pdfBytes;
                } catch (Exception e) {
                    pdfBytes = Files.readAllBytes(tempOutputFile.getPath());
                    if (pdfBytes.length < 1) {
                        throw e;
                    }
                    return pdfBytes;
                }
            } // tempInputFile auto-closed
        } // tempOutputFile auto-closed
    }

    /**
     * Render HTML (or a zip of HTML+CSS) to PDF via the Vivliostyle CLI. Used for vertical Japanese
     * typesetting where WeasyPrint is weak.
     */
    public static byte[] convertHtmlToPdfViaVivliostyle(
            String vivliostylePath,
            HTMLToPdfRequest request,
            byte[] fileBytes,
            String fileName,
            TempFileManager tempFileManager,
            CustomHtmlSanitizer customHtmlSanitizer)
            throws IOException, InterruptedException {

        boolean isZip = fileName.toLowerCase(Locale.ROOT).endsWith(".zip");

        try (TempFile tempOutputFile = new TempFile(tempFileManager, ".pdf")) {
            try (TempDirectory workDir = new TempDirectory(tempFileManager)) {
                Path entryHtml;

                if (fileName.toLowerCase(Locale.ROOT).endsWith(".html")) {
                    String sanitizedHtml =
                            sanitizeHtmlContent(
                                    new String(fileBytes, StandardCharsets.UTF_8),
                                    customHtmlSanitizer);
                    entryHtml = workDir.getPath().resolve("index.html");
                    Files.writeString(entryHtml, sanitizedHtml);
                } else if (isZip) {
                    // Reuse the existing zip-extract + sanitize pipeline by writing the zip to a
                    // temp file and extracting into workDir. sanitizeHtmlFilesInZip writes
                    // sanitized contents back to a directory, so we extract here directly.
                    extractAndSanitizeZipInto(
                            fileBytes, workDir.getPath(), tempFileManager, customHtmlSanitizer);
                    entryHtml = locateEntryHtml(workDir.getPath());
                    if (entryHtml == null) {
                        throw ExceptionUtils.createHtmlFileRequiredException();
                    }
                } else {
                    throw ExceptionUtils.createHtmlFileRequiredException();
                }

                List<String> command = new ArrayList<>();
                command.add(vivliostylePath);
                command.add("build");
                command.add(entryHtml.toAbsolutePath().toString());
                command.add("--output");
                command.add(tempOutputFile.getAbsolutePath());
                command.add("--no-sandbox");

                ProcessExecutorResult result =
                        ProcessExecutor.getInstance(ProcessExecutor.Processes.VIVLIOSTYLE)
                                .runCommandWithOutputHandling(command);
                if (result.getRc() != 0) {
                    throw new IOException(
                            "Vivliostyle build failed with exit code " + result.getRc());
                }

                return Files.readAllBytes(tempOutputFile.getPath());
            }
        }
    }

    /**
     * Detects whether the supplied HTML or zipped HTML+CSS bundle declares a vertical CSS
     * `writing-mode`. Used as a routing signal for picking the Vivliostyle renderer over
     * WeasyPrint.
     */
    public static boolean containsVerticalWritingMode(byte[] fileBytes, String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".css")) {
            return VERTICAL_WRITING_MODE_PATTERN
                    .matcher(new String(fileBytes, StandardCharsets.UTF_8))
                    .find();
        }
        if (!lower.endsWith(".zip")) {
            return false;
        }
        try (ZipInputStream zipIn =
                ZipSecurity.createHardenedInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!(name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".css"))) {
                    continue;
                }
                String content = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                if (VERTICAL_WRITING_MODE_PATTERN.matcher(content).find()) {
                    return true;
                }
            }
        } catch (IOException e) {
            // If the zip is malformed we'll let the renderer surface the error; for routing
            // purposes, treat as non-vertical so we don't accidentally pick the heavier path.
            return false;
        }
        return false;
    }

    private static void extractAndSanitizeZipInto(
            byte[] zipBytes,
            Path targetDir,
            TempFileManager tempFileManager,
            CustomHtmlSanitizer customHtmlSanitizer)
            throws IOException {
        try (ZipInputStream zipIn =
                ZipSecurity.createHardenedInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();
            while ((entry = zipIn.getNextEntry()) != null) {
                Path filePath = targetDir.resolve(sanitizeZipFilename(entry.getName()));
                if (!filePath.toAbsolutePath().normalize().startsWith(normalizedTargetDir)) {
                    throw new IOException(
                            "Zip entry path escapes target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                    continue;
                }
                Files.createDirectories(filePath.getParent());
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".html") || name.endsWith(".htm")) {
                    String content = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                    Files.writeString(filePath, sanitizeHtmlContent(content, customHtmlSanitizer));
                } else {
                    Files.copy(zipIn, filePath);
                }
            }
        }
    }

    private static Path locateEntryHtml(Path dir) throws IOException {
        Path candidate = dir.resolve("index.html");
        if (Files.exists(candidate)) {
            return candidate;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".html"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String sanitizeHtmlContent(
            String htmlContent, CustomHtmlSanitizer customHtmlSanitizer) {
        return customHtmlSanitizer.sanitize(htmlContent);
    }

    private static void sanitizeHtmlFilesInZip(
            Path zipFilePath,
            TempFileManager tempFileManager,
            CustomHtmlSanitizer customHtmlSanitizer)
            throws IOException {
        try (TempDirectory tempUnzippedDir = new TempDirectory(tempFileManager)) {
            try (ZipInputStream zipIn =
                    ZipSecurity.createHardenedInputStream(
                            new ByteArrayInputStream(Files.readAllBytes(zipFilePath)))) {
                ZipEntry entry = zipIn.getNextEntry();
                while (entry != null) {
                    Path filePath =
                            tempUnzippedDir.getPath().resolve(sanitizeZipFilename(entry.getName()));
                    Path normalizedTargetDir =
                            tempUnzippedDir.getPath().toAbsolutePath().normalize();
                    Path normalizedFilePath = filePath.toAbsolutePath().normalize();
                    if (!normalizedFilePath.startsWith(normalizedTargetDir)) {
                        throw new IOException(
                                "Zip entry path escapes target directory: " + entry.getName());
                    }
                    if (!entry.isDirectory()) {
                        Files.createDirectories(filePath.getParent());
                        if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".html")
                                || entry.getName().toLowerCase(Locale.ROOT).endsWith(".htm")) {
                            String content =
                                    new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                            String sanitizedContent =
                                    sanitizeHtmlContent(content, customHtmlSanitizer);
                            Files.writeString(filePath, sanitizedContent);
                        } else {
                            Files.copy(zipIn, filePath);
                        }
                    }
                    zipIn.closeEntry();
                    entry = zipIn.getNextEntry();
                }
            }

            // Repack the sanitized files
            zipDirectory(tempUnzippedDir.getPath(), zipFilePath);
        } // tempUnzippedDir auto-cleaned
    }

    private static void zipDirectory(Path sourceDir, Path zipFilePath) throws IOException {
        try (ZipOutputStream zos =
                new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
            try (Stream<Path> walk = Files.walk(sourceDir)) {
                walk.filter(path -> !Files.isDirectory(path))
                        .forEach(
                                path -> {
                                    ZipEntry zipEntry =
                                            new ZipEntry(sourceDir.relativize(path).toString());
                                    try {
                                        zos.putNextEntry(zipEntry);
                                        Files.copy(path, zos);
                                        zos.closeEntry();
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                });
            }
        }
    }

    static String sanitizeZipFilename(String entryName) {
        if (entryName == null || entryName.trim().isEmpty()) {
            return "";
        }
        // Remove any drive letters (e.g., "C:\") and leading forward/backslashes
        entryName =
                RegexPatternUtils.getInstance()
                        .getDriveLetterPattern()
                        .matcher(entryName)
                        .replaceAll("");
        entryName =
                RegexPatternUtils.getInstance()
                        .getLeadingSlashesPattern()
                        .matcher(entryName)
                        .replaceAll("");

        // Recursively remove path traversal sequences
        while (entryName.contains("../") || entryName.contains("..\\")) {
            entryName = entryName.replace("../", "").replace("..\\", "");
        }
        // Normalize all backslashes to forward slashes
        entryName =
                RegexPatternUtils.getInstance()
                        .getBackslashPattern()
                        .matcher(entryName)
                        .replaceAll("/");
        return entryName;
    }
}
