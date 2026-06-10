import { useTranslation } from "react-i18next";
import { createToolFlow } from "@app/components/tools/shared/createToolFlow";
import AiImpositionSettings from "@app/components/tools/aiImposition/AiImpositionSettings";
import { useAiImpositionParameters } from "@app/hooks/tools/aiImposition/useAiImpositionParameters";
import { useAiImpositionOperation } from "@app/hooks/tools/aiImposition/useAiImpositionOperation";
import { useBaseTool } from "@app/hooks/tools/shared/useBaseTool";
import { BaseToolProps, ToolComponent } from "@app/types/tool";

const AiImposition = (props: BaseToolProps) => {
  const { t } = useTranslation();

  const base = useBaseTool(
    "aiImposition",
    useAiImpositionParameters,
    useAiImpositionOperation,
    props,
  );

  return createToolFlow({
    files: {
      selectedFiles: base.selectedFiles,
      isCollapsed: base.hasResults,
    },
    steps: [
      {
        title: "Settings",
        isCollapsed: base.settingsCollapsed,
        onCollapsedClick: base.settingsCollapsed ? base.handleSettingsReset : undefined,
        content: (
          <AiImpositionSettings
            parameters={base.params.parameters}
            onParameterChange={base.params.updateParameter}
            disabled={base.endpointLoading}
          />
        ),
      },
    ],
    executeButton: {
      text: t("aiImposition.submit", "Create Imposed PDF"),
      isVisible: !base.hasResults,
      loadingText: t("loading"),
      onClick: base.handleExecute,
      endpointEnabled: base.endpointEnabled,
      paramsValid: base.params.validateParameters(),
    },
    review: {
      isVisible: base.hasResults,
      operation: base.operation,
      title: t("aiImposition.title", "AI Imposition Results"),
      onFileClick: base.handleThumbnailClick,
      onUndo: base.handleUndo,
    },
  });
};

export default AiImposition as ToolComponent;
