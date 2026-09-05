-- Backs both refresh-token rotation (auth module, Phase 3) and the "manage sessions" user
-- feature (each row is, in effect, one logged-in session/device).
--
-- The raw refresh token is never stored - only a SHA-256 hash of it (token_hash). This means
-- a database leak alone (backup exposed, SQL injection read-only leak, careless log dump)
-- does not hand out usable long-lived credentials; an attacker would still need the original
-- token value, which only ever existed in the HTTP response body and the client's storage.
-- This mirrors how password_hash works for passwords - the same principle, applied to tokens.
CREATE TABLE refresh_tokens (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash              VARCHAR(255) NOT NULL,
    issued_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL,
    revoked_at              TIMESTAMPTZ,
    replaced_by_token_id    UUID REFERENCES refresh_tokens(id),
    user_agent              VARCHAR(512),
    ip_address              VARCHAR(64)
);

-- Every refresh happens by looking up a token by its hash - this is the hot path and must be
-- unique (two different sessions must never validate against the same hash).
CREATE UNIQUE INDEX ux_refresh_tokens_token_hash ON refresh_tokens (token_hash);

-- "List my active sessions" / "revoke all my sessions" (a "manage sessions" feature) both
-- filter by user_id. A partial index (only rows not yet revoked) keeps it small and fast as
-- the table accumulates historical, already-revoked/expired rows over time - those rows are
-- kept for audit purposes rather than deleted, but almost never need to be scanned.
CREATE INDEX ix_refresh_tokens_user_id_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

-- Backs the scheduled cleanup job that deletes long-expired, revoked rows so this table
-- doesn't grow forever (added in Phase 3 alongside the rest of the auth module).
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);
