import type { BoardMode } from "./Board";

const MODES: { value: BoardMode; label: string; className: string }[] = [
  { value: "normal", label: "Move", className: "modebar__btn modebar__btn--normal" },
  { value: "split", label: "Split", className: "modebar__btn" },
  { value: "observe", label: "Observe", className: "modebar__btn" },
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
  return (
    <section className="panel">
      <div className="panel__title">Quantum move</div>
      <div className="modebar" role="radiogroup" aria-label="Move type">
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
            {entry.label}
          </button>
        ))}
      </div>
      <div className="modebar__hint">{hint}</div>
    </section>
  );
}
