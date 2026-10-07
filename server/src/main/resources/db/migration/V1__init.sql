CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(20) NOT NULL,
    username_lower VARCHAR(20) NOT NULL UNIQUE,
    password_hash  TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE TABLE games (
    id              VARCHAR(16) PRIMARY KEY,
    kind            VARCHAR(16) NOT NULL,
    visibility      VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    creator_id      BIGINT      NOT NULL REFERENCES users (id),
    white_id        BIGINT      NULL REFERENCES users (id),
    black_id        BIGINT      NULL REFERENCES users (id),
    move_count      INT         NOT NULL DEFAULT 0,
    result_winner   VARCHAR(8)  NULL,
    result_reason   VARCHAR(32) NULL,
    draw_offered_by VARCHAR(8)  NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ NULL
);

CREATE INDEX games_public_listing_idx ON games (visibility, status, created_at DESC);
CREATE INDEX games_creator_idx ON games (creator_id);
CREATE INDEX games_white_idx ON games (white_id);
CREATE INDEX games_black_idx ON games (black_id);

CREATE TABLE moves (
    game_id    VARCHAR(16) NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    ply        INT         NOT NULL,
    move       JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (game_id, ply)
);
