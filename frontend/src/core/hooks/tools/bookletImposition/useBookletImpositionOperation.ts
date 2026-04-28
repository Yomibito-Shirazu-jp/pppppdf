import { useTranslation } from 'react-i18next';
import { useToolOperation, ToolType } from '@app/hooks/tools/shared/useToolOperation';
import { createStandardErrorHandler } from '@app/utils/toolErrorHandler';
import { BookletImpositionParameters, defaultParameters } from '@app/hooks/tools/bookletImposition/useBookletImpositionParameters';

// Static configuration that can be used by both the hook and automation executor
export const buildBookletImpositionFormData = (parameters: BookletImpositionParameters, file: File): FormData => {
  const formData = new FormData();
  formData.append("fileInput", file);
  formData.append("pagesPerSheet", parameters.pagesPerSheet.toString());
  formData.append("addBorder", parameters.addBorder.toString());
  formData.append("spineLocation", parameters.spineLocation);
  formData.append("addGutter", parameters.addGutter.toString());
  formData.append("gutterSize", parameters.gutterSize.toString());
  formData.append("doubleSided", parameters.doubleSided.toString());
  formData.append("duplexPass", parameters.duplexPass);
  formData.append("flipOnShortEdge", parameters.flipOnShortEdge.toString());
  return formData;
};

// Static configuration object
export const bookletImpositionOperationConfig = {
  toolType: ToolType.singleFile,
  buildFormData: buildBookletImpositionFormData,
  operationType: 'bookletImposition',
  endpoint: '/api/v1/general/booklet-imposition',
  defaultParameters,
} as const;

export const useBookletImpositionOperation = () => {
  const { t } = useTranslation();

  const standardHandler = createStandardErrorHandler(
    t('bookletImposition.error.failed', '小冊子の面付け中にエラーが発生しました。'),
  );

  return useToolOperation<BookletImpositionParameters>({
    ...bookletImpositionOperationConfig,
    getErrorMessage: (error: any) => {
      // ProblemDetail body (Spring 400) — extract `detail` and translate known business errors.
      let serverDetail: string | undefined;
      const data = error?.response?.data;
      if (data && typeof data === 'object' && typeof data.detail === 'string') {
        serverDetail = data.detail;
      } else if (typeof data === 'string') {
        serverDetail = data;
      }

      if (serverDetail && /pagesPerSheet\s*>\s*2\s*requires\s*a\s*FACILIS/i.test(serverDetail)) {
        return t(
          'bookletImposition.error.facilisRequired',
          '4-up 以上の面付けには FACILIS テンプレートが必要です。Advanced Options から DB.zip をアップロードしてテンプレを選ぶか、「2-up (中綴じ)」を選んでください。',
        ) as string;
      }

      if (serverDetail) {
        return serverDetail;
      }

      return standardHandler(error);
    },
  });
};