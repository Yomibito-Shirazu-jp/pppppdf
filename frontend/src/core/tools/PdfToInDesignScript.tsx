import { useTranslation } from 'react-i18next';
import { createToolFlow } from '@app/components/tools/shared/createToolFlow';
import { useBaseTool } from '@app/hooks/tools/shared/useBaseTool';
import { BaseToolProps, ToolComponent } from '@app/types/tool';
import {
  usePdfToInDesignScriptParameters,
  usePdfToInDesignScriptOperation,
} from '@app/hooks/tools/pdfToInDesignScript/usePdfToInDesignScriptOperation';

const PdfToInDesignScript = (props: BaseToolProps) => {
  const { t } = useTranslation();

  const base = useBaseTool(
    'pdfToInDesignScript',
    usePdfToInDesignScriptParameters,
    usePdfToInDesignScriptOperation,
    props,
  );

  return createToolFlow({
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
    },
    steps: [],
    executeButton: {
      text: t('pdfToInDesignScript.submit', 'InDesignスクリプトを生成'),
      isVisible: !base.hasResults,
      loadingText: t('pdfToInDesignScript.loading', 'Geminiで変換中…'),
      onClick: base.handleExecute,
      endpointEnabled: base.endpointEnabled,
      paramsValid: base.params.validateParameters(),
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t('pdfToInDesignScript.result', 'InDesignスクリプト生成完了'),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

export default PdfToInDesignScript as ToolComponent;
