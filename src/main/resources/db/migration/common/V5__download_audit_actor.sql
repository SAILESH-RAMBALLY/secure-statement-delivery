-- Who performed a LINK_ISSUED or LINK_REVOKED action. It can differ from customer_id: an administrator can
-- revoke a customer's link. Added as its own migration because V3 has already shipped.
ALTER TABLE download_audit ADD COLUMN actor_id VARCHAR(128) NULL;
