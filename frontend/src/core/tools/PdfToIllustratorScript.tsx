import { useTranslation } from 'react-i18next';
import { createToolFlow } from '@app/components/tools/shared/createToolFlow';
import { useBaseTool } from '@app/hooks/tools/shared/useBaseTool';
import { BaseToolProps, ToolComponent } from '@app/types/tool';
import {
  usePdfToIllustratorScriptParameters,
  usePdfToIllustratorScriptOperation,
} from '@app/hooks/tools/pdfToIllustratorScript/usePdfToIllustratorScriptOperation';

const PdfToIllustratorScript = (props: BaseToolProps) => {
  const { t } = useTranslation();

  const base = useBaseTool(
    'pdfToIllustratorScript',
    usePdfToIllustratorScriptParameters,
    usePdfToIllustratorScriptOperation,
    props,
  );

  return createToolFlow({
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
    },
    steps: [],
    executeButton: {
      text: t('pdfToIllustratorScript.submit', 'InDesignスクリプトを生成'),
      isVisible: !base.hasResults,
      loadingText: t('pdfToIllustratorScript.loading', 'Geminiで変換中…'),
      onClick: base.handleExecute,
      endpointEnabled: base.endpointEnabled,
      paramsValid: base.params.validateParameters(),
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t('pdfToIllustratorScript.result', 'InDesignスクリプト生成完了'),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

export default PdfToIllustratorScript as ToolComponent;
