import { useTranslation } from 'react-i18next';
import { useToolOperation, ToolType } from '@app/hooks/tools/shared/useToolOperation';
import { createStandardErrorHandler } from '@app/utils/toolErrorHandler';
import { BaseParameters } from '@app/types/parameters';
import { useBaseParameters, BaseParametersHook } from '@app/hooks/tools/shared/useBaseParameters';

export interface PdfToInDesignScriptParameters extends BaseParameters {}

const defaultParameters: PdfToInDesignScriptParameters = {};

export type PdfToInDesignScriptParametersHook = BaseParametersHook<PdfToInDesignScriptParameters>;

export const usePdfToInDesignScriptParameters = (): PdfToInDesignScriptParametersHook => {
  return useBaseParameters({
    defaultParameters,
    endpointName: 'pdf-to-indesign-script',
    validateFn: () => true,
  });
};

const buildFormData = (
  _parameters: PdfToInDesignScriptParameters,
  file: File,
): FormData => {
  const formData = new FormData();
  formData.append('fileInput', file);
  return formData;
};

export const pdfToInDesignScriptOperationConfig = {
  toolType: ToolType.singleFile,
  buildFormData,
  operationType: 'pdfToInDesignScript',
  endpoint: '/api/v1/general/pdf-to-indesign-script',
  defaultParameters,
} as const;

export const usePdfToInDesignScriptOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<PdfToInDesignScriptParameters>({
    ...pdfToInDesignScriptOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t(
        'pdfToInDesignScript.error.failed',
        'InDesignスクリプトの生成に失敗しました。Google Cloud設定を確認してください。',
      ),
    ),
  });
};
