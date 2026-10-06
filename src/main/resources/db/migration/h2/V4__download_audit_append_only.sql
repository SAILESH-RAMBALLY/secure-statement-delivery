-- H2 counterpart of the PostgreSQL append-only triggers. H2 has no PL/pgSQL; on H2 (local runs and fast
-- tests) append-only is a code-level property: JdbcAuditLog only ever INSERTs. Kept so both engines report
-- the same migration versions.
SELECT 1;
