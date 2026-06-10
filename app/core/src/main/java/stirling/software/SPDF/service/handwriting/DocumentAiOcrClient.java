package stirling.software.SPDF.service.handwriting;

import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.stereotype.Component;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.ProcessResponse;
import com.google.cloud.documentai.v1.ProcessorName;
import com.google.cloud.documentai.v1.RawDocument;
import com.google.protobuf.ByteString;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.System.GoogleCloud;

/**
 * Fallback OCR client using Google Document AI. Used when Gemini fails or returns empty.
 *
 * <p>Document AI's online endpoint accepts up to 15 pages / 20 MB per request. The orchestrator is
 * responsible for chunking PDFs that exceed this limit before calling here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAiOcrClient {

    private final ApplicationProperties applicationProperties;

    public boolean isAvailable() {
        GoogleCloud cfg = applicationProperties.getSystem().getGoogleCloud();
        return cfg.isEnabled()
                && cfg.getDocumentAi().isEnabled()
                && !cfg.getProjectId().isBlank()
                && !cfg.getDocumentAi().getProcessorId().isBlank();
    }

    /**
     * Process a PDF chunk (≤15 pages, ≤20 MB) and return the concatenated text. The Document AI OCR
     * processor handles handwriting natively.
     */
    public String recognizePdfChunk(byte[] pdfBytes) throws IOException {
        GoogleCloud cfg = applicationProperties.getSystem().getGoogleCloud();
        String processorLocation = cfg.getDocumentAi().getProcessorLocation();
        String endpoint = processorLocation + "-documentai.googleapis.com:443";

        DocumentProcessorServiceSettings.Builder settingsBuilder =
                DocumentProcessorServiceSettings.newBuilder().setEndpoint(endpoint);

        GoogleCredentials credentials = loadCredentials(cfg);
        if (credentials != null) {
            settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
        }

        try (DocumentProcessorServiceClient client =
                DocumentProcessorServiceClient.create(settingsBuilder.build())) {

            ProcessorName name =
                    ProcessorName.of(
                            cfg.getProjectId(),
                            processorLocation,
                            cfg.getDocumentAi().getProcessorId());

            RawDocument rawDocument =
                    RawDocument.newBuilder()
                            .setContent(ByteString.copyFrom(pdfBytes))
                            .setMimeType("application/pdf")
                            .build();

            ProcessRequest request =
                    ProcessRequest.newBuilder()
                            .setName(name.toString())
                            .setRawDocument(rawDocument)
                            .build();

            ProcessResponse response = client.processDocument(request);
            String text = response.getDocument().getText();
            if (text == null || text.isBlank()) {
                throw new IOException("Document AI returned no text");
            }
            return text;
        }
    }

    private GoogleCredentials loadCredentials(GoogleCloud cfg) throws IOException {
        String path = cfg.getCredentialsPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(path)) {
            return GoogleCredentials.fromStream(fis);
        }
    }
}
