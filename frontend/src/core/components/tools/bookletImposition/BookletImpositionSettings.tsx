import { useEffect, useState, useRef, useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Stack, Text, Divider, Collapse, Button, NumberInput, Checkbox, Group, Select, TextInput, FileButton, Badge, Alert } from "@mantine/core";
import {
  BookletImpositionParameters,
  SignaturePagesPerSheet,
  CropMarkType,
} from "@app/hooks/tools/bookletImposition/useBookletImpositionParameters";
import ButtonSelector from "@app/components/shared/ButtonSelector";
import apiClient from "@app/services/apiClient";

interface BookletImpositionSettingsProps {
  parameters: BookletImpositionParameters;
  onParameterChange: (key: keyof BookletImpositionParameters, value: any) => void;
  disabled?: boolean;
}

interface FacilisLayoutSummary {
  id: string;
  name: string;
  category: string;
  pagesH: number;
  pagesV: number;
  finishWidthPt: number;
  finishHeightPt: number;
  plateWidthPt: number;
  plateHeightPt: number;
  bindSide: string;
}

const BookletImpositionSettings = ({ parameters, onParameterChange, disabled = false }: BookletImpositionSettingsProps) => {
  const { t } = useTranslation();
  const [advancedOpen, setAdvancedOpen] = useState(false);

  // ── FACILIS DB state ──
  const [layouts, setLayouts] = useState<FacilisLayoutSummary[]>([]);
  const [layoutsLoaded, setLayoutsLoaded] = useState<boolean>(false);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileResetRef = useRef<() => void>(null);

  const fetchLayouts = useCallback(async () => {
    try {
      const { data } = await apiClient.get<FacilisLayoutSummary[]>(
        "/api/v1/general/facilis-db/layouts",
      );
      setLayouts(Array.isArray(data) ? data : []);
      setLayoutsLoaded(true);
    } catch (err: any) {
      // 401 = not logged in: leave empty silently. Other errors: show in alert area.
      if (err?.response?.status !== 401) {
        setUploadError(err?.message ?? "Failed to load FACILIS layouts");
      }
      setLayoutsLoaded(false);
    }
  }, []);

  useEffect(() => {
    void fetchLayouts();
  }, [fetchLayouts]);

  const onUpload = useCallback(
    async (file: File | null) => {
      if (!file) return;
      setUploading(true);
      setUploadError(null);
      try {
        const form = new FormData();
        form.append("fileInput", file);
        const { data } = await apiClient.post<{ indexedLayouts?: number }>(
          "/api/v1/general/facilis-db/upload",
          form,
        );
        await fetchLayouts();
        if (typeof data?.indexedLayouts === "number" && data.indexedLayouts === 0) {
          setUploadError(
            t(
              "bookletImposition.facilis.noLayoutsFound",
              "Zip imported but no .lay layouts were found.",
            ) as string,
          );
        }
      } catch (err: any) {
        setUploadError(
          err?.response?.data?.error ??
            err?.message ??
            (t("bookletImposition.facilis.uploadFailed", "Upload failed") as string),
        );
      } finally {
        setUploading(false);
        fileResetRef.current?.();
      }
    },
    [fetchLayouts, t],
  );

  // Group layouts by category for the dropdown.
  const layoutOptions = useMemo(() => {
    return layouts.map((l) => ({
      value: l.id,
      label: l.category ? `${l.category} / ${l.name}` : l.name,
    }));
  }, [layouts]);

  const usingTemplate = Boolean(parameters.templateId);

  return (
    <Stack gap="md">
      <Divider ml='-md'></Divider>


      {/* Double Sided */}
      <Stack gap="sm">
        <Checkbox
          checked={parameters.doubleSided}
          onChange={(event) => {
            const isDoubleSided = event.currentTarget.checked;
            onParameterChange('doubleSided', isDoubleSided);
            // Reset to BOTH when turning double-sided back on
            if (isDoubleSided) {
              onParameterChange('duplexPass', 'BOTH');
            } else {
              // Default to FIRST pass when going to manual duplex
              onParameterChange('duplexPass', 'FIRST');
            }
          }}
          disabled={disabled}
          label={
            <div>
              <Text size="sm">{t('bookletImposition.doubleSided.label', 'Double-sided printing')}</Text>
              <Text size="xs" c="dimmed">{t('bookletImposition.doubleSided.tooltip', 'Creates both front and back sides for proper booklet printing')}</Text>
            </div>
          }
        />

        {/* Manual Duplex Pass Selection - only show when double-sided is OFF */}
        {!parameters.doubleSided && (
          <Stack gap="xs" ml="lg">
            <Text size="sm" fw={500} c="orange">
              {t('bookletImposition.manualDuplex.title', 'Manual Duplex Mode')}
            </Text>
            <Text size="xs" c="dimmed">
              {t('bookletImposition.manualDuplex.instructions', 'For printers without automatic duplex. You\'ll need to run this twice:')}
            </Text>

            <ButtonSelector
              label={t('bookletImposition.duplexPass.label', 'Print Pass')}
              value={parameters.duplexPass}
              onChange={(value) => onParameterChange('duplexPass', value)}
              options={[
                { value: 'FIRST', label: t('bookletImposition.duplexPass.first', '1st Pass') },
                { value: 'SECOND', label: t('bookletImposition.duplexPass.second', '2nd Pass') }
              ]}
              disabled={disabled}
            />

            <Text size="xs" c="blue" fs="italic">
              {parameters.duplexPass === 'FIRST'
                ? t('bookletImposition.duplexPass.firstInstructions', 'Prints front sides → stack face-down → run again with 2nd Pass')
                : t('bookletImposition.duplexPass.secondInstructions', 'Load printed stack face-down → prints back sides')
              }
            </Text>
          </Stack>
        )}
      </Stack>

      <Divider />

      {/* Advanced Options */}
      <Stack gap="sm">
        <Button
          variant="subtle"
          onClick={() => setAdvancedOpen(!advancedOpen)}
          disabled={disabled}
        >
          {t('bookletImposition.advanced.toggle', 'Advanced Options')} {advancedOpen ? '▲' : '▼'}
        </Button>

        <Collapse in={advancedOpen}>
          <Stack gap="md" mt="md">
            <Divider
              label={t('bookletImposition.section.facilis', 'FACILIS テンプレート (任意)')}
              labelPosition="center"
            />

            {/* FACILIS DB upload + template selector */}
            <Stack gap="xs">
              <Group gap="xs" align="center">
                <FileButton
                  resetRef={fileResetRef}
                  onChange={onUpload}
                  accept="application/zip,.zip"
                  disabled={disabled || uploading}
                >
                  {(props) => (
                    <Button {...props} size="xs" variant="light" loading={uploading}>
                      {t('bookletImposition.facilis.upload', 'DB.zip をアップロード')}
                    </Button>
                  )}
                </FileButton>
                {layoutsLoaded && (
                  <Badge color="green" variant="light">
                    {t('bookletImposition.facilis.indexed', '{{count}} テンプレ', {
                      count: layouts.length,
                    })}
                  </Badge>
                )}
                {!layoutsLoaded && layouts.length === 0 && !uploading && (
                  <Text size="xs" c="dimmed">
                    {t(
                      'bookletImposition.facilis.notLoaded',
                      'まだ DB が読み込まれていません',
                    )}
                  </Text>
                )}
                {parameters.templateId && (
                  <Button
                    size="xs"
                    variant="subtle"
                    color="red"
                    onClick={() => onParameterChange('templateId', null)}
                    disabled={disabled}
                  >
                    {t('bookletImposition.facilis.clear', 'テンプレ解除')}
                  </Button>
                )}
              </Group>

              {uploadError && (
                <Alert color="red" variant="light" p="xs">
                  <Text size="xs">{uploadError}</Text>
                </Alert>
              )}

              {layouts.length > 0 && (
                <Select
                  label={t('bookletImposition.facilis.selectLabel', 'テンプレートを選択')}
                  description={t(
                    'bookletImposition.facilis.selectDescription',
                    '選択時は手動設定 (折丁/ドブ/咥え/トンボ等) を全て無視して .lay の指示通りに面付けします',
                  )}
                  placeholder={t('bookletImposition.facilis.selectPlaceholder', '未選択 (手動設定を使用)')}
                  data={layoutOptions}
                  value={parameters.templateId ?? null}
                  onChange={(v) => onParameterChange('templateId', v)}
                  searchable
                  clearable
                  size="sm"
                  disabled={disabled}
                  comboboxProps={{ withinPortal: true }}
                  maxDropdownHeight={320}
                />
              )}

              {usingTemplate && (
                <Alert color="blue" variant="light" p="xs">
                  <Text size="xs">
                    {t(
                      'bookletImposition.facilis.activeNotice',
                      '⚠ テンプレ選択中: 以下の手動項目は無視されます',
                    )}
                  </Text>
                </Alert>
              )}
            </Stack>

            <Divider />

            {/* Right-to-Left Binding */}
            <Checkbox
              checked={parameters.spineLocation === 'RIGHT'}
              onChange={(event) => onParameterChange('spineLocation', event.currentTarget.checked ? 'RIGHT' : 'LEFT')}
              disabled={disabled || usingTemplate}
              label={
                <div>
                  <Text size="sm">{t('bookletImposition.rtlBinding.label', 'Right-to-left binding')}</Text>
                  <Text size="xs" c="dimmed">{t('bookletImposition.rtlBinding.tooltip', 'For Arabic, Hebrew, or other right-to-left languages')}</Text>
                </div>
              }
            />

            {/* Add Border Option */}
            <Checkbox
              checked={parameters.addBorder}
              onChange={(event) => onParameterChange('addBorder', event.currentTarget.checked)}
              disabled={disabled || usingTemplate}
              label={
                <div>
                  <Text size="sm">{t('bookletImposition.addBorder.label', 'Add borders around pages')}</Text>
                  <Text size="xs" c="dimmed">{t('bookletImposition.addBorder.tooltip', 'Adds borders around each page section to help with cutting and alignment')}</Text>
                </div>
              }
            />

            {/* Gutter Margin */}
            <Stack gap="xs">
              <Checkbox
                checked={parameters.addGutter}
                onChange={(event) => onParameterChange('addGutter', event.currentTarget.checked)}
                disabled={disabled || usingTemplate}
                label={
                  <div>
                    <Text size="sm">{t('bookletImposition.addGutter.label', 'Add gutter margin')}</Text>
                    <Text size="xs" c="dimmed">{t('bookletImposition.addGutter.tooltip', 'Adds inner margin space for binding')}</Text>
                  </div>
                }
              />

              {parameters.addGutter && (
                <NumberInput
                  label={t('bookletImposition.gutterSize.label', 'Gutter size (points)')}
                  value={parameters.gutterSize}
                  onChange={(value) => onParameterChange('gutterSize', value || 12)}
                  min={6}
                  max={72}
                  step={6}
                  disabled={disabled || usingTemplate}
                  size="sm"
                />
              )}
            </Stack>

            {/* Flip on Short Edge */}
            <Checkbox
              checked={parameters.flipOnShortEdge}
              onChange={(event) => onParameterChange('flipOnShortEdge', event.currentTarget.checked)}
              disabled={disabled || usingTemplate || !parameters.doubleSided}
              label={
                <div>
                  <Text size="sm" c={!parameters.doubleSided ? "dimmed" : undefined}>
                    {t('bookletImposition.flipOnShortEdge.label', 'Flip on short edge')}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {!parameters.doubleSided
                      ? t('bookletImposition.flipOnShortEdge.manualNote', 'Not needed in manual mode - you flip the stack yourself')
                      : t('bookletImposition.flipOnShortEdge.tooltip', 'Enable for short-edge duplex printing (automatic duplex only - ignored in manual mode)')
                    }
                  </Text>
                </div>
              }
            />

            <Divider label={t('bookletImposition.section.signature', '折丁 (Signature)')} labelPosition="center" />

            {/* 折丁 — pages per press sheet */}
            <Select
              label={t('bookletImposition.pagesPerSheet.label', '面付け数 (pages per sheet)')}
              description={t(
                'bookletImposition.pagesPerSheet.description',
                '折丁あたりのページ数。中綴じ=2、無線/アジロは8/16/32が一般的',
              )}
              value={String(parameters.pagesPerSheet)}
              onChange={(value) => {
                const next = Number(value) as SignaturePagesPerSheet;
                onParameterChange('pagesPerSheet', next);
              }}
              data={[
                { value: '2', label: '2-up (中綴じ)' },
                { value: '4', label: '4-up' },
                { value: '8', label: '8-up' },
                { value: '16', label: '16-up' },
                { value: '32', label: '32-up' },
              ]}
              disabled={disabled || usingTemplate}
              size="sm"
            />

            <Divider label={t('bookletImposition.section.bleed', 'ドブ (Bleed)')} labelPosition="center" />

            {/* 4辺独立ドブ */}
            <Checkbox
              checked={parameters.bleedIndependent}
              onChange={(event) => onParameterChange('bleedIndependent', event.currentTarget.checked)}
              disabled={disabled || usingTemplate}
              label={
                <div>
                  <Text size="sm">{t('bookletImposition.bleed.independent.label', '天地左右で独立したドブを指定')}</Text>
                  <Text size="xs" c="dimmed">{t('bookletImposition.bleed.independent.tooltip', 'OFF: 全辺一律 / ON: 4辺独立 (mm)')}</Text>
                </div>
              }
            />

            {parameters.bleedIndependent && (
              <Group grow>
                <NumberInput
                  label={t('bookletImposition.bleed.top', '天 (mm)')}
                  value={parameters.bleedTopMm}
                  onChange={(v) => onParameterChange('bleedTopMm', Number(v) || 0)}
                  min={0}
                  max={50}
                  step={0.5}
                  size="xs"
                  disabled={disabled || usingTemplate}
                />
                <NumberInput
                  label={t('bookletImposition.bleed.bottom', '地 (mm)')}
                  value={parameters.bleedBottomMm}
                  onChange={(v) => onParameterChange('bleedBottomMm', Number(v) || 0)}
                  min={0}
                  max={50}
                  step={0.5}
                  size="xs"
                  disabled={disabled || usingTemplate}
                />
                <NumberInput
                  label={t('bookletImposition.bleed.inside', 'ノド (mm)')}
                  value={parameters.bleedInsideMm}
                  onChange={(v) => onParameterChange('bleedInsideMm', Number(v) || 0)}
                  min={0}
                  max={50}
                  step={0.5}
                  size="xs"
                  disabled={disabled || usingTemplate}
                />
                <NumberInput
                  label={t('bookletImposition.bleed.outside', '小口 (mm)')}
                  value={parameters.bleedOutsideMm}
                  onChange={(v) => onParameterChange('bleedOutsideMm', Number(v) || 0)}
                  min={0}
                  max={50}
                  step={0.5}
                  size="xs"
                  disabled={disabled || usingTemplate}
                />
              </Group>
            )}

            <Divider label={t('bookletImposition.section.gripper', '咥え (Gripper)')} labelPosition="center" />

            {/* 咥え (gripper edge) */}
            <Checkbox
              checked={parameters.gripperEnabled}
              onChange={(event) => onParameterChange('gripperEnabled', event.currentTarget.checked)}
              disabled={disabled || usingTemplate}
              label={
                <div>
                  <Text size="sm">{t('bookletImposition.gripper.label', '咥え (グリッパ余白) を確保')}</Text>
                  <Text size="xs" c="dimmed">{t('bookletImposition.gripper.tooltip', '印刷機の咥えくわえ領域。プレート天端からのオフセット')}</Text>
                </div>
              }
            />
            {parameters.gripperEnabled && (
              <NumberInput
                label={t('bookletImposition.gripper.size', '咥え寸法 (mm)')}
                value={parameters.gripperMm}
                onChange={(v) => onParameterChange('gripperMm', Number(v) || 0)}
                min={0}
                max={30}
                step={0.5}
                size="sm"
                disabled={disabled || usingTemplate}
              />
            )}

            <Divider label={t('bookletImposition.section.spine', '背丁 (Spine signature)')} labelPosition="center" />

            {/* 背丁 (spine signature) */}
            <Checkbox
              checked={parameters.spineSignatureEnabled}
              onChange={(event) => onParameterChange('spineSignatureEnabled', event.currentTarget.checked)}
              disabled={disabled || usingTemplate}
              label={
                <div>
                  <Text size="sm">{t('bookletImposition.spine.label', '背丁テキストを背に印字')}</Text>
                  <Text size="xs" c="dimmed">{t('bookletImposition.spine.tooltip', '丁合確認用。%JobName% / %SignatureNo% / %ColorName% などのプレースホルダ可')}</Text>
                </div>
              }
            />
            {parameters.spineSignatureEnabled && (
              <TextInput
                label={t('bookletImposition.spine.text', '背丁テキスト')}
                value={parameters.spineSignatureText}
                onChange={(e) => onParameterChange('spineSignatureText', e.currentTarget.value)}
                placeholder="%JobName% %SignatureNo%"
                size="sm"
                disabled={disabled || usingTemplate}
              />
            )}

            <Divider label={t('bookletImposition.section.marks', 'トンボ／見当 (Marks)')} labelPosition="center" />

            {/* トンボ／見当 */}
            <Select
              label={t('bookletImposition.cropMarks.label', 'トンボ種類')}
              description={t(
                'bookletImposition.cropMarks.description',
                'なし / センター / 裁ち落とし / 折丁 / 矩形',
              )}
              value={parameters.cropMarkType}
              onChange={(value) => onParameterChange('cropMarkType', (value as CropMarkType) ?? 'NONE')}
              data={[
                { value: 'NONE', label: t('bookletImposition.cropMarks.none', 'なし') },
                { value: 'CENTER', label: t('bookletImposition.cropMarks.center', 'センタートンボ') },
                { value: 'CUTTING', label: t('bookletImposition.cropMarks.cutting', '裁ち落としトンボ') },
                { value: 'FOLDING', label: t('bookletImposition.cropMarks.folding', '折丁トンボ') },
                { value: 'RECT', label: t('bookletImposition.cropMarks.rect', '矩形トンボ') },
              ]}
              disabled={disabled || usingTemplate}
              size="sm"
            />
            <Checkbox
              checked={parameters.registrationMarks}
              onChange={(event) => onParameterChange('registrationMarks', event.currentTarget.checked)}
              disabled={disabled || usingTemplate}
              label={
                <div>
                  <Text size="sm">{t('bookletImposition.registrationMarks.label', '見当マーク (registration) を追加')}</Text>
                  <Text size="xs" c="dimmed">{t('bookletImposition.registrationMarks.tooltip', '版ずれ確認用の十字マークを4辺に配置')}</Text>
                </div>
              }
            />

            {/* Paper Size Note */}
            <Text size="xs" c="dimmed" fs="italic">
              {t('bookletImposition.paperSizeNote', 'Paper size is automatically derived from your first page.')}
            </Text>
          </Stack>
        </Collapse>
      </Stack>
    </Stack>
  );
};

export default BookletImpositionSettings;
