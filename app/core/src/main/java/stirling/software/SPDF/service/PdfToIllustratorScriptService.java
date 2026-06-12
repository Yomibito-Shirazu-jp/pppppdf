package stirling.software.SPDF.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.service.handwriting.GeminiOcrClient;
import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.System.GoogleCloud;

/**
 * PDF構造JSON → Adobe InDesign ExtendScript (.jsx) 変換サービス。
 * Gemini (Vertex AI) に PDFの構造記述JSONを渡し、InDesignで実行可能な
 * ExtendScriptコードを生成する。レスポンスはJSON {"response": "...jsx..."} 形式。
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
        + "あなたは、PDFの構造記述からAdobe InDesignで実行可能なAdobe ExtendScript (.jsx) を生成する専門家です。"
        + "特に日本語の縦書きを含む、レイアウトの再現性を重視します。\n\n"
        + "# タスク定義\n"
        + "PDFのレイアウトと内容を可能な限り忠実に再現するスクリプトを生成することを目的とします。"
        + "ただし、完全な再現は保証されるものではなく、特に複雑な効果やフォントの互換性には限界があることを前提とします。\n\n"
        + "## 入力仕様\n"
        + "- 入力として、PDFの構造を記述したJSONオブジェクトが提供されます。\n"
        + "- このJSONには、ページサイズやオブジェクト情報が含まれます。\n"
        + "- 全てのオブジェクト（テキスト、図形等）のジオメトリは bounds プロパティ [top, left, bottom, right] で定義されます。\n\n"
        + "## 出力仕様\n"
        + "- 生成するのはAdobe ExtendScriptのコード（テキスト）のみです。\n"
        + "- コードはjsxファイルとして保存できる形式とします。\n"
        + "- 必ずJSONオブジェクト {\"response\": \"...JSXコード...\"} の形式で返してください。\n\n"
        + "# 指示\n"
        + "1. 提供されたPDFの構造記述（JSON形式）を解析し、Adobe InDesign用ExtendScriptコードを生成します。\n"
        + "2. ドキュメントを新規作成するか、アクティブなドキュメントを使用する基本構造とします。単位系はポイント(points)に設定してください。\n"
        + "3. JSON内のpages配列をループし、ページごとにInDesignのページを作成・調整します。\n"
        + "4. 各ページ内のオブジェクトをループし、オブジェクトのtypeに応じて適切なコードを生成します。\n"
        + "5. 座標系はInDesignの標準（左上が原点、Y軸は下向き、geometricBoundsは[top, left, bottom, right]の順）に変換してコードを生成してください。\n"
        + "6. フォントが見つからない場合に備え、try-catchブロックを使用してエラーを回避するコードを記述してください。\n\n"
        + "## 再現する主な要素\n"
        + "- ページ構造: PDFのページごとにInDesignのページを生成・サイズ設定します。\n"
        + "- テキスト: テキストフレーム（横書き・縦書き）、フォント、サイズ、色、配置を再現します。\n"
        + "- 図形: パス、塗り、線の色と幅を再現します。\n"
        + "- 画像: 配置画像として再現します。\n\n"
        + "## 縦書き実装\n"
        + "縦書きは以下のコードを使用してください:\n"
        + "textFrame.storyPreferences.storyOrientation = StoryHorizontalOrVertical.VERTICAL;\n\n"
        + "## 色の実装\n"
        + "CMYK色は以下のパターンを使用してください:\n"
        + "var colorName = 'CMYK_C_M_Y_K';\n"
        + "var color = doc.colors.itemByName(colorName);\n"
        + "if (!color.isValid) { color = doc.colors.add({name:colorName, model:ColorModel.PROCESS, space:ColorSpace.CMYK, colorValue:[C,M,Y,K]}); }\n\n"
        + "## 出力形式\n"
        + "必ず以下のJSON形式で返してください（コードブロック記号は不要）:\n"
        + "{\"response\": \"#target indesign\\n\\n(function(){\\n  ...\\n})();\"}";

    public boolean isAvailable() {
        return geminiOcrClient.isAvailable();
    }

    /**
     * PDFのJSON構造文字列を受け取り、InDesign ExtendScriptを返す。
     *
     * @param pdfJsonString PdfJsonConversionServiceで生成したJSON
     * @return InDesignで実行可能な .jsx スクリプト文字列
     */
    public String generateScript(String pdfJsonString) throws IOException {
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
                            .setTemperature(1.0f)
                            .setTopP(0.95f)
                            .setMaxOutputTokens(65535)
                            .setResponseMimeType("application/json")
                            .build();

            GenerativeModel model =
                    new GenerativeModel(cfg.getGemini().getModel(), vertexAi)
                            .withGenerationConfig(generationConfig)
                            .withSystemInstruction(ContentMaker.fromString(SYSTEM_INSTRUCTION));

            log.info("Calling Gemini for InDesign script generation, JSON length={}", pdfJsonString.length());

            GenerateContentResponse response =
                    model.generateContent(ContentMaker.fromString(pdfJsonString));

            String rawText = ResponseHandler.getText(response);
            if (rawText == null || rawText.isBlank()) {
                throw new IOException("Gemini returned empty response");
            }

            // JSONラップを解除: {"response": "...jsx..."} → jsx文字列
            String script = extractScriptFromJson(rawText.trim());

            log.info("Gemini generated InDesign script, length={}", script.length());
            return script;
        }
    }

    private String extractScriptFromJson(String raw) throws IOException {
        // まずJSON解析を試みる
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.has("response")) {
                return node.get("response").asText();
            }
        } catch (Exception e) {
            log.debug("Response is not JSON, treating as raw script: {}", e.getMessage());
        }

        // JSONでなければそのまま使用（Markdownコードブロックを除去）
        if (raw.startsWith("```")) {
            raw = raw.replaceFirst("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        return raw;
    }

    private GoogleCredentials loadCredentials(GoogleCloud cfg) {
        String credPath = java.lang.System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credPath == null || credPath.isBlank()) return null;
        try {
            return GoogleCredentials.fromStream(new java.io.FileInputStream(credPath))
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        } catch (Exception e) {
            log.warn("Could not load GCP credentials from {}: {}", credPath, e.getMessage());
            return null;
        }
    }
}
