package stirling.software.SPDF.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.service.handwriting.GeminiOcrClient;
import stirling.software.common.model.ApplicationProperties;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfToIllustratorScriptService {

    private final ApplicationProperties applicationProperties;
    private final GeminiOcrClient geminiOcrClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        + "必ず以下のJSON形式で返してください:\n"
        + "{\"response\": \"#target illustrator\\n\\n(function(){\\n  ...\\n})();\"}";

    public boolean isAvailable() {
        String apiKey = System.getenv("GOOGLE_CLOUD_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) return true;
        return geminiOcrClient.isAvailable();
    }

    public String generateScript(String pdfJsonString) throws IOException {
        String apiKey = System.getenv("GOOGLE_CLOUD_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            return generateWithApiKey(pdfJsonString, apiKey, SYSTEM_INSTRUCTION, "Illustrator");
        }
        return generateWithVertexAI(pdfJsonString, SYSTEM_INSTRUCTION, "Illustrator");
    }

    String generateWithApiKey(String pdfJson, String apiKey, String systemInstruction, String target) throws IOException {
        String model = applicationProperties.getSystem().getGoogleCloud().getGemini().getModel();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        ObjectNode body = objectMapper.createObjectNode();

        ObjectNode sysInst = body.putObject("system_instruction");
        sysInst.putArray("parts").addObject().put("text", systemInstruction);

        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", pdfJson);

        ObjectNode genConfig = body.putObject("generationConfig");
        genConfig.put("temperature", 1.0);
        genConfig.put("topP", 0.95);
        genConfig.put("maxOutputTokens", 65535);
        genConfig.put("responseMimeType", "application/json");
        ObjectNode schema = genConfig.putObject("responseSchema");
        schema.put("type", "OBJECT");
        schema.putObject("properties").putObject("response").put("type", "STRING");

        String requestJson = objectMapper.writeValueAsString(body);

        log.info("Calling Gemini REST API for {} script, model={}, JSON length={}", target, model, pdfJson.length());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(300))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gemini API call interrupted", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Gemini API error " + response.statusCode() + ": " + response.body());
        }

        JsonNode respNode = objectMapper.readTree(response.body());
        String text = respNode.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text").asText();

        if (text == null || text.isBlank()) {
            throw new IOException("Gemini returned empty text");
        }

        String script = extractFromJson(text.trim());
        log.info("Gemini generated {} script via REST, length={}", target, script.length());
        return script;
    }

    String generateWithVertexAI(String pdfJsonString, String systemInstruction, String target) throws IOException {
        com.google.auth.oauth2.GoogleCredentials credentials = loadCredentials();
        var cfg = applicationProperties.getSystem().getGoogleCloud();

        com.google.cloud.vertexai.VertexAI.Builder builder =
                new com.google.cloud.vertexai.VertexAI.Builder()
                        .setProjectId(cfg.getProjectId())
                        .setLocation(cfg.getLocation());
        if (credentials != null) builder.setCredentials(credentials);

        try (com.google.cloud.vertexai.VertexAI vertexAi = builder.build()) {
            com.google.cloud.vertexai.api.Schema responseSchema =
                    com.google.cloud.vertexai.api.Schema.newBuilder()
                            .setType(com.google.cloud.vertexai.api.Type.OBJECT)
                            .putProperties("response",
                                    com.google.cloud.vertexai.api.Schema.newBuilder()
                                            .setType(com.google.cloud.vertexai.api.Type.STRING).build())
                            .build();

            com.google.cloud.vertexai.api.GenerationConfig generationConfig =
                    com.google.cloud.vertexai.api.GenerationConfig.newBuilder()
                            .setTemperature(1.0f).setTopP(0.95f).setMaxOutputTokens(65535)
                            .setResponseMimeType("application/json")
                            .setResponseSchema(responseSchema)
                            .build();

            com.google.cloud.vertexai.generativeai.GenerativeModel model =
                    new com.google.cloud.vertexai.generativeai.GenerativeModel(cfg.getGemini().getModel(), vertexAi)
                            .withGenerationConfig(generationConfig)
                            .withSystemInstruction(
                                    com.google.cloud.vertexai.generativeai.ContentMaker.fromString(systemInstruction));

            log.info("Calling Vertex AI for {} script, JSON length={}", target, pdfJsonString.length());

            com.google.cloud.vertexai.api.GenerateContentResponse response =
                    model.generateContent(
                            com.google.cloud.vertexai.generativeai.ContentMaker.fromString(pdfJsonString));

            String rawText = com.google.cloud.vertexai.generativeai.ResponseHandler.getText(response);
            if (rawText == null || rawText.isBlank()) throw new IOException("Gemini returned empty response");

            String script = extractFromJson(rawText.trim());
            log.info("Gemini generated {} script via VertexAI, length={}", target, script.length());
            return script;
        }
    }

    String extractFromJson(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.has("response")) return node.get("response").asText();
        } catch (Exception ignored) {}
        if (raw.startsWith("```")) {
            raw = raw.replaceFirst("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        return raw;
    }

    private com.google.auth.oauth2.GoogleCredentials loadCredentials() {
        String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credPath == null || credPath.isBlank()) return null;
        try {
            return com.google.auth.oauth2.GoogleCredentials
                    .fromStream(new java.io.FileInputStream(credPath))
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        } catch (Exception e) {
            log.warn("Could not load GCP credentials: {}", e.getMessage());
            return null;
        }
    }
}
