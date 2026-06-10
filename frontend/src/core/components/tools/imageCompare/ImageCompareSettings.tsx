import { useTranslation } from 'react-i18next';
import { Stack, Text, Alert, Divider } from '@mantine/core';
import { ImageCompareParameters } from '@app/hooks/tools/imageCompare/useImageCompareParameters';

interface ImageCompareSettingsProps {
  parameters: ImageCompareParameters;
  onParameterChange: (key: keyof ImageCompareParameters, value: any) => void;
  disabled?: boolean;
}

const ImageCompareSettings = ({}: ImageCompareSettingsProps) => {
  const { t } = useTranslation();

  return (
    <Stack gap="md">
      <Divider ml="-md" />
      <Alert color="blue" variant="light">
        <Text size="sm" fw={600}>
          {t('imageCompare.settings.title', 'AI 画像比較 (Gemini Vision + 画素差分)')}
        </Text>
        <Text size="xs" c="dimmed" mt={4}>
          {t(
            'imageCompare.settings.description',
            '2つの画像 (PNG / JPG / WebP / PDFのいずれか) を比較します。出力には以下が含まれます: ①画像A ②画像B ③色差分ヒートマップ (変化箇所を赤でハイライト) ④左右並列の合成画像 ⑤Gemini Vision による日本語の差分レポート (文字・ロゴ・レイアウト・色変更を記述)。PDFが入力された場合は1ページ目をラスタライズして比較します。',
          )}
        </Text>
      </Alert>
      <Text size="xs" c="dimmed">
        {t(
          'imageCompare.settings.usage',
          '使い方: 左側に「オリジナル画像」と「変更後画像」を順に選択して、実行ボタンを押してください。',
        )}
      </Text>
    </Stack>
  );
};

export default ImageCompareSettings;
