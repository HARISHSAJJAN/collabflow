-- Projects (project module, Phase 6). A project always belongs to exactly one team; a
-- project's members are drawn from that team's roster (enforced in application code, not the
-- schema - see ProjectService) rather than being an independent user pool.
CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "List this team's projects" (optionally filtered by status) is the primary listing query -
-- a composite index serves both "all of a team's projects" and "a team's ACTIVE projects"
-- without a second, narrower index.
CREATE INDEX ix_projects_team_id_status ON projects (team_id, status);

CREATE TABLE project_members (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- No `role` column here, unlike team_members: a project member's permissions are their TEAM
-- role (OWNER/ADMIN/MEMBER), looked up via team_members at authorization time. See
-- ProjectService's Javadoc for the full policy and why a separate project-level role would be
-- redundant given how this schema's authorization model is structured.
CREATE UNIQUE INDEX ux_project_members_project_user ON project_members (project_id, user_id);
CREATE INDEX ix_project_members_user_id ON project_members (user_id);
