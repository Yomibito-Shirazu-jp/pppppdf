import { BaseParameters } from '@app/types/parameters';
import { useBaseParameters, BaseParametersHook } from '@app/hooks/tools/shared/useBaseParameters';

export type SignaturePagesPerSheet = 2 | 4 | 8 | 16 | 32;
export type CropMarkType = 'NONE' | 'CENTER' | 'CUTTING' | 'FOLDING' | 'RECT';

export interface BookletImpositionParameters extends BaseParameters {
  // 折丁 (signature) — pages per press sheet
  pagesPerSheet: SignaturePagesPerSheet;
  addBorder: boolean;
  spineLocation: 'LEFT' | 'RIGHT';
  // 両面整合
  doubleSided: boolean;
  duplexPass: 'BOTH' | 'FIRST' | 'SECOND';
  flipOnShortEdge: boolean;
  // ドブ (bleed) — single value (legacy) and 4-side independent
  addGutter: boolean;
  gutterSize: number;
  bleedIndependent: boolean;
  bleedTopMm: number;
  bleedBottomMm: number;
  bleedInsideMm: number;
  bleedOutsideMm: number;
  // 咥え (gripper edge)
  gripperEnabled: boolean;
  gripperMm: number;
  // 背丁 (spine signature)
  spineSignatureEnabled: boolean;
  spineSignatureText: string;
  // トンボ (crop / registration marks)
  cropMarkType: CropMarkType;
  registrationMarks: boolean;
  // FACILIS .lay template (optional, overrides manual settings when set)
  templateId: string | null;
}

export const defaultParameters: BookletImpositionParameters = {
  pagesPerSheet: 2,
  addBorder: false,
  spineLocation: 'LEFT',
  doubleSided: true,
  duplexPass: 'BOTH',
  flipOnShortEdge: false,
  addGutter: false,
  gutterSize: 12,
  bleedIndependent: false,
  bleedTopMm: 3,
  bleedBottomMm: 3,
  bleedInsideMm: 3,
  bleedOutsideMm: 3,
  gripperEnabled: false,
  gripperMm: 10,
  spineSignatureEnabled: false,
  spineSignatureText: '%JobName% %SignatureNo%',
  cropMarkType: 'NONE',
  registrationMarks: false,
  templateId: null,
};

const ALLOWED_PAGES_PER_SHEET: ReadonlySet<number> = new Set([2, 4, 8, 16, 32]);

export type BookletImpositionParametersHook = BaseParametersHook<BookletImpositionParameters>;

export const useBookletImpositionParameters = (): BookletImpositionParametersHook => {
  return useBaseParameters({
    defaultParameters,
    endpointName: 'booklet-imposition',
    validateFn: (params) => ALLOWED_PAGES_PER_SHEET.has(params.pagesPerSheet),
  });
};