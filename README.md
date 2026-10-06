# Secure Statement Delivery

A Spring Boot 4.1 / Java 21 service that stores customer account statements as **encrypted PDF files** and
issues **secure, time-limited, single-use download links** to customers.

> Brief: "Develop a system to store customer account statements as PDF files and provide secure,
> time-limited download links to customers."

- Hexagonal architecture in one module, enforced by ArchUnit; the domain and application layers compile against the JDK alone.
- AES-256-GCM envelope encryption at rest behind a `KeyProvider` port.
- Links are 256-bit capability tokens; only their SHA-256 is stored. Atomic single-use via one conditional `UPDATE`, proven by 64-thread races on H2 and PostgreSQL.
- Every download failure is one byte-identical 404; the real reason lives in an append-only audit table.
- JWT resource server with a fail-closed default chain; a prod guard refuses to start with any development artefact active.
- Built test-first: 270+ fast tests without Docker, plus PostgreSQL integration tests via Testcontainers.

## Quick start

Requires Docker. No local JDK is needed for this path.

```bash
docker build -t secure-statements .
docker run --rm -p 127.0.0.1:8080:8080 -v statements-data:/data secure-statements
```

Open <http://localhost:8080/swagger-ui.html>. Bind to loopback as shown: the dev profile's token issuer must
not be reachable from the network. The image defaults to the `h2,dev,demo` profiles: file-based H2
and filesystem storage on the `/data` volume, a development token issuer at `POST /dev/token`, and five seeded
demo statements for customers `C-1001` and `C-2002`.

With PostgreSQL instead of H2:

```bash
cp .env.example .env            # optionally set APP_CRYPTO_KEK=$(openssl rand -base64 32)
docker compose up --build
```

Without Docker, with JDK 21: `./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,dev,demo`.

## Try it in 60 seconds

1. `POST /dev/token` with `{"subject":"C-1001","roles":["CUSTOMER"]}`; paste the token into Swagger's **Authorize** button.
2. `GET /api/statements` lists the seeded statements. `POST /api/statements/{statementId}/links` issues a link; copy the `url`.
3. Download it: `curl -OJ "<url>"` saves `statement-1234567890-2026-09.pdf`.
4. Download it again: `curl -i "<url>"` returns a constant `404 Statement not available`. The link was single-use.
5. `GET /api/statements/{statementId}/links` shows the link as `EXHAUSTED`. `DELETE /api/links/{linkId}` revokes a link.
6. To upload your own PDF, mint `{"subject":"ops-admin","roles":["ADMIN"]}` and `POST /api/admin/statements`
   (multipart: `file`, `customerId`, `accountNumber`, `period` as `yyyy-MM`).

The audit trail is in the `download_audit` table: `LINK_ISSUED`, `SUCCESS`, `EXHAUSTED`, `LINK_REVOKED`, `REVOKED`,
`UNKNOWN_TOKEN`, and so on. The app log shows delivery with the token redacted:
`Download link <id> for customer C-1001 delivered to http://localhost:8080/download/[redacted]`.

`scripts/smoke.sh [base-url]` drives this whole flow against a running instance and checks every step.

## Build and test

Requires JDK 21. Docker is needed only for the `*IT` integration tests (Testcontainers PostgreSQL).

```bash
./mvnw test              # unit, slice, contract and H2 end-to-end tests: no Docker needed
./mvnw verify            # adds the PostgreSQL integration tests and enforces the coverage gate
./mvnw verify -DskipITs  # verify without Docker
```

Reports: `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco/index.html`.
CI (`.github/workflows/ci.yml`) runs `./mvnw verify`, builds the image, starts it with the compose hardening
(read-only root, no capabilities, loopback port) and runs `scripts/smoke.sh` against it.

## API

| Method and path | Who | Result |
|---|---|---|
| `POST /api/admin/statements` (multipart) | ADMIN | 201 + `Location`; 400 / 403 / 409 / 413 / 415 |
| `GET /api/statements` | the JWT subject | own statements, newest first; `?customerId` is rejected with 400 |
| `POST /api/statements/{id}/links` | owner only (the response carries the credential) | 201 `{linkId, url, expiresAt, maxDownloads}`; 404 if not yours |
| `GET /api/statements/{id}/links` | owner or ADMIN | status and counts per link; never tokens or hashes |
| `DELETE /api/links/{linkId}` | owner or ADMIN | 204, idempotent; 404 if not yours |
| `GET /download/{token}` | public: the token is the credential | 200 PDF, or one constant 404 (infrastructure faults included, audited as `INTERNAL_ERROR`); 503 when saturated |
| `HEAD /download/{token}` | public | constant 404, never consumes the link |
| `POST /dev/token` | dev profile only | RS256 JWT for Swagger and curl |
| `/actuator/health`, `/liveness`, `/readiness` | public | readiness includes the database |
| `/actuator/metrics/**`, `/actuator/prometheus` | ADMIN | `uri` tag is always `/download/{token}` |

