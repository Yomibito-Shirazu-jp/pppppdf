import { useTranslation } from 'react-i18next';
import { useToolOperation, ToolType } from '@app/hooks/tools/shared/useToolOperation';
import { createStandardErrorHandler } from '@app/utils/toolErrorHandler';
import { BaseParameters } from '@app/types/parameters';
import { useBaseParameters, BaseParametersHook } from '@app/hooks/tools/shared/useBaseParameters';

export interface PdfToIllustratorScriptParameters extends BaseParameters {}

const defaultParameters: PdfToIllustratorScriptParameters = {};

export type PdfToIllustratorScriptParametersHook = BaseParametersHook<PdfToIllustratorScriptParameters>;

export const usePdfToIllustratorScriptParameters = (): PdfToIllustratorScriptParametersHook => {
  return useBaseParameters({
    defaultParameters,
    endpointName: 'pdf-to-illustrator-script',
    validateFn: () => true,
  });
};

const buildFormData = (
  _parameters: PdfToIllustratorScriptParameters,
  file: File,
): FormData => {
  const formData = new FormData();
  formData.append('fileInput', file);
  return formData;
};

export const pdfToIllustratorScriptOperationConfig = {
  toolType: ToolType.singleFile,
  buildFormData,
  operationType: 'pdfToIllustratorScript',
  endpoint: '/api/v1/general/pdf-to-illustrator-script',
  defaultParameters,
} as const;

export const usePdfToIllustratorScriptOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<PdfToIllustratorScriptParameters>({
    ...pdfToIllustratorScriptOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t(
        'pdfToIllustratorScript.error.failed',
        'Illustratorスクリプトの生成に失敗しました。Google Cloud設定を確認してください。',
      ),
    ),
  });
};
