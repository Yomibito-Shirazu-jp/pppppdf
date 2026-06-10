import { useTranslation } from 'react-i18next';
import { TooltipContent } from '@app/types/tips';

/**
 * Tooltip content for the "印刷面付" (Print Imposition) tool. Mirrors the structure of
 * useBookletImpositionTips so the tool feels familiar to users coming from Booklet Imposition,
 * but explains the jamis/impose-specific options (layout, sheet dimensions, registration marks).
 */
export const usePrintImpositionTips = (): TooltipContent => {
  const { t } = useTranslation();

  return {
    header: {
      title: t('printImposition.tooltip.header.title', 'Print Imposition (印刷面付) Guide'),
    },
    tips: [
      {
        title: t('printImposition.tooltip.description.title', 'What is Print Imposition?'),
        description: t(
          'printImposition.tooltip.description.text',
          'Production-grade imposition powered by jamis/impose (the pdf-impose Ruby gem). Pages are arranged on the sheet so when printed, folded, and bound, they read in the correct order. Unlike "AI 折面付", this includes registration marks (トンボ) and supports several standard layouts used in book printing.',
        ),
      },
      {
        title: t('printImposition.tooltip.layouts.title', 'Layout choices'),
        description: t('printImposition.tooltip.layouts.text', 'Pick based on signature thickness:'),
        bullets: [
          t('printImposition.tooltip.layouts.folio', 'Folio (二折): 4 pages per signature — thin pamphlets'),
          t('printImposition.tooltip.layouts.quarto', 'Quarto (四折): 8 pages — most common book printing'),
          t('printImposition.tooltip.layouts.octavo', 'Octavo (八折): 16 pages — thicker books'),
          t('printImposition.tooltip.layouts.cardFold', 'Card-fold 4 / 8: simple pamphlets folded by hand'),
        ],
      },
      {
        title: t('printImposition.tooltip.dimensions.title', 'Sheet dimensions'),
        description: t(
          'printImposition.tooltip.dimensions.text',
          'Specify either a paper-size name or explicit width × height in points (1pt = 1/72 inch).',
        ),
        bullets: [
          t('printImposition.tooltip.dimensions.named', 'Named: A4, A3, B4, LETTER, LEGAL, etc.'),
          t('printImposition.tooltip.dimensions.custom', 'Custom: "595x842" (= A4), "842x1191" (= A3)'),
          t('printImposition.tooltip.dimensions.note', 'The sheet must be larger than the input page (after layout fold)'),
        ],
      },
      {
        title: t('printImposition.tooltip.marks.title', 'Registration marks (トンボ)'),
        description: t(
          'printImposition.tooltip.marks.text',
          'Crop / registration marks help the press operator align cuts and folds. Always leave on for production printing — turn off only for clean-output preview.',
        ),
      },
      {
        title: t('printImposition.tooltip.advanced.title', 'Advanced'),
        description: t('printImposition.tooltip.advanced.text', 'Fine-tune the run:'),
        bullets: [
          t('printImposition.tooltip.advanced.forms', 'Forms per signature (0 = layout default; lower = thinner gathering)'),
          t('printImposition.tooltip.advanced.margin', 'Margin in points (36 ≈ 0.5 inch ≈ 12.7 mm)'),
          t('printImposition.tooltip.advanced.range', 'Start / End page: impose only a slice of the document'),
        ],
      },
    ],
  };
};
