import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { BRAND_NAME } from "../brand";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

export function NotFoundPage() {
  const { t } = useTranslation();
  useDocumentTitle(t("titles.notFound", { brand: BRAND_NAME }));
  return (
    <div className="page page--narrow" style={{ textAlign: "center" }}>
      <div className="page__eyebrow">{t("notFound.eyebrow")}</div>
      <h1 className="page__title">{t("notFound.title")}</h1>
      <p className="page__lede" style={{ margin: "12px auto 24px" }}>
        {t("notFound.lede")}
      </p>
      <Link to="/" className="btn btn--primary">
        {t("common.backToLobby")}
      </Link>
    </div>
  );
}
