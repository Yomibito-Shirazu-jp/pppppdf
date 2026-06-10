import React from 'react';
import { Stack, Select, Divider, Alert } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import LanguagePicker from '@app/components/tools/ocr/LanguagePicker';
import {
  OCRParameters,
  OCREngine,
  HandwritingOutputFormat,
} from '@app/hooks/tools/ocr/useOCRParameters';
import { Z_INDEX_AUTOMATE_DROPDOWN } from '@app/styles/zIndex';

interface OCRSettingsProps {
  parameters: OCRParameters;
  onParameterChange: <K extends keyof OCRParameters>(key: K, value: OCRParameters[K]) => void;
  disabled?: boolean;
}

const OCRSettings: React.FC<OCRSettingsProps> = ({
  parameters,
  onParameterChange,
  disabled = false
}) => {
  const { t } = useTranslation();
  const isHandwriting = parameters.engine === 'handwriting';

  return (
    <Stack gap="md">
      <Select
        label={t('ocr.settings.engine.label', 'OCR Engine')}
        value={parameters.engine}
        onChange={(value) => onParameterChange('engine', (value as OCREngine) || 'tesseract')}
        data={[
          {
            value: 'tesseract',
            label: t('ocr.settings.engine.tesseract', 'Tesseract (printed text)'),
          },
          {
            value: 'handwriting',
            label: t(
              'ocr.settings.engine.handwriting',
              'Google AI (handwriting / 手書き対応)',
            ),
          },
        ]}
        disabled={disabled}
        comboboxProps={{ withinPortal: true, zIndex: Z_INDEX_AUTOMATE_DROPDOWN }}
      />

      {isHandwriting && (
        <>
          <Alert color="blue" variant="light">
            {t(
              'ocr.settings.engine.handwritingNotice',
              'Handwriting mode uses Google Gemini with Document AI fallback. Use this for transcription (文字起こし) of handwritten manuscripts. Language selection is not required — Gemini detects script automatically.',
            )}
          </Alert>

          <Select
            label={t('ocr.settings.outputFormat.label', 'Output Format')}
            value={parameters.outputFormat}
            onChange={(value) =>
              onParameterChange('outputFormat', (value as HandwritingOutputFormat) || 'text')
            }
            data={[
              {
                value: 'text',
                label: t(
                  'ocr.settings.outputFormat.text',
                  'Transcript only (.txt) — recommended for 文字起こし',
                ),
              },
              {
                value: 'zip',
                label: t(
                  'ocr.settings.outputFormat.zip',
                  'Original PDF + transcript (.zip)',
                ),
              },
            ]}
            disabled={disabled}
            comboboxProps={{ withinPortal: true, zIndex: Z_INDEX_AUTOMATE_DROPDOWN }}
          />
        </>
      )}

      {!isHandwriting && (
        <Select
          label={t('ocr.settings.ocrMode.label', 'OCR Mode')}
          value={parameters.ocrType}
          onChange={(value) => onParameterChange('ocrType', value || 'skip-text')}
          data={[
            { value: 'skip-text', label: t('ocr.settings.ocrMode.auto', 'Auto (skip text layers)') },
            { value: 'force-ocr', label: t('ocr.settings.ocrMode.force', 'Force (re-OCR all, replace text)') },
            { value: 'Normal', label: t('ocr.settings.ocrMode.strict', 'Strict (abort if text found)') },
          ]}
          disabled={disabled}
          comboboxProps={{ withinPortal: true, zIndex: Z_INDEX_AUTOMATE_DROPDOWN }}
        />
      )}

      {!isHandwriting && (
        <>
          <Divider />
          <LanguagePicker
            value={parameters.languages || []}
            onChange={(value) => onParameterChange('languages', value)}
            placeholder={t('ocr.settings.languages.placeholder', 'Select languages')}
            disabled={disabled}
            label={t('ocr.settings.languages.label', 'Languages')}
          />
        </>
      )}
    </Stack>
  );
};

export default OCRSettings;