"Not yours" and "does not exist" are deliberately indistinguishable everywhere.

## Architecture

```
src/main/java/dev/rambally/statements
├── domain/         pure Java: Statement, DownloadLink, value objects, sealed DomainException
├── application/    use cases (one interface per driving port), driven ports, AesGcmEnvelopeCipher
│   ├── port/in     UploadStatement, IssueDownloadLink, RedeemDownloadLink, ListStatements, ListLinks, RevokeDownloadLink
│   └── port/out    StatementRepository, DownloadLinkRepository, StatementStorage, KeyProvider, AuditLog, NotificationPort, TokenGenerator
├── adapters/in/    REST controllers, JwtPrincipalResolver, ApiExceptionHandler, DownloadConcurrencyFilter, DevTokenController
├── adapters/out/   JDBC repositories and audit log, filesystem storage, local KEK provider, logging notifier, token generator
└── bootstrap/      Spring only: SecurityConfig, UseCaseConfig, AdapterConfig, AppProperties, ProdGuard, OpenAPI, metrics, demo seed
```

`HexagonalArchitectureTest` enforces: domain depends only on the JDK; application only on domain and the
JDK; no Spring, Jakarta or SLF4J in either; adapter slices are independent of each other; inbound adapters
depend on driving ports and never on service classes; nothing outside bootstrap depends on bootstrap;
services are constructed only in bootstrap; no field injection.

## Security model

| Threat | Control | Proving test |
|---|---|---|
| Stolen storage volume | AES-256-GCM per statement; envelope (wrapped DEK, IVs) is in the database, not beside the file | `AesGcmEnvelopeCipherTest`, `EndToEndH2Test` (no `%PDF-` on disk) |
| Stolen database dump | Only token hashes and wrapped keys are stored; KEK is in the environment or a KMS | `IssueDownloadLinkServiceTest.stored_link_holds_hash_not_token` |
| Guessing links | 256-bit random tokens; constant 404 for every failure | `DownloadControllerTest.every_redemption_outcome_returns_byte_identical_404_problem_detail` |
| Replaying a leaked link | Single-use by default (configurable 1 to 10), 24 h TTL, revocable, HEAD never consumes | `DownloadLinkConcurrencyIT`, `ParallelDownloadIT`, `LinkLifecycleHttpTest` |
| Tampered or swapped ciphertext | GCM tag verified before the first byte; AAD binds statement id and format | `AesGcmEnvelopeCipherTest` tamper and swap cases |
| Token leaking via logs or metrics | Redacted `toString`, redacting notifier (no customer ids either), templated `uri` tag, no access log | `EndToEndH2Test` (scans captured logs and every meter tag, with positive controls) |
| Token leaking via error bodies | `instance` fixed to `/api` or `/download`; details are reason phrases | `ApiExceptionHandlerTest`, `ServerErrorHygieneTest` |
| Forged `Host` header | Links built from `APP_PUBLIC_BASE_URL`, forwarded headers ignored | `PublicBaseUrlTest`, `IssueDownloadLinkServiceTest` |
| Horizontal access (IDOR) | Identity from the JWT subject only; ownership checked in services; 404 for not-yours; admins cannot mint customer links | `StatementControllerTest`, `IssueDownloadLinkServiceTest`, `RevokeDownloadLinkServiceTest` |
| Token minted for another service | Audience required and validated in every profile; `ProdGuard` refuses to start without one | `JwtNegativeHttpTest`, `ProdGuardTest` |
| Misattributed admin actions | Issue and revoke audit rows carry `actor_id` separately from the customer | `RevokeDownloadLinkServiceTest`, `JdbcAuditLogTest` |
| Forgotten security rule | No-matcher default chain ends in `denyAll` | `SecurityConfigTest` |
| Dev artefacts in production | `ProdGuard` refuses the dev key, blank key, http base URL, H2, dev/demo profiles, open Swagger | `ProdGuardTest` |
| Audit tampering or loss | `REQUIRES_NEW` writes that never throw; PostgreSQL trigger forbids UPDATE/DELETE/TRUNCATE | `JdbcAuditLogTest`, `DownloadAuditAppendOnlyIT` |
| Memory exhaustion via downloads | 10 MiB cap, bulkhead filter holding its permit until the body is written, 503 + `Retry-After` | `DownloadConcurrencyFilterTest` |

