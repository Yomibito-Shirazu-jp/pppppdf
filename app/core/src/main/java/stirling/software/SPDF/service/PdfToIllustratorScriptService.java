package stirling.software.SPDF.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.System.GoogleCloud;
import stirling.software.SPDF.service.handwriting.GeminiOcrClient;

/**
 * PDF構造JSON → Adobe Illustrator ExtendScript (.jsx) 変換サービス。
 * Gemini (Vertex AI) に PDFの構造記述JSONを渡し、Illustratorで実行可能な
 * ExtendScriptコードを生成する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfToIllustratorScriptService {

    private final ApplicationProperties applicationProperties;
    private final GeminiOcrClient geminiOcrClient;

    private static final String SYSTEM_INSTRUCTION =
        "#役割\n"
        + "あなたは、PDFの構造記述からAdobe Illustratorで実行可能なAdobe ExtendScript (.jsx) を生成する専門家です。"
        + "特に日本語の縦書きを含む、レイアウトの再現性を重視します。\n\n"
        + "# タスク定義\n"
        + "PDFのレイアウトと内容を可能な限り忠実に再現するスクリプトを生成することを目的とします。"
        + "ただし、完全な再現は保証されるものではなく、特に複雑な効果やフォントの互換性には限界があることを前提とします。\n\n"
        + "## 入力仕様\n"
        + "- 入力として、PDFの構造を記述したJSONオブジェクトが提供されます。\n"
        + "- このJSONには、ページサイズや、テキスト、図形、画像などの各オブジェクトの位置、サイズ、内容、スタイル情報が含まれます。\n\n"
        + "## 出力仕様\n"
        + "- 生成するのはAdobe ExtendScriptのコード（テキスト）のみです。\n"
        + "- コードはjsxファイルとして保存できる形式とします。\n"
        + "- プレビュー画像や実際のファイル生成は行いません。\n\n"
        + "# 指示\n"
        + "1. あなたは提供されたPDFの構造記述（JSON形式）を解析し、それを再現するためのAdobe ExtendScriptコードを生成します。\n"
        + "2. ドキュメントを新規作成するか、アクティブなドキュメントを使用する基本構造とします。\n"
        + "3. JSON内のpages配列をループし、ページごとにアートボードを作成・調整します。\n"
        + "4. 各ページ内のオブジェクトをループし、オブジェクトのtypeに応じて適切なコードを生成します。\n"
        + "5. 座標系はIllustratorの標準（左上が原点、Y軸は下向き）に変換してコードを生成してください。\n"
        + "6. フォントが見つからない場合に備え、try-catchブロックを使用してエラーを回避するコードを記述してください。\n\n"
        + "## 再現する主な要素\n"
        + "- ページ構造: PDFのページごとにアートボードを生成します。\n"
        + "- テキスト: テキストフレーム（横書き・縦書き）、フォント、サイズ、色、配置を再現します。\n"
        + "- 図形: パス、塗り、線の色と幅を再現します。\n"
        + "- 画像: 配置画像として再現します（パスのプレースホルダー）。\n\n"
        + "## 重要\n"
        + "- 出力はAdobe ExtendScriptコードのみ。説明文やMarkdownコードブロック記号は不要です。\n"
        + "- コードは #target illustrator で始めてください。\n"
        + "- 縦書きテキストはtextFrame.orientation = TextOrientation.VERTICAL を使用してください。\n"
        + "- 色はCMYKColorオブジェクトを使用してください。";

    public boolean isAvailable() {
        return geminiOcrClient.isAvailable();
    }

    /**
     * PDFのJSON構造文字列を受け取り、Illustrator ExtendScriptを返す。
     *
     * @param pdfJsonString PdfJsonConversionServiceで生成したJSON
     * @return Illustratorで実行可能な .jsx スクリプト文字列
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
                            .build();

            GenerativeModel model =
                    new GenerativeModel(cfg.getGemini().getModel(), vertexAi)
                            .withGenerationConfig(generationConfig)
                            .withSystemInstruction(ContentMaker.fromString(SYSTEM_INSTRUCTION));

            log.info("Calling Gemini for Illustrator script generation, JSON length={}", pdfJsonString.length());

            GenerateContentResponse response =
                    model.generateContent(ContentMaker.fromString(pdfJsonString));

            String script = ResponseHandler.getText(response);
            if (script == null || script.isBlank()) {
                throw new IOException("Gemini returned empty script");
            }

            // Markdownコードブロックが付いていれば除去
            script = script.trim();
            if (script.startsWith("```")) {
                script = script.replaceFirst("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();
            }

            log.info("Gemini generated script, length={}", script.length());
            return script;
        }
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
