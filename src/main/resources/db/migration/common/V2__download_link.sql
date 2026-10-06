-- A download link stores only the SHA-256 of its token. UNIQUE(token_hash) doubles as the lookup index for
-- GET /download/{token}; the table-level CHECK guarantees the conditional UPDATE can never over-consume.
CREATE TABLE download_link (
    id                 UUID         NOT NULL,
    statement_id       UUID         NOT NULL,
    customer_id        VARCHAR(128) NOT NULL,
    token_hash         BYTEA        NOT NULL,
    issued_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    max_downloads      INTEGER      NOT NULL,
    download_count     INTEGER      NOT NULL DEFAULT 0,
    revoked_at         TIMESTAMP WITH TIME ZONE NULL,
    last_downloaded_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT pk_download_link PRIMARY KEY (id),
    CONSTRAINT fk_download_link_statement FOREIGN KEY (statement_id) REFERENCES statement (id),
    CONSTRAINT uq_download_link_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_link_max_downloads CHECK (max_downloads BETWEEN 1 AND 10),
    CONSTRAINT chk_link_count CHECK (download_count >= 0 AND download_count <= max_downloads),
    CONSTRAINT chk_link_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX idx_download_link_statement ON download_link (statement_id, issued_at DESC);
