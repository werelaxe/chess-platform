import { useTranslation } from "react-i18next";
import type { BoardMode } from "./Board";

const MODES: { value: BoardMode; className: string }[] = [
  { value: "normal", className: "modebar__btn modebar__btn--normal" },
  { value: "split", className: "modebar__btn" },
  { value: "observe", className: "modebar__btn" },
];

export function ModeBar({
  mode,
  onChange,
  disabled,
  hint,
}: {
  mode: BoardMode;
  onChange: (mode: BoardMode) => void;
  disabled: boolean;
  hint: string;
}) {
  const { t } = useTranslation();
  return (
    <section className="panel">
      <div className="panel__title">{t("modes.title")}</div>
      <div className="modebar" role="radiogroup" aria-label={t("modes.label")}>
        {MODES.map((entry) => (
          <button
            key={entry.value}
            type="button"
            role="radio"
            aria-checked={mode === entry.value}
            className={`${entry.className}${mode === entry.value ? " is-active" : ""}`}
            disabled={disabled}
            onClick={() => onChange(entry.value)}
          >
            {t(`modes.${entry.value}`)}
          </button>
        ))}
      </div>
      <div className="modebar__hint">{hint}</div>
    </section>
  );
}
