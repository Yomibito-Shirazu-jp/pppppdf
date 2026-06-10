import { useTranslation } from 'react-i18next';
import { createToolFlow } from '@app/components/tools/shared/createToolFlow';
import ImageCompareSettings from '@app/components/tools/imageCompare/ImageCompareSettings';
import { useImageCompareParameters } from '@app/hooks/tools/imageCompare/useImageCompareParameters';
import { useImageCompareOperation } from '@app/hooks/tools/imageCompare/useImageCompareOperation';
import { useBaseTool } from '@app/hooks/tools/shared/useBaseTool';
import { BaseToolProps, ToolComponent } from '@app/types/tool';

const ImageCompare = (props: BaseToolProps) => {
  const { t } = useTranslation();

  const base = useBaseTool(
    'imageCompare',
    useImageCompareParameters,
    useImageCompareOperation,
    props,
    { minFiles: 2 },
  );

  return createToolFlow({
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
    },
    steps: [
      {
        title: 'Settings',
        isCollapsed: base.settingsCollapsed,
        onCollapsedClick: base.settingsCollapsed ? base.handleSettingsReset : undefined,
        content: (
          <ImageCompareSettings
            parameters={base.params.parameters}
            onParameterChange={base.params.updateParameter}
            disabled={base.endpointLoading}
          />
        ),
      },
    ],
    executeButton: {
      text: t('imageCompare.submit', 'AI画像比較を実行'),
      isVisible: !base.hasResults,
      loadingText: t('imageCompare.loading', 'Gemini Vision で比較中...'),
      onClick: base.handleExecute,
      endpointEnabled: base.endpointEnabled,
      paramsValid: base.selectedFiles.length === 2,
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t('imageCompare.title', 'AI 画像比較結果'),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

export default ImageCompare as ToolComponent;
