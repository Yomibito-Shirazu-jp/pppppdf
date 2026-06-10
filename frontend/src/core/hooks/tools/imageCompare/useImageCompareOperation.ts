import { useTranslation } from 'react-i18next';
import { useToolOperation, ToolType } from '@app/hooks/tools/shared/useToolOperation';
import { createStandardErrorHandler } from '@app/utils/toolErrorHandler';
import {
  ImageCompareParameters,
  defaultParameters,
} from '@app/hooks/tools/imageCompare/useImageCompareParameters';

/**
 * Build FormData for image-compare endpoint. Expects exactly TWO selected files:
 * - files[0] → baseImage
 * - files[1] → comparisonImage
 */
export const buildImageCompareFormData = (
  _parameters: ImageCompareParameters,
  files: File[],
): FormData => {
  const formData = new FormData();
  if (files[0]) formData.append('baseImage', files[0]);
  if (files[1]) formData.append('comparisonImage', files[1]);
  return formData;
};

export const imageCompareOperationConfig = {
  toolType: ToolType.multiFile,
  buildFormData: buildImageCompareFormData,
  operationType: 'imageCompare',
  endpoint: '/api/v1/general/image-compare',
  defaultParameters,
  multiFileEndpoint: true,
} as const;

export const useImageCompareOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<ImageCompareParameters>({
    ...imageCompareOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t(
        'imageCompare.error.failed',
        'AI image comparison failed. Verify the Gemini API is enabled and the runtime service account has Vertex AI permissions.',
      ),
    ),
  });
};
