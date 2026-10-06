# Secure Statement Delivery

A Spring Boot 4.1 / Java 21 service that stores customer account statements as **encrypted PDF files** and
issues **secure, time-limited, single-use download links** to customers.

> Brief: "Develop a system to store customer account statements as PDF files and provide secure,
> time-limited download links to customers."

## Quick start

Requires Docker. No local JDK needed for this path.

```bash
docker build -t secure-statements .
docker run --rm -p 8080:8080 -v statements-data:/data secure-statements
```

Open <http://localhost:8080/swagger-ui.html>. The image defaults to the `h2,dev,demo` profiles: file-based H2
and filesystem storage on the `/data` volume, a development token issuer at `POST /dev/token`, and seeded
demo statements.

With PostgreSQL instead of H2:

```bash
cp .env.example .env            # optionally set APP_CRYPTO_KEK=$(openssl rand -base64 32)
docker compose up --build
```

## Try it in 60 seconds

1. `POST /dev/token` with `{"subject":"ops-admin","roles":["ADMIN"]}` and again with
   `{"subject":"C-1001","roles":["CUSTOMER"]}`. Paste a token into Swagger's **Authorize** button.
2. As **ops-admin**: `POST /api/admin/statements` with a PDF, `customerId=C-1001`, `accountNumber=1234567890`,
   `period=2026-09`.
3. As **C-1001**: `GET /api/statements`, then `POST /api/statements/{statementId}/links`. Copy the `url`.
4. Download it: `curl -OJ "<url>"` — you get `statement-1234567890-2026-09.pdf`.
5. Download it again: `curl -i "<url>"` — a constant `404 Statement not available`. The link was single-use.
6. Look at the audit trail: `SELECT event_type, outcome FROM download_audit ORDER BY id` shows
   `LINK_ISSUED`, `SUCCESS`, `EXHAUSTED`.

The app log shows the delivery notification with the token redacted:
`Download link <id> for customer C-1001 delivered to http://localhost:8080/download/[redacted]`.

## Build and test

Requires JDK 21. Docker is needed only for the `*IT` integration tests (Testcontainers PostgreSQL).

```bash
./mvnw test              # unit, slice, contract and H2 end-to-end tests — no Docker needed
./mvnw verify            # adds the PostgreSQL integration tests and the coverage report
./mvnw verify -DskipITs  # verify without Docker
```

Reports land in `target/surefire-reports`, `target/failsafe-reports` and `target/site/jacoco`.

## What is where

```
src/main/java/dev/rambally/statements
├── domain/         pure Java: Statement, DownloadLink, value objects, sealed exceptions
├── application/    use cases (one interface per driving port), driven ports, AES-GCM envelope cipher
├── adapters/in/    REST controllers, JWT principal resolver, error sanitiser, download bulkhead, dev token issuer
├── adapters/out/   JDBC repositories, filesystem storage, local KEK provider, logging notifier, token generator
└── bootstrap/      Spring wiring only: security chains, use-case beans, properties, OpenAPI, metrics
```

The dependency direction is enforced by ArchUnit (`HexagonalArchitectureTest`): the domain and application
layers compile against the JDK alone.

_More sections (security model, the hard problems and their proving tests, configuration, decisions) are
added as the remaining slices land._
