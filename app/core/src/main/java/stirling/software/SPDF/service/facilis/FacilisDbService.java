package stirling.software.SPDF.service.facilis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.pixee.security.ZipSecurity;

import jakarta.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the imported FACILIS DB tree (extracted from a customer-uploaded {@code DB.zip}) and
 * keeps an in-memory index of {@code .lay} signature layouts. The extracted directory persists
 * across restarts so customers don't need to re-upload after every deployment.
 *
 * <p>Layout id format = relative path of the .lay file under the configured root directory, with
 * forward slashes (e.g. {@code "Layout/SignatureLayout/A版/その他/A2 2面（A4）16p7号機.lay"}).
 *
 * <p>Customer-supplied .lay names contain Japanese characters, full-width spaces, etc. — we
 * preserve them verbatim in the id and rely on URL encoding at the API boundary.
 */
@Slf4j
@Service
public class FacilisDbService {

    private static final String LAYOUT_EXT = ".lay";
    private static final String SIGNATURE_LAYOUT_PREFIX = "Layout/SignatureLayout/";

    @Value("${stirling.pdf.facilisDbDir:/customFiles/facilis-db}")
    private String configuredRootDir;

    private Path rootDir;
    private final Map<String, FacilisLayout> layoutsById = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        rootDir = Paths.get(configuredRootDir);
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            log.warn("Failed to create FACILIS DB directory {}: {}", rootDir, e.getMessage());
            return;
        }
        rebuildIndex();
    }

    /** Result summary of a {@link #importDbZip(byte[])} call. */
    public record ImportResult(
            int indexedLayouts,
            int totalFilesExtracted,
            int parseFailures,
            Map<String, Integer> extensionCounts) {}

    /**
     * Replace the imported DB with the contents of {@code zipBytes}. Returns rich diagnostics so
     * the caller can explain to the user when 0 .lay layouts were found (e.g. wrong DB subset
     * uploaded — only .tm1/.tm2/.dai files inside).
     */
    public synchronized ImportResult importDbZip(byte[] zipBytes) throws IOException {
        // Wipe existing tree before extracting so stale templates don't linger after re-upload.
        if (Files.isDirectory(rootDir)) {
            deleteRecursively(rootDir);
        }
        Files.createDirectories(rootDir);

        int totalFiles = 0;
        Map<String, Integer> extCounts = new java.util.TreeMap<>();
        try (ZipInputStream zis =
                ZipSecurity.createHardenedInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            Path normalizedRoot = rootDir.toAbsolutePath().normalize();
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                if (entryName.endsWith("/")) {
                    continue;
                }
                // Strip a single leading "DB/" prefix if present so callers can ship the zip with
                // or without the wrapper directory.
                String rel = entryName;
                if (rel.startsWith("DB/")) {
                    rel = rel.substring(3);
                }
                if (rel.contains("..")) {
                    log.warn("Skipping path-traversal entry: {}", entryName);
                    continue;
                }
                // Ignore .DS_Store / Mac resource forks.
                String fileName = Paths.get(rel).getFileName().toString();
                if (".DS_Store".equals(fileName) || fileName.startsWith("._")) {
                    continue;
                }

                Path target = rootDir.resolve(rel).normalize();
                if (!target.startsWith(normalizedRoot)) {
                    log.warn("Zip entry escapes root, skipping: {}", entryName);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream entryStream = nonClosingStream(zis)) {
                    Files.copy(entryStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
                totalFiles++;
                int dot = fileName.lastIndexOf('.');
                String ext =
                        (dot > 0 && dot < fileName.length() - 1)
                                ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT)
                                : "(none)";
                extCounts.merge(ext, 1, Integer::sum);
            }
        }

        int candidateLayCount;
        try (Stream<Path> stream = Files.walk(rootDir)) {
            candidateLayCount = (int)
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(LAYOUT_EXT))
                            .count();
        } catch (IOException e) {
            candidateLayCount = 0;
        }
        rebuildIndex();
        int indexed = layoutsById.size();
        int parseFailures = Math.max(0, candidateLayCount - indexed);
        return new ImportResult(indexed, totalFiles, parseFailures, extCounts);
    }

    /** Returns true when at least one .lay has been indexed. */
    public boolean isLoaded() {
        return !layoutsById.isEmpty();
    }

    /** Sorted by category, then name. */
    public List<FacilisLayout> listLayouts() {
        List<FacilisLayout> all = new ArrayList<>(layoutsById.values());
        all.sort(
                Comparator.comparing(FacilisLayout::category, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(FacilisLayout::name, String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    /** Filter by category prefix and/or page count substring. */
    public List<FacilisLayout> searchLayouts(String categoryPrefix, String nameSubstring) {
        String catFilter = categoryPrefix == null ? "" : categoryPrefix.toLowerCase(Locale.ROOT);
        String nameFilter = nameSubstring == null ? "" : nameSubstring.toLowerCase(Locale.ROOT);
        return listLayouts().stream()
                .filter(
                        l ->
                                catFilter.isEmpty()
                                        || l.category().toLowerCase(Locale.ROOT).startsWith(catFilter))
                .filter(
                        l ->
                                nameFilter.isEmpty()
                                        || l.name().toLowerCase(Locale.ROOT).contains(nameFilter))
                .toList();
    }

    public FacilisLayout getLayout(String id) {
        return layoutsById.get(id);
    }

    /** Resolves a layout id to its on-disk .lay path; returns null when the id isn't indexed. */
    public Path getLayoutFile(String id) {
        if (id == null || !layoutsById.containsKey(id)) {
            return null;
        }
        Path file = rootDir.resolve(id).normalize();
        if (!file.startsWith(rootDir.toAbsolutePath().normalize())) {
            return null;
        }
        return file;
    }

    /** Re-scan the root directory; called at startup and after import. */
    public synchronized void rebuildIndex() {
        layoutsById.clear();
        if (!Files.isDirectory(rootDir)) {
            return;
        }
        Path layoutDir = rootDir.resolve(SIGNATURE_LAYOUT_PREFIX.replace('/', java.io.File.separatorChar));
        if (!Files.isDirectory(layoutDir)) {
            // Customer may have uploaded a partial DB without SignatureLayout — still walk the
            // root and pick up any .lay we find.
            walkAndIndex(rootDir);
            return;
        }
        walkAndIndex(rootDir);
    }

    private void walkAndIndex(Path start) {
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(LAYOUT_EXT))
                    .forEach(this::indexFileSafely);
        } catch (IOException e) {
            log.warn("Failed walking FACILIS DB root {}: {}", start, e.getMessage());
        }
        log.info("FACILIS DB indexed: {} layouts under {}", layoutsById.size(), start);
    }

    private void indexFileSafely(Path file) {
        String relPath = rootDir.relativize(file).toString().replace(java.io.File.separatorChar, '/');
        String category = deriveCategory(relPath);
        try {
            FacilisLayout layout = FacilisLayoutParser.parse(file, relPath, category);
            layoutsById.put(relPath, layout);
        } catch (Exception e) {
            log.debug("Skipping unparseable FACILIS layout {}: {}", relPath, e.getMessage());
        }
    }

    /**
     * Derives a human category like "A版/その他" from a path like
     * "Layout/SignatureLayout/A版/その他/A2 2面.lay".
     */
    private static String deriveCategory(String relativePath) {
        String trimmed =
                relativePath.startsWith(SIGNATURE_LAYOUT_PREFIX)
                        ? relativePath.substring(SIGNATURE_LAYOUT_PREFIX.length())
                        : relativePath;
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        return trimmed.substring(0, lastSlash);
    }

    /** Wraps the given {@link InputStream} so {@link Files#copy} doesn't close the parent zip. */
    private static InputStream nonClosingStream(InputStream src) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return src.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return src.read(b, off, len);
            }

            @Override
            public void close() {
                // Intentionally do not close the underlying zip stream.
            }
        };
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}
