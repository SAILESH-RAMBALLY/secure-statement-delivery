-- Statement metadata plus the encryption envelope. The ciphertext itself lives in StatementStorage, so a
-- stolen database dump is useless without the files and the KEK, and a stolen volume is useless without the DB.
CREATE TABLE statement (
    id             UUID         NOT NULL,
    customer_id    VARCHAR(128) NOT NULL,
    account_number VARCHAR(20)  NOT NULL,
    period         CHAR(7)      NOT NULL,            -- yyyy-MM
    size_bytes     BIGINT       NOT NULL,
    content_sha256 BYTEA        NOT NULL,            -- digest of the plaintext PDF
    storage_key    VARCHAR(255) NOT NULL,
    cipher_format  SMALLINT     NOT NULL,
    kek_id         VARCHAR(64)  NOT NULL,
    wrapped_dek    BYTEA        NOT NULL,
    dek_iv         BYTEA        NOT NULL,
    content_iv     BYTEA        NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_statement PRIMARY KEY (id),
    CONSTRAINT uq_statement_storage_key UNIQUE (storage_key),
    CONSTRAINT uq_statement_account_period UNIQUE (customer_id, account_number, period),
    CONSTRAINT chk_statement_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT chk_statement_cipher_format CHECK (cipher_format = 1)
);

CREATE INDEX idx_statement_customer_period ON statement (customer_id, period DESC);
