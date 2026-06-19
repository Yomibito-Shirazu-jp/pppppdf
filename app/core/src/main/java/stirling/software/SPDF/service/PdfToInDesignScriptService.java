package stirling.software.SPDF.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.service.handwriting.GeminiOcrClient;
import stirling.software.common.model.ApplicationProperties;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfToInDesignScriptService {

    private final ApplicationProperties applicationProperties;
    private final GeminiOcrClient geminiOcrClient;
    private final PdfToIllustratorScriptService delegate;

    private static final String SYSTEM_INSTRUCTION =
        "#役割\n"
        + "あなたは、PDFの構造記述からAdobe InDesignで実行可能なAdobe ExtendScript (.jsx) を生成する専門家です。"
        + "特に日本語の縦書きを含む、レイアウトの再現性を重視します。\n\n"
        + "# タスク定義\n"
        + "PDFのレイアウトと内容を可能な限り忠実に再現するスクリプトを生成することを目的とします。\n\n"
        + "## 入力仕様\n"
        + "- 入力として、PDFの構造を記述したJSONオブジェクトが提供されます。\n"
        + "- 全てのオブジェクトのジオメトリは `bounds` プロパティ `[top, left, bottom, right]` で定義されます。\n\n"
        + "## 出力仕様\n"
        + "- 必ずJSONオブジェクト {\"response\": \"...JSXコード...\"} の形式で返してください。\n\n"
        + "# 指示\n"
        + "1. PDFの構造記述（JSON形式）を解析し、Adobe InDesign用ExtendScriptコードを生成します。\n"
        + "2. #target indesign で始めてください。\n"
        + "3. 単位系はポイント(points)に設定してください。\n"
        + "4. JSON内のpages配列をループし、ページごとにInDesignのページを作成・調整します。\n"
        + "5. geometricBoundsは[top, left, bottom, right]の順で設定してください。\n"
        + "6. フォントが見つからない場合に備え、try-catchブロックを使用してください。\n\n"
        + "## 再現する主な要素\n"
        + "- テキストフレーム: page.textFrames.add()、geometricBounds=[top,left,bottom,right]\n"
        + "- 縦書き: storyPreferences.storyOrientation = StoryHorizontalOrVertical.VERTICAL\n"
        + "- CMYK色: doc.colors.add({model:ColorModel.PROCESS, space:ColorSpace.CMYK, colorValue:[C,M,Y,K]})\n\n"
        + "## 実行例\n"
        + "```jsx\n"
        + "#target indesign\n\n"
        + "(function(){\n"
        + "  var doc = app.documents.length == 0 ? app.documents.add() : app.activeDocument;\n"
        + "  doc.viewPreferences.horizontalMeasurementUnits = MeasurementUnits.POINTS;\n"
        + "  doc.viewPreferences.verticalMeasurementUnits = MeasurementUnits.POINTS;\n"
        + "  var page = doc.pages.item(0);\n"
        + "  doc.documentPreferences.pageWidth = 595 + 'pt';\n"
        + "  doc.documentPreferences.pageHeight = 842 + 'pt';\n"
        + "  var tf = page.textFrames.add();\n"
        + "  tf.geometricBounds = [100, 500, 400, 550];\n"
        + "  tf.contents = '縦書きテスト';\n"
        + "  tf.storyPreferences.storyOrientation = StoryHorizontalOrVertical.VERTICAL;\n"
        + "  var rect = page.rectangles.add();\n"
        + "  rect.geometricBounds = [600, 100, 700, 300];\n"
        + "  var color = doc.colors.add({name:'CMYK_0_100_100_0',model:ColorModel.PROCESS,"
        +   "space:ColorSpace.CMYK,colorValue:[0,100,100,0]});\n"
        + "  rect.fillColor = color;\n"
        + "})();\n"
        + "```\n\n"
        + "必ず以下のJSON形式で返してください:\n"
        + "{\"response\": \"#target indesign\\n\\n(function(){\\n  ...\\n})();\"}";

    public boolean isAvailable() {
        String apiKey = System.getenv("GOOGLE_CLOUD_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) return true;
        return geminiOcrClient.isAvailable();
    }

    public String generateScript(String pdfJsonString) throws IOException {
        String apiKey = System.getenv("GOOGLE_CLOUD_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            return delegate.generateWithApiKey(pdfJsonString, apiKey, SYSTEM_INSTRUCTION, "InDesign");
        }
        return delegate.generateWithVertexAI(pdfJsonString, SYSTEM_INSTRUCTION, "InDesign");
    }
}
