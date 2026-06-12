package stirling.software.SPDF.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.service.handwriting.GeminiOcrClient;
import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.System.GoogleCloud;

/**
 * PDF構造JSON → Adobe Illustrator ExtendScript (.jsx) 変換サービス。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfToIllustratorScriptService {

    private final ApplicationProperties applicationProperties;
    private final GeminiOcrClient geminiOcrClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_INSTRUCTION =
        "#役割\n"
        + "あなたは、PDFの構造記述からAdobe Illustratorで実行可能なAdobe ExtendScript (.jsx) を生成する専門家です。"
        + "特に日本語の縦書きを含む、レイアウトの再現性を重視します。\n\n"
        + "## 入力仕様\n"
        + "- 入力として、PDFの構造を記述したJSONオブジェクトが提供されます。\n"
        + "- このJSONには、ページサイズや、テキスト、図形、画像などの各オブジェクトの位置、サイズ、内容、スタイル情報が含まれます。\n\n"
        + "## 出力仕様\n"
        + "- 生成するのはAdobe Illustrator用ExtendScriptのコード（テキスト）のみです。\n"
        + "- 必ずJSONオブジェクト {\"response\": \"...JSXコード...\"} の形式で返してください。\n\n"
        + "# 指示\n"
        + "1. 提供されたPDFの構造記述（JSON形式）を解析し、Adobe Illustrator用ExtendScriptコードを生成します。\n"
        + "2. #target illustrator で始めてください。\n"
        + "3. ドキュメントを新規作成するか、アクティブなドキュメントを使用する基本構造とします。\n"
        + "4. JSON内のpages配列をループし、ページごとにアートボードを作成・調整します。\n"
        + "5. 各ページ内のオブジェクトをループし、typeに応じて適切なコードを生成します。\n"
        + "6. 座標系はIllustratorの標準（左上が原点、Y軸は下向き）に変換してください。\n"
        + "7. フォントが見つからない場合に備え、try-catchブロックを使用してください。\n\n"
        + "## 再現する主な要素\n"
        + "- ページ構造: PDFのページごとにアートボードを生成します。\n"
        + "- テキスト: textFrame.orientation = TextOrientation.VERTICAL で縦書きを再現します。\n"
        + "- 図形: pathItems、CMYKColorを使用します。\n"
        + "- 画像: 配置画像として再現します。\n\n"
        + "## 出力形式\n"
        + "必ず以下のJSON形式で返してください:\n"
        + "{\"response\": \"#target illustrator\\n\\n(function(){\\n  ...\\n})();\"}";

    public boolean isAvailable() {
        return geminiOcrClient.isAvailable();
    }

    public String generateScript(String pdfJsonString) throws IOException {
        GoogleCloud cfg = applicationProperties.getSystem().getGoogleCloud();
        GoogleCredentials credentials = loadCredentials(cfg);

        VertexAI.Builder builder =
                new VertexAI.Builder()
                        .setProjectId(cfg.getProjectId())
                        .setLocation(cfg.getLocation());
        if (credentials != null) builder.setCredentials(credentials);

        try (VertexAI vertexAi = builder.build()) {
            Schema responseSchema = Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putProperties("response", Schema.newBuilder().setType(Type.STRING).build())
                    .build();

            GenerationConfig generationConfig =
                    GenerationConfig.newBuilder()
                            .setTemperature(1.0f)
                            .setTopP(0.95f)
                            .setMaxOutputTokens(65535)
                            .setResponseMimeType("application/json")
                            .setResponseSchema(responseSchema)
                            .build();

            GenerativeModel model =
                    new GenerativeModel(cfg.getGemini().getModel(), vertexAi)
                            .withGenerationConfig(generationConfig)
                            .withSystemInstruction(ContentMaker.fromString(SYSTEM_INSTRUCTION));

            log.info("Calling Gemini for Illustrator script generation, JSON length={}", pdfJsonString.length());

            GenerateContentResponse response =
                    model.generateContent(ContentMaker.fromString(pdfJsonString));

            String rawText = ResponseHandler.getText(response);
            if (rawText == null || rawText.isBlank()) {
                throw new IOException("Gemini returned empty response");
            }

            String script = extractFromJson(rawText.trim());
            log.info("Gemini generated Illustrator script, length={}", script.length());
            return script;
        }
    }

    private String extractFromJson(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.has("response")) return node.get("response").asText();
        } catch (Exception ignored) {}
        if (raw.startsWith("```")) {
            raw = raw.replaceFirst("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        return raw;
    }

    private GoogleCredentials loadCredentials(GoogleCloud ignored) {
        String credPath = java.lang.System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credPath == null || credPath.isBlank()) return null;
        try {
            return GoogleCredentials.fromStream(new java.io.FileInputStream(credPath))
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        } catch (Exception e) {
            log.warn("Could not load GCP credentials: {}", e.getMessage());
            return null;
        }
    }
}
