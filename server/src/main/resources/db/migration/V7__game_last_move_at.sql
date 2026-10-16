-- When the game was last played: the latest move, or the start of the game before the first one;
-- null while the game waits for an opponent. The stale sweep (docs/ARCHITECTURE.md section 2.3)
-- counts idle time from here rather than from updated_at, which a draw offer touches too.
ALTER TABLE games ADD COLUMN last_move_at TIMESTAMPTZ NULL;
UPDATE games g
SET last_move_at = COALESCE((SELECT MAX(m.created_at) FROM moves m WHERE m.game_id = g.id), g.updated_at)
WHERE g.status <> 'WAITING';
CREATE INDEX games_stale_idx ON games (status, last_move_at);
