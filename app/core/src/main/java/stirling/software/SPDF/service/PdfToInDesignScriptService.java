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
 * PDF構造JSON → Adobe InDesign ExtendScript (.jsx) 変換サービス。
 * Gemini Vertex AI (thinking HIGH) を使用して高精度なスクリプトを生成する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfToInDesignScriptService {

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
        + "- 全てのオブジェクト（テキスト、図形等）のジオメトリは `bounds` プロパティ `[top, left, bottom, right]` で定義されます。\n\n"
        + "## 出力仕様\n"
        + "- 生成するのはAdobe ExtendScriptのコード（テキスト）のみです。\n"
        + "- コードはjsxファイルとして保存できる形式とします。\n"
        + "- プレビュー画像や実際のファイル生成は行いません。\n\n"
        + "# 指示\n"
        + "1. あなたは提供されたPDFの構造記述（JSON形式）を解析し、それを再現するためのAdobe InDesign用ExtendScriptコードを生成します。\n"
        + "2. ドキュメントを新規作成するか、アクティブなドキュメントを使用する基本構造とします。単位系はポイント(points)に設定してください。\n"
        + "3. JSON内のpages配列をループし、ページごとにInDesignのページを作成・調整します。\n"
        + "4. 各ページ内のobjects配列をループし、オブジェクトのtypeに応じて適切なコードを生成します。\n"
        + "5. 座標系はInDesignの標準（左上が原点、Y軸は下向き、geometricBoundsは[top, left, bottom, right]の順）に変換してコードを生成してください。\n"
        + "6. フォントが見つからない場合に備え、try-catchブロックを使用してエラーを回避するコードを記述してください。\n\n"
        + "## 再現する主な要素\n"
        + "- ページ構造: PDFのページごとにInDesignのページを生成・サイズ設定します。\n"
        + "- テキスト: テキストフレーム（横書き・縦書き）、フォント、サイズ、色、配置を再現します。\n"
        + "- 図形: パス、塗り、線の色と幅を再現します。\n"
        + "- 画像: 配置画像として再現します。\n\n"
        + "## 実行例\n\n"
        + "### 入力 (PDF構造記述)\n"
        + "```json\n"
        + "{\n"
        + "  \"pages\": [\n"
        + "    {\n"
        + "      \"width\": 595,\n"
        + "      \"height\": 842,\n"
        + "      \"objects\": [\n"
        + "        {\n"
        + "          \"type\": \"text\",\n"
        + "          \"content\": \"縦書きテスト\",\n"
        + "          \"font\": \"KozMinPr6N-Regular\",\n"
        + "          \"size\": 36,\n"
        + "          \"bounds\": [100, 500, 400, 550],\n"
        + "          \"orientation\": \"vertical\"\n"
        + "        },\n"
        + "        {\n"
        + "          \"type\": \"path\",\n"
        + "          \"shape\": \"rectangle\",\n"
        + "          \"bounds\": [600, 100, 700, 300],\n"
        + "          \"fillColor\": [0, 100, 100, 0],\n"
        + "          \"strokeWeight\": 0\n"
        + "        }\n"
        + "      ]\n"
        + "    }\n"
        + "  ]\n"
        + "}\n"
        + "```\n\n"
        + "### 出力 (Adobe ExtendScriptコード)\n"
        + "```jsx\n"
        + "#target indesign\n\n"
        + "(function(){\n"
        + "  var doc;\n"
        + "  if (app.documents.length == 0) {\n"
        + "    doc = app.documents.add();\n"
        + "  } else {\n"
        + "    doc = app.activeDocument;\n"
        + "  }\n\n"
        + "  doc.viewPreferences.horizontalMeasurementUnits = MeasurementUnits.POINTS;\n"
        + "  doc.viewPreferences.verticalMeasurementUnits = MeasurementUnits.POINTS;\n\n"
        + "  // Page 1\n"
        + "  var page = doc.pages.item(0);\n"
        + "  doc.documentPreferences.pageWidth = 595 + \"pt\";\n"
        + "  doc.documentPreferences.pageHeight = 842 + \"pt\";\n\n"
        + "  // Vertical Text\n"
        + "  var textFrame = page.textFrames.add();\n"
        + "  textFrame.geometricBounds = [100, 500, 400, 550];\n"
        + "  textFrame.contents = \"縦書きテスト\";\n"
        + "  textFrame.storyPreferences.storyOrientation = StoryHorizontalOrVertical.VERTICAL;\n\n"
        + "  var textRange = textFrame.parentStory;\n"
        + "  try {\n"
        + "    textRange.appliedFont = app.fonts.item(\"KozMinPr6N-Regular\");\n"
        + "  } catch(e) {}\n"
        + "  textRange.pointSize = 36;\n\n"
        + "  // Rectangle Path\n"
        + "  var rect = page.rectangles.add();\n"
        + "  rect.geometricBounds = [600, 100, 700, 300];\n\n"
        + "  var colorName = \"CMYK_0_100_100_0\";\n"
        + "  var color = doc.colors.itemByName(colorName);\n"
        + "  if (!color.isValid) {\n"
        + "    color = doc.colors.add({\n"
        + "      name: colorName,\n"
        + "      model: ColorModel.PROCESS,\n"
        + "      space: ColorSpace.CMYK,\n"
        + "      colorValue: [0, 100, 100, 0]\n"
        + "    });\n"
        + "  }\n"
        + "  rect.fillColor = color;\n"
        + "  rect.strokeWeight = 0;\n\n"
        + "})();\n"
        + "```\n\n"
        + "必ず以下のJSON形式で返してください:\n"
        + "{\"response\": \"#target indesign\\n\\n(function(){\\n  ...\\n})();\"}";

    public boolean isAvailable() {
        return geminiOcrClient.isAvailable();
    }

    public String generateScript(String pdfJsonString) throws IOException {
        GoogleCloud cfg = applicationProperties.getSystem().getGoogleCloud();
        GoogleCredentials credentials = loadCredentials();

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

            log.info("Calling Gemini for InDesign script generation, JSON length={}", pdfJsonString.length());

            GenerateContentResponse response =
                    model.generateContent(ContentMaker.fromString(pdfJsonString));

            String rawText = ResponseHandler.getText(response);
            if (rawText == null || rawText.isBlank()) {
                throw new IOException("Gemini returned empty response");
            }

            String script = extractFromJson(rawText.trim());
            log.info("Gemini generated InDesign script, length={}", script.length());
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

    private GoogleCredentials loadCredentials() {
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
