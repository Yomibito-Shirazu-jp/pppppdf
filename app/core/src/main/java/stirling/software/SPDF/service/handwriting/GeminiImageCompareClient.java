package stirling.software.SPDF.service.handwriting;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.System.GoogleCloud;

/**
 * Calls Vertex AI Gemini to compare two images (PNG / JPG / single-page PDF rasters) and return a
 * Japanese-language report listing every difference (text changes, logo / image swaps, layout
 * shifts, color changes). Reuses the same auth + project config as {@link GeminiOcrClient}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiImageCompareClient {

    private final ApplicationProperties applicationProperties;

    private static final String COMPARE_PROMPT =
            "あなたは精密な視覚差分アナライザーです。これから2枚の画像を見せます：画像A（オリジナル）"
                    + "と画像B（変更後）。両者の **すべての差分** を抽出してください。\n\n"
                    + "各差分について以下を出力してください:\n"
                    + "- 種別: [文字変更 / 画像差替 / ロゴ差替 / レイアウト変更 / 色変更 / 追加 / 削除]\n"
                    + "- 位置: 大まかな場所（例: 左上、中央上部、右下、ページ中央）\n"
                    + "- 内容: 具体的に何が変わったか（日本語で簡潔に）\n\n"
                    + "出力フォーマット:\n"
                    + "1. [種別] @ [位置] - [内容]\n"
                    + "2. [種別] @ [位置] - [内容]\n"
                    + "...\n\n"
                    + "差分が無い場合: \"差分なし\"\n"
                    + "解析できない場合: \"解析不能: [理由]\"\n"
                    + "必ず日本語で回答してください。";

    public boolean isAvailable() {
        GoogleCloud cfg = applicationProperties.getSystem().getGoogleCloud();
        return cfg.isEnabled() && cfg.getGemini().isEnabled() && !cfg.getProjectId().isBlank();
    }

    /**
     * Compare two images and return the AI-generated diff report. {@code mimeType} should be one of
     * {@code image/png}, {@code image/jpeg}, {@code image/webp}.
     */
    public String compare(byte[] imageA, byte[] imageB, String mimeType) throws IOException {
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

            List<Part> parts = new ArrayList<>();
            parts.add(Part.newBuilder().setText("画像A (オリジナル):").build());
            parts.add(PartMaker.fromMimeTypeAndData(mimeType, imageA));
            parts.add(Part.newBuilder().setText("画像B (変更後):").build());
            parts.add(PartMaker.fromMimeTypeAndData(mimeType, imageB));
            parts.add(Part.newBuilder().setText(COMPARE_PROMPT).build());

            Content content = Content.newBuilder().setRole("user").addAllParts(parts).build();
            GenerateContentResponse response = model.generateContent(content);
            String text = ResponseHandler.getText(response);
            if (text == null || text.isBlank()) {
                throw new IOException("Gemini returned no comparison text");
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
