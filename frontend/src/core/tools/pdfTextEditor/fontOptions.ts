/**
 * User-selectable display fonts for the PDF Text Editor. When a font in the document
 * cannot be reproduced exactly, the user can pick which system / web font is used to
 * preview / render that PDF font in the editor.
 *
 * The override is keyed by the normalized base font name (subset prefixes stripped,
 * spaces / dashes / underscores removed, lowercased) so a single choice applies to
 * every variant that shares the same logical face (e.g. `ABCDEF+MS-Gothic` ≡
 * `MSGothic`).
 */

export interface FontOption {
  /** CSS `font-family` string applied directly to text rendering. */
  value: string;
  /** Display label shown in the dropdown. */
  label: string;
  /** Server-side fallback font ID used when writing the PDF output. */
  serverFontId?: string;
}

/**
 * Default option used when no override is set — falls through to the editor's
 * built-in matching logic. Empty string is the sentinel for "auto".
 */
export const AUTO_FONT_OPTION: FontOption = {
  value: '',
  label: 'Auto (default fallback)',
};

export const FONT_OPTIONS: FontOption[] = [
  AUTO_FONT_OPTION,
  // Japanese — Noto family
  { value: '"Noto Sans JP", "Noto Sans CJK JP", sans-serif', label: 'Noto Sans JP', serverFontId: 'fallback-noto-jp' },
  { value: '"Noto Serif JP", "Noto Serif CJK JP", serif', label: 'Noto Serif JP', serverFontId: 'fallback-noto-jp' },
  // Japanese — Yu / Hiragino / MS (display only; map to Noto JP in PDF output)
  { value: '"Yu Gothic", "游ゴシック", sans-serif', label: 'Yu Gothic / 游ゴシック', serverFontId: 'fallback-noto-jp' },
  { value: '"Yu Mincho", "游明朝", serif', label: 'Yu Mincho / 游明朝', serverFontId: 'fallback-noto-jp' },
  { value: '"Hiragino Sans", "ヒラギノ角ゴシック", sans-serif', label: 'Hiragino Sans / ヒラギノ角ゴシック', serverFontId: 'fallback-noto-jp' },
  {
    value: '"Hiragino Mincho ProN", "ヒラギノ明朝 ProN", serif',
    label: 'Hiragino Mincho / ヒラギノ明朝',
    serverFontId: 'fallback-noto-jp',
  },
  { value: '"MS Gothic", "ＭＳ ゴシック", sans-serif', label: 'MS Gothic / ＭＳゴシック', serverFontId: 'fallback-noto-jp' },
  { value: '"MS Mincho", "ＭＳ 明朝", serif', label: 'MS Mincho / ＭＳ明朝', serverFontId: 'fallback-noto-jp' },
  // Latin core
  { value: 'Arial, Helvetica, sans-serif', label: 'Arial / Helvetica', serverFontId: 'fallback-liberation-sans' },
  { value: '"Times New Roman", Times, serif', label: 'Times New Roman', serverFontId: 'fallback-liberation-serif' },
  { value: '"Courier New", Courier, monospace', label: 'Courier New', serverFontId: 'fallback-liberation-mono' },
  // Liberation
  { value: '"Liberation Sans", sans-serif', label: 'Liberation Sans', serverFontId: 'fallback-liberation-sans' },
  { value: '"Liberation Serif", serif', label: 'Liberation Serif', serverFontId: 'fallback-liberation-serif' },
  { value: '"Liberation Mono", monospace', label: 'Liberation Mono', serverFontId: 'fallback-liberation-mono' },
  // DejaVu
  { value: '"DejaVu Sans", sans-serif', label: 'DejaVu Sans', serverFontId: 'fallback-dejavu-sans' },
  { value: '"DejaVu Serif", serif', label: 'DejaVu Serif', serverFontId: 'fallback-dejavu-serif' },
];

/**
 * Normalize a PDF font's base name into a stable key for the override map.
 * Strips the 6-letter subset prefix (e.g. `ABCDEF+`) and removes whitespace,
 * dashes and underscores, lowercased.
 */
export const buildFontOverrideKey = (baseName: string | null | undefined): string => {
  if (!baseName) return '';
  return baseName
    .replace(/^[A-Z]{6}\+/u, '')
    .toLowerCase()
    .replace(/[-_\s]/gu, '');
};
