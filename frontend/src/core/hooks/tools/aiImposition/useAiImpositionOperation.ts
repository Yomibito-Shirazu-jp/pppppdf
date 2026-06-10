import { useTranslation } from 'react-i18next';
import { useToolOperation, ToolType } from '@app/hooks/tools/shared/useToolOperation';
import { createStandardErrorHandler } from '@app/utils/toolErrorHandler';
import {
  AiImpositionParameters,
  defaultParameters,
} from '@app/hooks/tools/aiImposition/useAiImpositionParameters';

export const buildAiImpositionFormData = (
  parameters: AiImpositionParameters,
  file: File,
): FormData => {
  const formData = new FormData();
  formData.append('fileInput', file);
  formData.append('signaturePages', parameters.signaturePages.toString());
  formData.append('addBorder', parameters.addBorder.toString());
  formData.append('spineLocation', parameters.spineLocation);
  formData.append('addGutter', parameters.addGutter.toString());
  formData.append('gutterSize', parameters.gutterSize.toString());
  formData.append('doubleSided', parameters.doubleSided.toString());
  formData.append('duplexPass', parameters.duplexPass);
  formData.append('flipOnShortEdge', parameters.flipOnShortEdge.toString());
  return formData;
};

export const aiImpositionOperationConfig = {
  toolType: ToolType.singleFile,
  buildFormData: buildAiImpositionFormData,
  operationType: 'aiImposition',
  endpoint: '/api/v1/general/ai-imposition',
  defaultParameters,
} as const;

export const useAiImpositionOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<AiImpositionParameters>({
    ...aiImpositionOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t('aiImposition.error.failed', 'An error occurred while creating the imposed PDF.'),
    ),
  });
};
