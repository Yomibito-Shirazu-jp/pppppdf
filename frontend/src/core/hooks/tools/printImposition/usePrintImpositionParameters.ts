import { BaseParameters } from '@app/types/parameters';
import { useBaseParameters, BaseParametersHook } from '@app/hooks/tools/shared/useBaseParameters';

export type ImposeLayout = 'folio' | 'quarto' | 'octavo' | 'card-fold4' | 'card-fold8';
export type ImposeOrientation = 'portrait' | 'landscape';

export interface PrintImpositionParameters extends BaseParameters {
  layout: ImposeLayout;
  orientation: ImposeOrientation;
  dimensions: string;
  forms: number;
  margin: number;
  marks: boolean;
  mirrorBackSide: boolean;
  startPage: number;
  endPage: number;
}

export const defaultParameters: PrintImpositionParameters = {
  layout: 'quarto',
  orientation: 'portrait',
  dimensions: 'A4',
  forms: 0,
  margin: 36,
  marks: true,
  mirrorBackSide: false,
  startPage: 1,
  endPage: 0,
};

export type PrintImpositionParametersHook = BaseParametersHook<PrintImpositionParameters>;

export const usePrintImpositionParameters = (): PrintImpositionParametersHook => {
  return useBaseParameters({
    defaultParameters,
    endpointName: 'print-imposition',
    validateFn: (params) =>
      ['folio', 'quarto', 'octavo', 'card-fold4', 'card-fold8'].includes(params.layout) &&
      ['portrait', 'landscape'].includes(params.orientation) &&
      !!params.dimensions &&
      params.margin >= 0 &&
      params.startPage >= 1 &&
      params.endPage >= 0,
  });
};
