import { useState, type FormEvent } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { api } from "../api/client";
import type { BotLevel, ColorChoice, GameKind, Opponent, Visibility } from "../api/types";
import { BOT_LEVELS, botLevelHint, botLevelLabel, createGameRequest } from "../core/computer";
import { useAuthStore } from "../store/auth";
import { startSession } from "../store/session";
import { Choice, ErrorNotice } from "./ui";

export function CreateGameForm() {
  const { t } = useTranslation();
  const user = useAuthStore((state) => state.user);
  const navigate = useNavigate();
  const [kind, setKind] = useState<GameKind>("QUANTUM");
  const [opponent, setOpponent] = useState<Opponent>("HUMAN");
  const [level, setLevel] = useState<BotLevel>("MEDIUM");
  const [visibility, setVisibility] = useState<Visibility>("PUBLIC");
  const [color, setColor] = useState<ColorChoice>("RANDOM");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const computer = opponent === "COMPUTER";

  async function create(asGuest: boolean) {
    setSubmitting(true);
    setError(null);
    try {
      if (asGuest) {
        const session = await api.guest();
        startSession(session.token, session.user);
      }
      const game = await api.createGame(createGameRequest({ kind, visibility, color, opponent, level }));
      navigate(`/games/${game.id}`);
    } catch (cause) {
      setError(cause);
      setSubmitting(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void create(false);
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2 className="card__title">{t("create.title")}</h2>
      <p className="card__subtitle">{t(`create.subtitle.${kind}`)}</p>
      <div className="field">
        <span className="field__label">{t("create.variant")}</span>
        <Choice
          name={t("create.variant")}
          value={kind}
          onChange={setKind}
          disabled={submitting}
          options={[
            { value: "CLASSIC", label: t("kinds.CLASSIC") },
            { value: "QUANTUM", label: t("kinds.QUANTUM"), quantum: true },
          ]}
        />
      </div>
      <div className="field">
        <span className="field__label">{t("create.opponent")}</span>
        <Choice
          name={t("create.opponent")}
          value={opponent}
          onChange={setOpponent}
          disabled={submitting}
          options={[
            { value: "HUMAN", label: t("create.human") },
            { value: "COMPUTER", label: t("create.computer") },
          ]}
        />
      </div>
      {computer ? (
        <div className="field">
          <span className="field__label">{t("create.level")}</span>
          <Choice
            name={t("create.level")}
            value={level}
            onChange={setLevel}
            disabled={submitting}
            options={BOT_LEVELS.map((value) => ({ value, label: botLevelLabel(value) }))}
          />
          <span className="field__hint">{botLevelHint(level)}</span>
        </div>
      ) : null}
      <div className="field">
        <span className="field__label">{t("create.yourColor")}</span>
        <Choice
          name={t("create.color")}
          value={color}
          onChange={setColor}
          disabled={submitting}
          options={[
            { value: "WHITE", label: t("create.white") },
            { value: "RANDOM", label: t("create.random") },
            { value: "BLACK", label: t("create.black") },
          ]}
        />
      </div>
      {computer ? (
        <div className="field">
          <span className="field__hint">{t("create.computerUnlisted")}</span>
        </div>
      ) : (
        <div className="field">
          <span className="field__label">{t("create.visibility")}</span>
          <Choice
            name={t("create.visibility")}
            value={visibility}
            onChange={setVisibility}
            disabled={submitting}
            options={[
              { value: "PUBLIC", label: t("create.public") },
              { value: "PRIVATE", label: t("create.private") },
            ]}
          />
          <span className="field__hint">{visibility === "PUBLIC" ? t("create.publicHint") : t("create.privateHint")}</span>
        </div>
      )}
      {error ? <ErrorNotice error={error} /> : null}
      {user ? (
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? t("create.creating") : t("create.submit")}
        </button>
      ) : (
        <div className="stack">
          <Link to="/login" className="btn btn--primary btn--block">
            {t("create.logIn")}
          </Link>
          <button type="button" className="btn btn--block" disabled={submitting} onClick={() => void create(true)}>
            {submitting ? t("create.creating") : t("create.continueAsGuest")}
          </button>
          <p className="faint small">
            <Trans i18nKey="create.noAccount" components={{ register: <Link to="/register" /> }} />
          </p>
        </div>
      )}
    </form>
  );
}
