import { useTranslation } from 'react-i18next';
import { createToolFlow } from '@app/components/tools/shared/createToolFlow';
import PrintImpositionSettings from '@app/components/tools/printImposition/PrintImpositionSettings';
import { usePrintImpositionParameters } from '@app/hooks/tools/printImposition/usePrintImpositionParameters';
import { usePrintImpositionOperation } from '@app/hooks/tools/printImposition/usePrintImpositionOperation';
import { useBaseTool } from '@app/hooks/tools/shared/useBaseTool';
import { usePrintImpositionTips } from '@app/components/tooltips/usePrintImpositionTips';
import { BaseToolProps, ToolComponent } from '@app/types/tool';

const PrintImposition = (props: BaseToolProps) => {
  const { t } = useTranslation();

  const base = useBaseTool(
    'printImposition',
    usePrintImpositionParameters,
    usePrintImpositionOperation,
    props,
  );

  const printTips = usePrintImpositionTips();

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
        tooltip: printTips,
        content: (
          <PrintImpositionSettings
            parameters={base.params.parameters}
            onParameterChange={base.params.updateParameter}
            disabled={base.endpointLoading}
          />
        ),
      },
    ],
    executeButton: {
      text: t('printImposition.submit', 'Run Print Imposition'),
      isVisible: !base.hasResults,
      loadingText: t('loading'),
      onClick: base.handleExecute,
      endpointEnabled: base.endpointEnabled,
      paramsValid: base.params.validateParameters(),
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t('printImposition.title', 'Print Imposition Results'),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

export default PrintImposition as ToolComponent;
