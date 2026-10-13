import { useCallback, useState, type FormEvent } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { api, detailedErrorMessage } from "../api/client";
import type { ColorChoice, GameKind, Visibility } from "../api/types";
import { INVITEE_ERROR_STATUSES, createGameRequest, type OpponentMode } from "../core/newGame";
import { useAuthStore } from "../store/auth";
import { startSession } from "../store/session";
import { BotPicker } from "./BotPicker";
import { PlayerSearch } from "./PlayerSearch";
import { Choice, Notice, Toggle } from "./ui";

export function CreateGameForm() {
  const { t } = useTranslation();
  const user = useAuthStore((state) => state.user);
  const navigate = useNavigate();
  const [kind, setKind] = useState<GameKind>("QUANTUM");
  const [mode, setMode] = useState<OpponentMode>("OPEN");
  const [bot, setBot] = useState<string | null>(null);
  const [playerText, setPlayerText] = useState("");
  /** The username the player field resolves to; null while it names nobody. */
  const [player, setPlayer] = useState<string | null>(null);
  const [visibility, setVisibility] = useState<Visibility>("PUBLIC");
  const [color, setColor] = useState<ColorChoice>("RANDOM");
  const [rated, setRated] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  // A visitor who is not signed in can only go on as a guest, and guests play casual games only.
  const guest = user === null || user.guest === true;
  const opponentUsername = mode === "BOT" ? bot : mode === "PLAYER" ? player : null;
  const incomplete = mode !== "OPEN" && opponentUsername === null;
  const visibilityHint =
    mode === "OPEN"
      ? visibility === "PUBLIC"
        ? t("create.publicHint")
        : t("create.privateHint")
      : visibility === "PUBLIC"
        ? t("create.publicStartHint")
        : t("create.privateStartHint");

  const pickBot = useCallback((username: string) => setBot(username), []);

  async function create(asGuest: boolean) {
    setSubmitting(true);
    setError(null);
    try {
      if (asGuest) {
        const session = await api.guest();
        startSession(session.token, session.user);
      }
      const game = await api.createGame(createGameRequest({ kind, visibility, color, rated, opponentUsername, guest: asGuest || guest }));
      navigate(`/games/${game.id}`);
    } catch (cause) {
      setError(cause);
      setSubmitting(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (incomplete) return;
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
          value={mode}
          onChange={setMode}
          disabled={submitting}
          options={[
            { value: "OPEN", label: t("create.open") },
            { value: "BOT", label: t("create.bot") },
            { value: "PLAYER", label: t("create.player") },
          ]}
        />
        {mode === "OPEN" ? <span className="field__hint">{t("create.openHint")}</span> : null}
      </div>
      {mode === "BOT" ? (
        <div className="field">
          <span className="field__label">{t("create.chooseBot")}</span>
          <BotPicker kind={kind} value={bot} onChange={pickBot} disabled={submitting} />
        </div>
      ) : null}
      {mode === "PLAYER" ? (
        <div className="field">
          <span className="field__label">{t("create.playerUsername")}</span>
          <PlayerSearch kind={kind} value={playerText} onChange={setPlayerText} onMatch={setPlayer} viewer={user} disabled={submitting} />
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
      <div className="field">
        <Toggle
          checked={rated && !guest}
          onChange={setRated}
          disabled={submitting || guest}
          label={t("create.rated")}
          hint={guest ? t("create.ratedGuestHint") : rated ? t("create.ratedHint") : t("create.casualHint")}
        />
      </div>
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
        <span className="field__hint">{visibilityHint}</span>
      </div>
      {error ? <Notice kind="error">{detailedErrorMessage(error, INVITEE_ERROR_STATUSES)}</Notice> : null}
      {user ? (
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting || incomplete}>
          {submitting ? t("create.creating") : mode === "OPEN" ? t("create.submit") : t("create.submitInvite")}
        </button>
      ) : (
        <div className="stack">
          <Link to="/login" className="btn btn--primary btn--block">
            {t("create.logIn")}
          </Link>
          <button type="button" className="btn btn--block" disabled={submitting || incomplete} onClick={() => void create(true)}>
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
