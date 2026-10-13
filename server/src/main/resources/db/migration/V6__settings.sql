-- Runtime settings of the bots (docs/ARCHITECTURE.md section 2.11); a key without a row has its default.
CREATE TABLE settings (
    key        VARCHAR(64) PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
