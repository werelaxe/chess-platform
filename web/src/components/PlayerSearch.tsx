import { useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "../api/client";
import type { GameKind, PlayerSummary, UserRef } from "../api/types";
import { displayName, findByUsername, isOwnUsername } from "../core/players";
import { ratingIn } from "../core/ratings";
import { Rating, UserTag } from "./ui";

const DEBOUNCE_MS = 250;
const SUGGESTIONS = 8;

/**
 * A username field with suggestions from the player search as the user types. An invitation
 * goes to a username, so the text only resolves to a player picked from the suggestions or
 * whose username was typed in full and found by the search; `onMatch` reports that username,
 * or null while the text names nobody (the viewer included: one cannot invite oneself).
 */
export function PlayerSearch({
  kind,
  value,
  onChange,
  onMatch,
  viewer,
  disabled,
}: {
  kind: GameKind;
  value: string;
  onChange: (text: string) => void;
  /** Called whenever the username the text resolves to changes. */
  onMatch: (username: string | null) => void;
  viewer: UserRef | null;
  disabled?: boolean;
}) {
  const { t } = useTranslation();
  const listId = useId();
  /** The players of the last completed search; the text is matched against them until the next one. */
  const [found, setFound] = useState<PlayerSummary[]>([]);
  const [picked, setPicked] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const query = value.trim();
  const self = isOwnUsername(query, viewer);
  const match = self ? null : (picked ?? findByUsername(query, found)?.user.username ?? null);

  useEffect(() => {
    onMatch(match);
  }, [match, onMatch]);

  useEffect(() => {
    // A pick fills the field with the exact username; searching for it again would only reopen the list.
    if (!query || query === picked) {
      setSearching(false);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const timer = setTimeout(() => {
      api
        .searchPlayers(query, SUGGESTIONS)
        .then((players) => {
          if (cancelled) return;
          setFound(players.filter((player) => player.user.id !== viewer?.id));
          setOpen(true);
        })
        .catch(() => {
          // Suggestions are a convenience: a failed search leaves the typed name as it is.
          if (!cancelled) setFound([]);
        })
        .finally(() => {
          if (!cancelled) setSearching(false);
        });
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query, picked, viewer?.id]);

  function pick(player: PlayerSummary) {
    setPicked(player.user.username);
    onChange(player.user.username);
    setFound([]);
    setOpen(false);
  }

  const showList = open && found.length > 0;
  const hint = searching
    ? t("create.searching")
    : query && !match
      ? found.length > 0
        ? t("create.playerPick")
        : t("create.playerNoMatch")
      : t("create.playerHint");

  return (
    <div className="suggest">
      <div className="suggest__field">
        <input
          className="field__input"
          value={value}
          disabled={disabled}
          placeholder={t("create.playerPlaceholder")}
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          maxLength={20}
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-invalid={self}
          aria-label={t("create.player")}
          onChange={(event) => {
            setPicked(null);
            onChange(event.target.value);
          }}
          onFocus={() => setOpen(true)}
          // Delayed so that a click on a suggestion lands before the list goes away.
          onBlur={() => setTimeout(() => setOpen(false), 150)}
        />
        {showList ? (
          <ul className="suggest__list" id={listId} role="listbox">
            {found.map((player) => (
              <li key={player.user.id} role="option" aria-selected={false}>
                <button type="button" className="suggest__option" onMouseDown={(event) => event.preventDefault()} onClick={() => pick(player)}>
                  <span className="suggest__name">
                    {displayName(player.user)}
                    <UserTag user={player.user} />
                    <span className="suggest__username">{player.user.username}</span>
                  </span>
                  <Rating value={ratingIn(player.ratings, kind)?.rating} />
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
      {self ? (
        <span className="field__error" role="alert">
          {t("create.playerSelf")}
        </span>
      ) : (
        <span className="field__hint">{hint}</span>
      )}
    </div>
  );
}
