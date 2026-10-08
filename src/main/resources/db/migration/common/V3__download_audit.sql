-- Append-only audit trail. Deliberately no foreign key to download_link: an UNKNOWN_TOKEN attempt has no
-- link, and auditing must never fail because a referenced row is absent.
CREATE TABLE download_audit (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    occurred_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type        VARCHAR(16)  NOT NULL,          -- LINK_ISSUED | LINK_REVOKED | REDEMPTION
    outcome           VARCHAR(32)  NULL,              -- RedemptionOutcome, for REDEMPTION rows
    token_hash_prefix VARCHAR(16)  NULL,
    link_id           UUID         NULL,
    statement_id      UUID         NULL,
    customer_id       VARCHAR(128) NULL,
    client_ip         VARCHAR(45)  NULL,
    user_agent        VARCHAR(255) NULL,
    CONSTRAINT pk_download_audit PRIMARY KEY (id)
);

CREATE INDEX idx_download_audit_link ON download_audit (link_id, occurred_at);
CREATE INDEX idx_download_audit_time ON download_audit (occurred_at);
