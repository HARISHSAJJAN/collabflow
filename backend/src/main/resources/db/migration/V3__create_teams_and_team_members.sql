-- Teams and team membership (team module, Phase 5).
--
-- team_members.role uses VARCHAR + CHECK rather than a native Postgres ENUM type. A native
-- enum type is tempting for "the database rejects invalid values" the same guarantee, but
-- adding a new value to a Postgres enum type cannot run inside the same transaction as other
-- schema changes in older PostgreSQL versions and is generally more awkward to evolve than a
-- CHECK constraint, which is just a DROP CONSTRAINT / ADD CONSTRAINT away from changing. Since
-- this project explicitly wants to demonstrate comfortable schema evolution (see
-- docs/database.md), VARCHAR + CHECK is the more maintainable choice for a value set that,
-- while currently fixed, is exactly the kind of thing a product requirement could add to
-- later (e.g. a future VIEWER role).
CREATE TABLE teams (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "My teams" (list every team a user belongs to) is a common query; teams a user created are
-- a natural filter too, backed by this index on the FK itself.
CREATE INDEX ix_teams_created_by ON teams (created_by);

CREATE TABLE team_members (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id   UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A user can only hold one membership row (one role) per team - this is what "assign roles"
-- actually means: update the row, not add a second one.
CREATE UNIQUE INDEX ux_team_members_team_user ON team_members (team_id, user_id);

-- Listing a team's roster (GET /teams/{id}/members) filters by team_id; the unique index
-- above already serves that as its leading column, so no separate index is needed for it.
-- "My teams" (join team_members -> teams where user_id = ?) needs its own index on user_id,
-- since team_id being the unique index's leading column means it can't efficiently serve a
-- user_id-only lookup.
CREATE INDEX ix_team_members_user_id ON team_members (user_id);
