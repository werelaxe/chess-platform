ALTER TABLE users ADD COLUMN display_name VARCHAR(40) NULL;
ALTER TABLE users ADD COLUMN bot_level    VARCHAR(8)  NULL;
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMPTZ NULL;

-- The single computer account of the previous versions stays a MEDIUM bot for its old games.
UPDATE users SET bot_level = 'MEDIUM', display_name = 'Computer' WHERE is_bot = TRUE;

CREATE TABLE ratings (
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    kind       VARCHAR(16) NOT NULL,
    rating     INT         NOT NULL,
    games      INT         NOT NULL DEFAULT 0,
    wins       INT         NOT NULL DEFAULT 0,
    draws      INT         NOT NULL DEFAULT 0,
    losses     INT         NOT NULL DEFAULT 0,
    peak       INT         NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, kind)
);

CREATE INDEX ratings_leaderboard_idx ON ratings (kind, rating DESC, games DESC);

CREATE TABLE rating_changes (
    id            BIGSERIAL   PRIMARY KEY,
    game_id       VARCHAR(16) NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    kind          VARCHAR(16) NOT NULL,
    rating_before INT         NOT NULL,
    rating_after  INT         NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX rating_changes_game_idx ON rating_changes (game_id);
CREATE INDEX rating_changes_user_idx ON rating_changes (user_id, created_at DESC);

ALTER TABLE games ADD COLUMN rated               BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE games ADD COLUMN invitee_id          BIGINT  NULL REFERENCES users (id);
ALTER TABLE games ADD COLUMN white_rating_before INT     NULL;
ALTER TABLE games ADD COLUMN black_rating_before INT     NULL;
ALTER TABLE games DROP COLUMN bot_level;

CREATE INDEX games_invitee_idx ON games (invitee_id);
