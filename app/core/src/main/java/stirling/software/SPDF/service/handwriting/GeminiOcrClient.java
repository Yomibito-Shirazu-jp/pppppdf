package stirling.software.SPDF.service.handwriting;

import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.System.GoogleCloud;

/**
 * Calls Vertex AI Gemini for handwriting OCR. One image (one PDF page rendered to PNG) per call.
 * Returns the plain extracted text or throws if Gemini fails — the caller decides whether to fall
 * back to Document AI.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiOcrClient {

    private final ApplicationProperties applicationProperties;

    private static final String OCR_PROMPT =
            "You are a high-accuracy OCR engine for Japanese handwritten manuscripts. "
                    + "Transcribe ALL text from this image exactly as written, preserving line "
                    + "breaks and the original reading order. Do NOT translate, summarize, "
                    + "explain, or add commentary. If a character is unreadable use ●. "
                    + "Output only the raw transcribed text.";

    public boolean isAvailable() {
        GoogleCloud cfg = applicationProperties.getSystem().getGoogleCloud();
        return cfg.isEnabled() && cfg.getGemini().isEnabled() && !cfg.getProjectId().isBlank();
    }

    /** Recognize text in a single page image (PNG bytes). */
    public String recognize(byte[] pngBytes) throws IOException {
        GoogleCloud cfg = applicationProperties.getSystem().getGoogleCloud();
        GoogleCredentials credentials = loadCredentials(cfg);

        VertexAI.Builder builder =
                new VertexAI.Builder()
                        .setProjectId(cfg.getProjectId())
                        .setLocation(cfg.getLocation());
        if (credentials != null) {
            builder.setCredentials(credentials);
        }

        try (VertexAI vertexAi = builder.build()) {

            GenerationConfig generationConfig =
                    GenerationConfig.newBuilder()
                            .setTemperature((float) cfg.getGemini().getTemperature())
                            .setMaxOutputTokens(cfg.getGemini().getMaxOutputTokens())
                            .build();

            GenerativeModel model =
                    new GenerativeModel(cfg.getGemini().getModel(), vertexAi)
                            .withGenerationConfig(generationConfig);

            GenerateContentResponse response =
                    model.generateContent(
                            ContentMaker.fromMultiModalData(
                                    PartMaker.fromMimeTypeAndData("image/png", pngBytes),
                                    OCR_PROMPT));

            String text = ResponseHandler.getText(response);
            if (text == null || text.isBlank()) {
                throw new IOException("Gemini returned no text content");
            }
            return text.trim();
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
