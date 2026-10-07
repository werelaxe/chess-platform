import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { api } from "../api/client";
import type { BotLevel, ColorChoice, GameKind, Opponent, Visibility } from "../api/types";
import { BOT_LEVELS, BOT_LEVEL_HINTS, BOT_LEVEL_LABELS, createGameRequest } from "../core/computer";
import { useAuthStore } from "../store/auth";
import { Choice, ErrorNotice } from "./ui";

export function CreateGameForm() {
  const { user, setSession } = useAuthStore();
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
        setSession(session.token, session.user);
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
      <h2 className="card__title">New game</h2>
      <p className="card__subtitle">
        {kind === "QUANTUM"
          ? "Split pieces across universes and observe to collapse them."
          : "Standard FIDE rules with castling, en passant and promotion."}
      </p>
      <div className="field">
        <span className="field__label">Variant</span>
        <Choice
          name="Variant"
          value={kind}
          onChange={setKind}
          disabled={submitting}
          options={[
            { value: "CLASSIC", label: "Classic" },
            { value: "QUANTUM", label: "Quantum", quantum: true },
          ]}
        />
      </div>
      <div className="field">
        <span className="field__label">Opponent</span>
        <Choice
          name="Opponent"
          value={opponent}
          onChange={setOpponent}
          disabled={submitting}
          options={[
            { value: "HUMAN", label: "Human" },
            { value: "COMPUTER", label: "Computer" },
          ]}
        />
      </div>
      {computer ? (
        <div className="field">
          <span className="field__label">Level</span>
          <Choice
            name="Level"
            value={level}
            onChange={setLevel}
            disabled={submitting}
            options={BOT_LEVELS.map((value) => ({ value, label: BOT_LEVEL_LABELS[value] }))}
          />
          <span className="field__hint">{BOT_LEVEL_HINTS[level]}</span>
        </div>
      ) : null}
      <div className="field">
        <span className="field__label">Your color</span>
        <Choice
          name="Color"
          value={color}
          onChange={setColor}
          disabled={submitting}
          options={[
            { value: "WHITE", label: "White" },
            { value: "RANDOM", label: "Random" },
            { value: "BLACK", label: "Black" },
          ]}
        />
      </div>
      {computer ? (
        <div className="field">
          <span className="field__hint">Computer games are unlisted: only people with the link can watch.</span>
        </div>
      ) : (
        <div className="field">
          <span className="field__label">Visibility</span>
          <Choice
            name="Visibility"
            value={visibility}
            onChange={setVisibility}
            disabled={submitting}
            options={[
              { value: "PUBLIC", label: "Public" },
              { value: "PRIVATE", label: "Private" },
            ]}
          />
          <span className="field__hint">
            {visibility === "PUBLIC" ? "Listed in the lobby for anyone to join." : "Only people with the link can join or watch."}
          </span>
        </div>
      )}
      {error ? <ErrorNotice error={error} /> : null}
      {user ? (
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? "Creating" : "Create game"}
        </button>
      ) : (
        <div className="stack">
          <Link to="/login" className="btn btn--primary btn--block">
            Log in
          </Link>
          <button type="button" className="btn btn--block" disabled={submitting} onClick={() => void create(true)}>
            {submitting ? "Creating" : "Continue as guest"}
          </button>
          <p className="faint small">
            No account yet? <Link to="/register">Register</Link>, or continue as a guest to create this game right away.
          </p>
        </div>
      )}
    </form>
  );
}
