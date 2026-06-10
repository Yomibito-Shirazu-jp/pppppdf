import { BaseParameters } from '@app/types/parameters';
import { useBaseParameters, BaseParametersHook } from '@app/hooks/tools/shared/useBaseParameters';

export type OCREngine = 'tesseract' | 'handwriting';
export type HandwritingOutputFormat = 'text' | 'zip';

export interface OCRParameters extends BaseParameters {
  engine: OCREngine;
  outputFormat: HandwritingOutputFormat;
  languages: string[];
  ocrType: string;
  ocrRenderType: string;
  additionalOptions: string[];
}

export type OCRParametersHook = BaseParametersHook<OCRParameters>;

export const defaultParameters: OCRParameters = {
  engine: 'tesseract',
  outputFormat: 'text',
  languages: [],
  ocrType: 'skip-text',
  ocrRenderType: 'hocr',
  additionalOptions: [],
};

export const useOCRParameters = (): OCRParametersHook => {
  return useBaseParameters({
    defaultParameters,
    endpointName: 'ocr-pdf',
    validateFn: (params) => {
      // Handwriting engine doesn't require a language selection — Gemini detects automatically
      if (params.engine === 'handwriting') return true;
      return params.languages.length > 0;
    },
  });
};
