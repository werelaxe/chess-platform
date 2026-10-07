import { Trans, useTranslation } from "react-i18next";
import { Link } from "react-router";
import { BRAND_NAME } from "../brand";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

const STRONG = { strong: <strong /> };

/** Player-facing rules; the text mirrors docs/RULES.md in every language. */
export function RulesPage() {
  const { t } = useTranslation();
  useDocumentTitle(t("titles.rules", { brand: BRAND_NAME }));
  return (
    <div className="page page--prose prose">
      <div className="page__header reveal">
        <div className="page__eyebrow">{t("rules.eyebrow")}</div>
        <h1 className="page__title">{t("rules.title")}</h1>
        <p className="page__lede">{t("rules.lede")}</p>
      </div>

      <div className="reveal" style={{ "--i": 1 } as React.CSSProperties}>
        <h2 id="classic">{t("rules.classic.title")}</h2>
        <p>{t("rules.classic.p1")}</p>
        <p>{t("rules.classic.p2")}</p>
      </div>

      <div className="reveal" style={{ "--i": 2 } as React.CSSProperties}>
        <h2 id="quantum">{t("rules.quantum.title")}</h2>
        <p className="lead">{t("rules.quantum.lead")}</p>

        <h3>{t("rules.quantum.moves.title")}</h3>
        <p>{t("rules.quantum.moves.intro")}</p>
        <p>
          <Trans i18nKey="rules.quantum.moves.normal" components={STRONG} />
        </p>
        <p>
          <Trans i18nKey="rules.quantum.moves.split" components={STRONG} />
        </p>
        <p>
          <Trans i18nKey="rules.quantum.moves.observe" components={STRONG} />
        </p>

        <h3>{t("rules.quantum.end.title")}</h3>
        <p>{t("rules.quantum.end.p1")}</p>
        <p>
          <Trans i18nKey="rules.quantum.end.p2" components={STRONG} />
        </p>
        <p>{t("rules.quantum.end.p3")}</p>

        <h3>{t("rules.quantum.reading.title")}</h3>
        <p>{t("rules.quantum.reading.p1")}</p>
        <div className="legend">
          <img src="/pieces/Chess_nlt45.svg" alt="" style={{ width: 48, height: 48 }} />
          <p>{t("rules.quantum.reading.legendCertain")}</p>
          <img src="/pieces/Chess_nlt45.svg" alt="" style={{ width: 48, height: 48, opacity: 0.7 }} />
          <p>{t("rules.quantum.reading.legendUncertain")}</p>
          <span
            aria-hidden="true"
            style={{ width: 48, height: 48, borderRadius: 6, border: "2px dashed var(--quantum)", display: "block" }}
          />
          <p>{t("rules.quantum.reading.legendObservable")}</p>
        </div>
      </div>

      <p className="muted reveal" style={{ "--i": 3 } as React.CSSProperties}>
        <Trans i18nKey="rules.ready" components={{ lobby: <Link to="/" /> }} />
      </p>
    </div>
  );
}
