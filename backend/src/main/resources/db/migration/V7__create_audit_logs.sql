-- Append-only audit trail (audit module, Phase 8). Populated purely by listening to domain
-- events published by other modules (task, project, team, comment) - the audit module never
-- calls into another module, only listens. See docs/architecture.md's "Two kinds of
-- cross-module events" section: these are plain in-process Spring events for now (audit is
-- the only consumer), with Kafka publishing of some of the same event types added in Phase 10
-- for other consumers (notifications).
--
-- old_value/new_value are JSONB, not their own typed columns, because different action types
-- legitimately have different shapes ("status: TODO -> IN_PROGRESS" vs "added member: user X")
-- and a generic audit trail across many resource types is exactly the case where a flexible
-- semi-structured column earns its keep instead of fighting a fixed relational shape - see
-- ADR-002's "why PostgreSQL" for why this is a deliberate, narrow use of JSONB rather than a
-- wholesale move away from relational modeling.
CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id      UUID REFERENCES users(id),
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id   UUID NOT NULL,
    project_id    UUID,
    team_id       UUID,
    old_value     JSONB,
    new_value     JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- actor_id is nullable, unlike most "who did this" columns elsewhere in this schema: a future
-- system-initiated action (a scheduled job, an automated rule) would have no human actor, and
-- the audit trail should still be able to record what happened even then.

-- "View project activity" (an explicit brief requirement) is the primary read query -
-- everything that happened within one project, newest first.
CREATE INDEX ix_audit_logs_project_id_created_at ON audit_logs (project_id, created_at) WHERE project_id IS NOT NULL;

-- Team-level activity (membership changes) - same shape, different scope.
CREATE INDEX ix_audit_logs_team_id_created_at ON audit_logs (team_id, created_at) WHERE team_id IS NOT NULL;

-- "What has this actor done" and general retention/cleanup queries.
CREATE INDEX ix_audit_logs_actor_id ON audit_logs (actor_id) WHERE actor_id IS NOT NULL;
