import { BaseParameters } from '@app/types/parameters';
import { useBaseParameters, BaseParametersHook } from '@app/hooks/tools/shared/useBaseParameters';

export interface ImageCompareParameters extends BaseParameters {
  // No tuning parameters yet — the comparison is fully driven by the Gemini prompt server-side.
  _placeholder?: never;
}

export const defaultParameters: ImageCompareParameters = {};

export type ImageCompareParametersHook = BaseParametersHook<ImageCompareParameters>;

export const useImageCompareParameters = (): ImageCompareParametersHook => {
  return useBaseParameters({
    defaultParameters,
    endpointName: 'image-compare',
    validateFn: () => true,
  });
};
