import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Stack,
  Text,
  Divider,
  Collapse,
  Button,
  NumberInput,
  Checkbox,
  Select,
  TextInput,
} from '@mantine/core';
import {
  PrintImpositionParameters,
  ImposeLayout,
  ImposeOrientation,
} from '@app/hooks/tools/printImposition/usePrintImpositionParameters';

interface PrintImpositionSettingsProps {
  parameters: PrintImpositionParameters;
  onParameterChange: (key: keyof PrintImpositionParameters, value: any) => void;
  disabled?: boolean;
}

const PrintImpositionSettings = ({
  parameters,
  onParameterChange,
  disabled = false,
}: PrintImpositionSettingsProps) => {
  const { t } = useTranslation();
  const [advancedOpen, setAdvancedOpen] = useState(false);

  return (
    <Stack gap="md">
      <Divider ml="-md" />

      <Select
        label={t('printImposition.layout.label', 'Imposition layout')}
        description={t(
          'printImposition.layout.description',
          'quarto = 8 pages per sheet (most common book printing). folio = 4 pages. octavo = 16 pages. card-fold = simple pamphlets.',
        )}
        value={parameters.layout}
        onChange={(value) =>
          onParameterChange('layout', (value as ImposeLayout) || 'quarto')
        }
        data={[
          { value: 'folio', label: t('printImposition.layout.folio', 'Folio (4 pages / 二折)') },
          { value: 'quarto', label: t('printImposition.layout.quarto', 'Quarto (8 pages / 四折) — recommended') },
          { value: 'octavo', label: t('printImposition.layout.octavo', 'Octavo (16 pages / 八折)') },
          { value: 'card-fold4', label: t('printImposition.layout.cardFold4', 'Card-fold 4 (4 pages, simple pamphlet)') },
          { value: 'card-fold8', label: t('printImposition.layout.cardFold8', 'Card-fold 8 (8 pages, simple pamphlet)') },
        ]}
        disabled={disabled}
      />

      <Select
        label={t('printImposition.orientation.label', 'Sheet orientation')}
        value={parameters.orientation}
        onChange={(value) =>
          onParameterChange('orientation', (value as ImposeOrientation) || 'portrait')
        }
        data={[
          { value: 'portrait', label: t('printImposition.orientation.portrait', 'Portrait (縦)') },
          { value: 'landscape', label: t('printImposition.orientation.landscape', 'Landscape (横)') },
        ]}
        disabled={disabled}
      />

      <TextInput
        label={t('printImposition.dimensions.label', 'Sheet dimensions')}
        description={t(
          'printImposition.dimensions.description',
          'Paper-size name (LETTER, A4, A3, B4, ...) or "WIDTHxHEIGHT" in points (e.g. "595x842").',
        )}
        value={parameters.dimensions}
        onChange={(event) => onParameterChange('dimensions', event.currentTarget.value)}
        disabled={disabled}
      />

      <Checkbox
        checked={parameters.marks}
        onChange={(event) => onParameterChange('marks', event.currentTarget.checked)}
        disabled={disabled}
        label={
          <div>
            <Text size="sm">
              {t('printImposition.marks.label', 'Include registration / crop marks (トンボ)')}
            </Text>
            <Text size="xs" c="dimmed">
              {t(
                'printImposition.marks.tooltip',
                'Required for production printing — leave on unless you specifically need clean output.',
              )}
            </Text>
          </div>
        }
      />

      <Checkbox
        checked={parameters.mirrorBackSide}
        onChange={(event) => onParameterChange('mirrorBackSide', event.currentTarget.checked)}
        disabled={disabled}
        label={
          <div>
            <Text size="sm">
              {t('printImposition.mirrorBackSide.label', '裏面左右を反転（版反転補正）')}
            </Text>
            <Text size="xs" c="dimmed">
              {t(
                'printImposition.mirrorBackSide.tooltip',
                '短辺とじ（左右フリップ）の版焼きワークフローで裏面が反転する場合に有効にしてください。',
              )}
            </Text>
          </div>
        }
      />

      <Divider />

      <Stack gap="sm">
        <Button variant="subtle" onClick={() => setAdvancedOpen(!advancedOpen)} disabled={disabled}>
          {t('printImposition.advanced.toggle', 'Advanced Options')} {advancedOpen ? '▲' : '▼'}
        </Button>

        <Collapse in={advancedOpen}>
          <Stack gap="md" mt="md">
            <NumberInput
              label={t('printImposition.forms.label', 'Forms per signature (0 = layout default)')}
              description={t(
                'printImposition.forms.description',
                'Lower = thinner signature. Quarto default = 4, Folio default = 8.',
              )}
              value={parameters.forms}
              onChange={(value) => onParameterChange('forms', value || 0)}
              min={0}
              max={32}
              step={1}
              disabled={disabled}
            />

            <NumberInput
              label={t('printImposition.margin.label', 'Margin (points)')}
              description={t(
                'printImposition.margin.description',
                '36 points ≈ 0.5 inch ≈ 12.7 mm.',
              )}
              value={parameters.margin}
              onChange={(value) => onParameterChange('margin', value || 36)}
              min={0}
              max={200}
              step={6}
              disabled={disabled}
            />

            <NumberInput
              label={t('printImposition.startPage.label', 'Start page (1-indexed)')}
              value={parameters.startPage}
              onChange={(value) => onParameterChange('startPage', value || 1)}
              min={1}
              step={1}
              disabled={disabled}
            />

            <NumberInput
              label={t('printImposition.endPage.label', 'End page (0 = last)')}
              value={parameters.endPage}
              onChange={(value) => onParameterChange('endPage', value || 0)}
              min={0}
              step={1}
              disabled={disabled}
            />
          </Stack>
        </Collapse>
      </Stack>
    </Stack>
  );
};

export default PrintImpositionSettings;