## The hard problems

- **Atomic single-use under concurrency.** One `UPDATE ... WHERE revoked_at IS NULL AND expires_at > :now AND
  download_count < max_downloads`; the update count is the verdict. `DownloadLinkPredicateParityTest` proves the
  SQL predicate agrees with the domain rule over a state matrix including the exact expiry instant.
- **Uniform 404 without losing diagnostics.** The controller returns one literal body for every failure and
  the audit adapter records the real outcome in its own transaction.
- **Streaming versus integrity.** Bounded-buffer decryption with a size cap and a bulkhead, chosen over
  streaming so a tampered file can never produce a partial 200 (ADR-004).
- **Decrypt before consume.** A bank-side fault never burns the customer's one download.
- **Public chain beside a JWT chain.** Explicit matchers for the public paths, a fail-closed default for the rest.

## Configuration

Profiles: `h2` (file DB and storage under `APP_DATA_DIR`), `postgres`, `dev` (RSA token issuer, Swagger, dev
KEK fallback with a WARN banner), `demo` (seeded statements), `prod` (issuer URI, https, no dev fallback,
Swagger off, structured ECS logs, guard), `test`.

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `h2,dev,demo` in the image | profile set |
| `APP_DATA_DIR` | `/data` in the image | H2 file and ciphertext root |
| `APP_PUBLIC_BASE_URL` | `http://localhost:8080` | base of issued links; must be https in prod |
| `APP_CRYPTO_KEK` | blank (dev fallback) | base64 of 32 random bytes; required in prod |
| `APP_CRYPTO_KEK_ID` | `local-kek-v1` | identifier stored with each statement, for rotation |
| `APP_JWT_ISSUER_URI` | none | prod JWT issuer (required by the guard) |
| `APP_JWT_AUDIENCE` | `secure-statements` | audience every token must carry |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | Tomcat default (private ranges) | prod only: proxies whose `X-Forwarded-*` headers are trusted |
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | compose values | PostgreSQL connection |
| `APP_LINK_TTL`, `APP_LINK_MAX_DOWNLOADS` | `PT24H`, `1` | link policy (bounded 1 min to 30 d, 1 to 10) |
| `APP_DOWNLOAD_MAX_CONCURRENT` | `16` | bulkhead permits |

Production sketch: `SPRING_PROFILES_ACTIVE=postgres,prod`, the KEK from a secrets manager, TLS at the ingress
forwarding `X-Forwarded-For` / `X-Forwarded-Proto`, `APP_PUBLIC_BASE_URL=https://...`, `APP_JWT_ISSUER_URI` and
`APP_JWT_AUDIENCE` matching the identity provider. `ProdGuard` refuses anything less, naming every missing item.

## Extension points designed for, not built

- `S3StatementStorage` behind `StatementStorage` (the byte[] contract maps to put/get).
- `KmsKeyProvider` behind `KeyProvider` (wrap/unwrap with an encryption context).
- `SmtpNotificationAdapter` behind `NotificationPort` (receives the real URL).
- KEK rotation job re-wrapping `wrapped_dek` rows; the content AAD excludes the KEK id for exactly this reason.
- Chunked AEAD as `cipher_format = 2` for statements beyond tens of megabytes.
- Edge rate limiting; in-process limiting was rejected because 256-bit tokens make enumeration infeasible.
- Digest-pinned base images and an image vulnerability scan in CI.

## Decisions

`docs/adr/` records the eight decisions behind the design: hexagonal single module, JDBC not JPA, token as
credential with uniform 404, bounded-buffer decrypt-then-consume with append-only audit, envelope encryption
and the KMS path, the two security chains, public base URL and the notification port, and the testing approach.

Deliberately out of scope: Kafka, Kubernetes manifests, a UI, MinIO, in-process rate limiting, PDF parsing of
uploads, JPA.

## Toolchain note

Spring Boot 4.1.x was chosen over the 3.5 line because 3.5's open-source support ended in June 2026. Boot 4
moves test-slice annotations into technology modules (`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`,
`org.springframework.boot.jdbc.test.autoconfigure.JdbcTest`), renames Testcontainers 2 artifacts
(`testcontainers-postgresql`) and defaults to Jackson 3; the code here uses those directly.
