import { BaseParameters } from '@app/types/parameters';
import { useBaseParameters, BaseParametersHook } from '@app/hooks/tools/shared/useBaseParameters';

export type SignaturePages = 4 | 8 | 16 | 32;

export interface AiImpositionParameters extends BaseParameters {
  signaturePages: SignaturePages;
  addBorder: boolean;
  spineLocation: 'LEFT' | 'RIGHT';
  addGutter: boolean;
  gutterSize: number;
  doubleSided: boolean;
  duplexPass: 'BOTH' | 'FIRST' | 'SECOND';
  flipOnShortEdge: boolean;
}

export const defaultParameters: AiImpositionParameters = {
  signaturePages: 16,
  addBorder: false,
  spineLocation: 'LEFT',
  addGutter: false,
  gutterSize: 12,
  doubleSided: true,
  duplexPass: 'BOTH',
  flipOnShortEdge: false,
};

export type AiImpositionParametersHook = BaseParametersHook<AiImpositionParameters>;

export const useAiImpositionParameters = (): AiImpositionParametersHook => {
  return useBaseParameters({
    defaultParameters,
    endpointName: 'ai-imposition',
    validateFn: (params) => {
      return [4, 8, 16, 32].includes(params.signaturePages);
    },
  });
};
