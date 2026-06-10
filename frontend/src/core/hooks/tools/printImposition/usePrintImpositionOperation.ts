import { useTranslation } from 'react-i18next';
import { useToolOperation, ToolType } from '@app/hooks/tools/shared/useToolOperation';
import { createStandardErrorHandler } from '@app/utils/toolErrorHandler';
import {
  PrintImpositionParameters,
  defaultParameters,
} from '@app/hooks/tools/printImposition/usePrintImpositionParameters';

export const buildPrintImpositionFormData = (
  parameters: PrintImpositionParameters,
  file: File,
): FormData => {
  const formData = new FormData();
  formData.append('fileInput', file);
  formData.append('layout', parameters.layout);
  formData.append('orientation', parameters.orientation);
  formData.append('dimensions', parameters.dimensions);
  formData.append('forms', parameters.forms.toString());
  formData.append('margin', parameters.margin.toString());
  formData.append('marks', parameters.marks.toString());
  formData.append('startPage', parameters.startPage.toString());
  formData.append('endPage', parameters.endPage.toString());
  return formData;
};

export const printImpositionOperationConfig = {
  toolType: ToolType.singleFile,
  buildFormData: buildPrintImpositionFormData,
  operationType: 'printImposition',
  endpoint: '/api/v1/general/print-imposition',
  defaultParameters,
} as const;

export const usePrintImpositionOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<PrintImpositionParameters>({
    ...printImpositionOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t(
        'printImposition.error.failed',
        'An error occurred while running the print imposition (jamis/impose).',
      ),
    ),
  });
};
