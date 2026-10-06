import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { api } from "../api/client";
import type { ColorChoice, GameKind, Visibility } from "../api/types";
import { useAuthStore } from "../store/auth";
import { Choice, ErrorNotice } from "./ui";

export function CreateGameForm() {
  const user = useAuthStore((state) => state.user);
  const navigate = useNavigate();
  const [kind, setKind] = useState<GameKind>("QUANTUM");
  const [visibility, setVisibility] = useState<Visibility>("PUBLIC");
  const [color, setColor] = useState<ColorChoice>("RANDOM");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const game = await api.createGame({ kind, visibility, color });
      navigate(`/games/${game.id}`);
    } catch (cause) {
      setError(cause);
      setSubmitting(false);
    }
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
      {error ? <ErrorNotice error={error} /> : null}
      {user ? (
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? "Creating" : "Create game"}
        </button>
      ) : (
        <p className="muted small">
          <Link to="/login">Log in</Link> or <Link to="/register">register</Link> to create a game.
        </p>
      )}
    </form>
  );
}
