import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Stack, Text, Divider, Collapse, Button, NumberInput, Checkbox, Select } from "@mantine/core";
import {
  AiImpositionParameters,
  SignaturePages,
} from "@app/hooks/tools/aiImposition/useAiImpositionParameters";

interface AiImpositionSettingsProps {
  parameters: AiImpositionParameters;
  onParameterChange: (key: keyof AiImpositionParameters, value: any) => void;
  disabled?: boolean;
}

const AiImpositionSettings = ({
  parameters,
  onParameterChange,
  disabled = false,
}: AiImpositionSettingsProps) => {
  const { t } = useTranslation();
  const [advancedOpen, setAdvancedOpen] = useState(false);

  return (
    <Stack gap="md">
      <Divider ml="-md" />

      {/* Signature size — the primary setting that distinguishes this tool from Booklet Imposition */}
      <Stack gap="sm">
        <Select
          label={t("aiImposition.signaturePages.label", "Pages per signature (折ページ数)")}
          description={t(
            "aiImposition.signaturePages.description",
            "How many pages fold into one signature. Larger = thicker signature, fewer to bind. 16 is the most common for book printing.",
          )}
          value={parameters.signaturePages.toString()}
          onChange={(value) =>
            onParameterChange("signaturePages", parseInt(value || "16", 10) as SignaturePages)
          }
          data={[
            { value: "4", label: t("aiImposition.signaturePages.4", "4 pages (1 sheet folded once)") },
            { value: "8", label: t("aiImposition.signaturePages.8", "8 pages (2 sheets / 4面折)") },
            { value: "16", label: t("aiImposition.signaturePages.16", "16 pages (4 sheets / 8面折) — recommended") },
            { value: "32", label: t("aiImposition.signaturePages.32", "32 pages (8 sheets / 16面折)") },
          ]}
          disabled={disabled}
        />
      </Stack>

      <Divider />

      {/* Double Sided */}
      <Stack gap="sm">
        <Checkbox
          checked={parameters.doubleSided}
          onChange={(event) => {
            const isDoubleSided = event.currentTarget.checked;
            onParameterChange("doubleSided", isDoubleSided);
            if (isDoubleSided) {
              onParameterChange("duplexPass", "BOTH");
            } else {
              onParameterChange("duplexPass", "FIRST");
            }
          }}
          disabled={disabled}
          label={
            <div>
              <Text size="sm">
                {t("aiImposition.doubleSided.label", "Double-sided printing")}
              </Text>
              <Text size="xs" c="dimmed">
                {t(
                  "aiImposition.doubleSided.tooltip",
                  "Creates both front and back sides for proper signature printing",
                )}
              </Text>
            </div>
          }
        />

        {!parameters.doubleSided && (
          <Stack gap="xs" ml="lg">
            <Text size="sm" fw={500} c="orange">
              {t("aiImposition.manualDuplex.title", "Manual Duplex Mode")}
            </Text>
            <Text size="xs" c="dimmed">
              {t(
                "aiImposition.manualDuplex.instructions",
                "For printers without automatic duplex. Run twice: 1st pass, then re-feed and 2nd pass.",
              )}
            </Text>
            <Select
              label={t("aiImposition.duplexPass.label", "Print Pass")}
              value={parameters.duplexPass}
              onChange={(value) => onParameterChange("duplexPass", value || "FIRST")}
              data={[
                { value: "FIRST", label: t("aiImposition.duplexPass.first", "1st Pass") },
                { value: "SECOND", label: t("aiImposition.duplexPass.second", "2nd Pass") },
              ]}
              disabled={disabled}
              size="sm"
            />
          </Stack>
        )}
      </Stack>

      <Divider />

      {/* Advanced */}
      <Stack gap="sm">
        <Button variant="subtle" onClick={() => setAdvancedOpen(!advancedOpen)} disabled={disabled}>
          {t("aiImposition.advanced.toggle", "Advanced Options")} {advancedOpen ? "▲" : "▼"}
        </Button>

        <Collapse in={advancedOpen}>
          <Stack gap="md" mt="md">
            <Checkbox
              checked={parameters.spineLocation === "RIGHT"}
              onChange={(event) =>
                onParameterChange("spineLocation", event.currentTarget.checked ? "RIGHT" : "LEFT")
              }
              disabled={disabled}
              label={
                <div>
                  <Text size="sm">
                    {t("aiImposition.rtlBinding.label", "Right-to-left binding (右綴じ)")}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {t(
                      "aiImposition.rtlBinding.tooltip",
                      "For Japanese books in tategaki (vertical writing) or other RTL languages",
                    )}
                  </Text>
                </div>
              }
            />

            <Checkbox
              checked={parameters.addBorder}
              onChange={(event) => onParameterChange("addBorder", event.currentTarget.checked)}
              disabled={disabled}
              label={
                <div>
                  <Text size="sm">{t("aiImposition.addBorder.label", "Add borders around pages")}</Text>
                  <Text size="xs" c="dimmed">
                    {t("aiImposition.addBorder.tooltip", "Helps with cutting and alignment")}
                  </Text>
                </div>
              }
            />

            <Stack gap="xs">
              <Checkbox
                checked={parameters.addGutter}
                onChange={(event) => onParameterChange("addGutter", event.currentTarget.checked)}
                disabled={disabled}
                label={
                  <div>
                    <Text size="sm">{t("aiImposition.addGutter.label", "Add gutter margin")}</Text>
                    <Text size="xs" c="dimmed">
                      {t("aiImposition.addGutter.tooltip", "Inner margin for binding")}
                    </Text>
                  </div>
                }
              />
              {parameters.addGutter && (
                <NumberInput
                  label={t("aiImposition.gutterSize.label", "Gutter size (points)")}
                  value={parameters.gutterSize}
                  onChange={(value) => onParameterChange("gutterSize", value || 12)}
                  min={6}
                  max={72}
                  step={6}
                  disabled={disabled}
                  size="sm"
                />
              )}
            </Stack>

            <Checkbox
              checked={parameters.flipOnShortEdge}
              onChange={(event) => onParameterChange("flipOnShortEdge", event.currentTarget.checked)}
              disabled={disabled || !parameters.doubleSided}
              label={
                <div>
                  <Text size="sm" c={!parameters.doubleSided ? "dimmed" : undefined}>
                    {t("aiImposition.flipOnShortEdge.label", "Flip on short edge")}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {!parameters.doubleSided
                      ? t(
                          "aiImposition.flipOnShortEdge.manualNote",
                          "Not needed in manual mode - you flip the stack yourself",
                        )
                      : t(
                          "aiImposition.flipOnShortEdge.tooltip",
                          "Enable for short-edge duplex printing (automatic duplex only)",
                        )}
                  </Text>
                </div>
              }
            />

            <Text size="xs" c="dimmed" fs="italic">
              {t(
                "aiImposition.paperSizeNote",
                "Paper size is automatically derived from your first page (output is 2-up landscape).",
              )}
            </Text>
          </Stack>
        </Collapse>
      </Stack>
    </Stack>
  );
};

export default AiImpositionSettings;
