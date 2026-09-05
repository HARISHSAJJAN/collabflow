# Search and filtering

## Why PostgreSQL full-text search, not Elasticsearch

The brief is explicit about this: use PostgreSQL's own capabilities first, and only reach for
a dedicated search engine if there's a meaningful reason to. There isn't one yet:

- **Data volume**: this is a single-tenant, locally-deployed project with no measured
  production traffic (see `docs/troubleshooting.md`'s "no fake experience" standard) - nowhere
  close to the scale where PostgreSQL's GIN-indexed `tsvector` search stops being fast enough.
- **Feature need**: `tsvector`/`tsquery` already provides real relevance-aware matching
  (English stemming - "authentication" matches "authenticate", "authenticating"; stop-word
  removal) with an index behind it, not a naive `LIKE '%...%'` scan. This covers the brief's
  "keyword search" requirement completely.
- **Operational cost**: Elasticsearch/OpenSearch would mean a second datastore to keep in
  sync with PostgreSQL (dual-write or CDC), a second thing to run and monitor, and a second
  query language - real ongoing cost that buys nothing at this scale.

**What would justify Elasticsearch/OpenSearch later**: multi-field relevance ranking across
very large text bodies, typo-tolerant ("fuzzy") search, faceted search UIs needing
sub-second aggregation over millions of rows, or search that needs to span data this
application doesn't itself own. If any of those became real requirements, the migration path
is additive, not a rewrite: keep PostgreSQL as the system of record, stand up
Elasticsearch/OpenSearch as a read-optimized index fed by the same domain events already
published to Kafka (`collabflow.task-events`, etc. - see docs/kafka.md) via a new consumer,
and point the search endpoint at it instead of `TaskRepository.advancedSearch`. The
event-driven architecture already built for Phase 10 is exactly the mechanism that migration
would use.

## What's implemented (Phase 13)

`GET /api/v1/tasks/search?projectId=&status=&priority=&assigneeId=&dueDateFrom=&dueDateTo=&label=&keyword=&page=&size=`

- **Keyword** (`keyword`): full-text match against a generated, indexed `search_vector`
  column (`to_tsvector('english', title || ' ' || description)`, `STORED` so it's always in
  sync with no application-level upkeep - see the V9 migration's comment) via `plainto_tsquery`.
- **Status / priority / assignee**: exact match, same as the simpler `GET /api/v1/tasks`.
- **Due-date range** (`dueDateFrom`/`dueDateTo`): inclusive range on `due_date`.
- **Label** (`label`): exact match against `task_labels`.
- All filters combine with AND, and are all optional - omit any of them to not filter on it.
- **Pagination**: standard `page`/`size`.
- **Sorting**: fixed (not client-controlled) on this endpoint - see `TaskRepository.
  advancedSearch`'s Javadoc for why (a native-query/`Sort` interaction limitation, documented
  rather than silently broken). The plain `GET /api/v1/tasks` listing endpoint (Phase 7) *does*
  support client-driven `?sort=field,direction`, since it's backed by JPQL, which does
  participate in Spring Data's property-to-column mapping.

## A real bug found by testing: parameter type inference

See `docs/troubleshooting.md` for the full writeup. Short version: PostgreSQL resolves a
native query's bind-parameter types once, at prepare time, from the query's structure alone -
a parameter whose only appearance is `:param IS NULL` gives it nothing to infer from, and
`ERROR: could not determine data type of parameter $8` surfaced on the very first real request
using the due-date-range filter (with an actual, non-null date value - the ambiguity is about
the query's *structure*, not whether the value happens to be null at runtime). Fixed by
explicitly `cast(:param as <type>)` in every such check.

## Indexes behind this

| Index | Backs |
|---|---|
| `ix_tasks_search_vector` (GIN) | `keyword` |
| `ix_tasks_project_id_status` (existing, Phase 7) | `status`/project scoping |
| `ix_tasks_assignee_id` (existing, Phase 7) | `assigneeId` |
| `ix_tasks_due_date` (existing, Phase 7) | `dueDateFrom`/`dueDateTo` |

`label` and the count query's `EXISTS` subquery against `task_labels` use
`ux_task_labels_task_label` (Phase 7) - its leading column is `task_id`, so it serves the
per-task existence check efficiently even though it wasn't originally added with this query in
mind.
