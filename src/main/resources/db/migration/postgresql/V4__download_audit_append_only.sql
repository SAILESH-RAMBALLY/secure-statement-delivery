-- PostgreSQL only: make the audit trail append-only at the database level. The application never updates or
-- deletes audit rows, and these triggers stop anyone else doing it by accident or through the application's
-- normal connection. The table's owner can still disable triggers, so a production setup should run Flyway
-- as a separate owner role and give the application user INSERT and SELECT only.
CREATE OR REPLACE FUNCTION download_audit_forbid_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'download_audit is append-only (% not permitted)', TG_OP USING ERRCODE = '42501';
END
$$;

CREATE TRIGGER trg_download_audit_no_update
    BEFORE UPDATE ON download_audit
    FOR EACH ROW EXECUTE FUNCTION download_audit_forbid_mutation();

CREATE TRIGGER trg_download_audit_no_delete
    BEFORE DELETE ON download_audit
    FOR EACH ROW EXECUTE FUNCTION download_audit_forbid_mutation();

CREATE TRIGGER trg_download_audit_no_truncate
    BEFORE TRUNCATE ON download_audit
    FOR EACH STATEMENT EXECUTE FUNCTION download_audit_forbid_mutation();
