import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { api } from "../api/client";
import type { GameKind, PlayerSummary } from "../api/types";
import { displayName } from "../core/players";
import { ratingIn } from "../core/ratings";
import { useAsync } from "../hooks/useAsync";
import { ErrorNotice, Loading, Rating, UserTag } from "./ui";

/** The bot to preselect: a medium one when the roster has it, otherwise the first. */
function defaultBot(bots: PlayerSummary[]): PlayerSummary | undefined {
  return bots.find((bot) => bot.user.botLevel === "MEDIUM") ?? bots[0];
}

/** The roster of bots as a list of radio options showing each one's level and rating in `kind`. */
export function BotPicker({
  kind,
  value,
  onChange,
  disabled,
}: {
  kind: GameKind;
  value: string | null;
  onChange: (username: string) => void;
  disabled?: boolean;
}) {
  const { t } = useTranslation();
  const { data, error, loading, reload } = useAsync(() => api.bots(), []);

  useEffect(() => {
    if (!data || data.length === 0) return;
    if (value !== null && data.some((bot) => bot.user.username === value)) return;
    const first = defaultBot(data);
    if (first) onChange(first.user.username);
  }, [data, value, onChange]);

  if (error) return <ErrorNotice error={error} onRetry={() => reload()} />;
  if (loading || !data) return <Loading label={t("create.loadingBots")} />;
  if (data.length === 0) return <span className="field__hint">{t("create.noBots")}</span>;

  return (
    <div className="bot-list" role="radiogroup" aria-label={t("create.bot")}>
      {data.map((bot) => {
        const selected = bot.user.username === value;
        return (
          <button
            key={bot.user.id}
            type="button"
            role="radio"
            aria-checked={selected}
            disabled={disabled}
            className={`bot-option${selected ? " is-selected" : ""}`}
            onClick={() => onChange(bot.user.username)}
          >
            <span className="bot-option__name">
              {displayName(bot.user)}
              <UserTag user={bot.user} />
            </span>
            <Rating value={ratingIn(bot.ratings, kind)?.rating} title={t(`variants.${kind}`)} />
          </button>
        );
      })}
    </div>
  );
}
