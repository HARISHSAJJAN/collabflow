-- Full-text keyword search for tasks (Phase 13), using PostgreSQL's built-in text search
-- rather than introducing Elasticsearch/OpenSearch. See docs/search.md for the full "why not
-- Elasticsearch yet, and what would change if it were needed" discussion - the short version:
-- this project's scale doesn't come close to needing a dedicated search engine, and
-- PostgreSQL's tsvector/GIN combination already gives real relevance-free full-text matching
-- (stemming, stop-word removal) with an index, not a naive LIKE '%...%' table scan.
--
-- A generated, STORED column (not computed at query time): PostgreSQL recomputes a STORED
-- generated column automatically whenever title/description change, so the indexed value is
-- always in sync with no application-level trigger or dual-write to keep it that way, and it
-- can be indexed like any other column - a query-time to_tsvector(...) call could not be.
ALTER TABLE tasks ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, ''))) STORED;

-- A GIN (Generalized Inverted Index) index, not a plain B-tree: full-text search needs to
-- efficiently answer "which rows contain this lexeme," which is exactly the inverted-index
-- structure GIN provides (a B-tree can't usefully index a tsvector's internal structure at all).
CREATE INDEX ix_tasks_search_vector ON tasks USING GIN (search_vector);
