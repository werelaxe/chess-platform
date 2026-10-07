import { Trans, useTranslation } from "react-i18next";
import { Link } from "react-router";
import { BRAND_NAME } from "../brand";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

function external(href: string) {
  return <a href={href} target="_blank" rel="noreferrer" />;
}

export function AboutPage() {
  const { t } = useTranslation();
  useDocumentTitle(t("titles.about", { brand: BRAND_NAME }));
  return (
    <div className="page page--prose prose">
      <div className="page__header reveal">
        <div className="page__eyebrow">{t("about.eyebrow")}</div>
        <h1 className="page__title">{t("about.title", { brand: BRAND_NAME })}</h1>
        <p className="page__lede">{t("about.lede")}</p>
      </div>
      <div className="reveal" style={{ "--i": 1 } as React.CSSProperties}>
        <h2>{t("about.credits")}</h2>
        <div className="credits">
          <div className="credits__item">
            <div className="credits__pieces" aria-hidden="true">
              <img src="/pieces/Chess_klt45.svg" alt="" />
              <img src="/pieces/Chess_qdt45.svg" alt="" />
              <img src="/pieces/Chess_ndt45.svg" alt="" />
              <img src="/pieces/Chess_rlt45.svg" alt="" />
            </div>
            <p>
              <Trans
                i18nKey="about.pieces"
                components={{
                  author: external("https://en.wikipedia.org/wiki/User:Cburnett"),
                  license: external("https://creativecommons.org/licenses/by-sa/3.0/"),
                  commons: external("https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces"),
                }}
              />
            </p>
          </div>
          <div className="credits__item">
            <div style={{ fontFamily: "var(--font-display)", fontSize: "1.8rem", textAlign: "center" }} aria-hidden="true">
              Aa
            </div>
            <p>{t("about.typefaces")}</p>
          </div>
        </div>
        <h2>{t("about.openSource")}</h2>
        <p>
          <Trans i18nKey="about.stack" components={{ rules: <Link to="/rules" /> }} />
        </p>
      </div>
    </div>
  );
}
