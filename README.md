# Secure Statement Delivery

This is my submission for the Secure File Statement Delivery brief:

> Develop a system to store customer account statements as PDF files and provide secure, time-limited
> download links to customers.

It's a Spring Boot 4.1 service written in Java 21. An administrator uploads a customer's statement, and it is
encrypted before it touches the disk. The customer can then ask for a download link. Each link works once,
expires after 24 hours, and can be cancelled at any time. Everything is exposed as a REST API with Swagger UI,
and the whole thing runs in Docker.

The first two sections get it running. The rest explains how it's built and why.

## Running it

You only need Docker for this.

```bash
docker build -t secure-statements .
docker run --rm -p 127.0.0.1:8080:8080 -v statements-data:/data secure-statements
```

Then open http://localhost:8080/swagger-ui.html.

If port 8080 is taken, map a different one and tell the service, so the links it hands out point at the
right address: `-p 127.0.0.1:9090:8080 -e APP_PUBLIC_BASE_URL=http://localhost:9090`.

Out of the box the image runs in a self-contained demo mode. It uses an embedded H2 database and stores files
on the `/data` volume, so nothing else needs to be installed. It also seeds five sample statements for two
customers, `C-1001` and `C-2002`, and switches on a small development endpoint that hands out login tokens.
That endpoint is why the port is bound to `127.0.0.1` above: it should never be reachable from a network.

To run against PostgreSQL instead:

```bash
docker compose up --build
```

Compose starts PostgreSQL first and waits until it's healthy before starting the app. It works without any
extra configuration. To use your own encryption key, or a port other than 8080, set them before starting:

```bash
export APP_CRYPTO_KEK=$(openssl rand -base64 32)   # optional
export APP_PORT=9090                               # optional, defaults to 8080
docker compose up --build
```

Both demo runs use a built-in development key when `APP_CRYPTO_KEK` isn't set, and say so loudly in the log.
That's fine for trying it out. Keep the same key across restarts, though, or the existing statements can't
be decrypted.

If you have JDK 21 installed you can also run it without Docker:
`./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,dev,demo`.

## Trying it out

In Swagger:

1. Under **Development**, call `POST /dev/token` with `{"subject":"C-1001","roles":["CUSTOMER"]}`. Copy the
   token, click **Authorize** at the top of the page and paste it in.
2. `GET /api/statements` lists C-1001's statements. Pick one and copy its `statementId`.
3. `POST /api/statements/{statementId}/links` gives you a `url`. Open it in a browser tab and the PDF
   downloads, with a name like `statement-7890-2026-09.pdf` (the last four digits of the account and the
   statement's month).
4. Refresh that tab. You'll get `404 Statement not available`, because the link has already been used.
5. `GET /api/statements/{statementId}/links` now shows that link as `EXHAUSTED`. You can revoke a fresh
   link with `DELETE /api/links/{linkId}`, after which it also returns 404.
6. To upload a statement, get a token for `{"subject":"ops-admin","roles":["ADMIN"]}` and use
   `POST /api/admin/statements` with a PDF, a `customerId`, an `accountNumber` and a `period` such as `2026-05`.

If you log in as `C-2002` and try to use one of C-1001's statements, you get a 404, the same as if it didn't
exist.

There's also a script that runs this whole journey and checks each step:

```bash
scripts/smoke.sh                    # against http://localhost:8080
scripts/smoke.sh http://other:8080  # or anywhere else
```

## Building and running the tests

You need JDK 21. Docker is only needed for the PostgreSQL integration tests.

```bash
./mvnw test              # 335 unit, slice and end-to-end tests on H2, no Docker needed
./mvnw verify            # adds 27 PostgreSQL integration tests and the coverage check
./mvnw verify -DskipITs  # the full build without Docker
```

Test reports end up in `target/surefire-reports` and `target/failsafe-reports`, and the coverage report in
`target/site/jacoco/index.html`. The build fails if coverage drops below 90% of lines and 85% of branches
in the domain and application code, or 80% of lines overall. Right now the domain and application code is at
about 98% of lines and 97% of branches, and everything else at about 98% and 94%. Spring configuration
classes and the response DTOs are left out of the count. The check uses the unit and slice tests, not the
PostgreSQL integration tests.

The GitHub Actions workflow runs `./mvnw verify` and builds the image. It then runs the smoke script against
the image on its own (with a read-only filesystem and no Linux capabilities) and against the compose stack on
PostgreSQL, and checks that the image refuses to start in production mode when nothing is configured.

## The API

| Endpoint | Who can call it | What it does |
|---|---|---|
| `POST /api/admin/statements` | admins | Upload a statement PDF for a customer. |
| `GET /api/statements` | customers (and admins, for their own) | List your own statements, newest first. |
| `POST /api/statements/{id}/links` | the statement's owner | Create a download link. |
| `GET /api/statements/{id}/links` | the owner or an admin | See each link's status and download count. |
| `DELETE /api/links/{linkId}` | the owner or an admin | Revoke a link. Doing it twice is fine. |
| `GET /download/{token}` | anyone with the link | Download the PDF. |
| `POST /dev/token` | anyone, dev mode only | Get a login token for testing. |
| `/actuator/health` | anyone | Health check, used by Docker. |
| `/actuator/metrics`, `/actuator/prometheus` | admins | Metrics. Only Prometheus is exposed in production. |

When something isn't yours, the API says it doesn't exist rather than saying you aren't allowed. That way you
can't use the API to find out whether someone else's statement or link exists.

`HEAD` on a download link always returns 404 and never counts as a download. Mail scanners and download
managers often check a link with `HEAD` first, and that shouldn't use up a single-use link.

## How the code is organised

I used a hexagonal (ports and adapters) structure, kept in a single Maven module:

```
src/main/java/dev/rambally/statements
├── domain        plain Java: Statement, DownloadLink, the value objects and the business rules
├── application   the use cases, the interfaces ("ports") they need, and the encryption code
├── adapters/in   everything that calls into the application: REST controllers, request handling
├── adapters/out  everything the application calls out to: the database, file storage, keys, logging
└── bootstrap     Spring configuration that wires it all together
```

The domain and application packages have no Spring in them at all. They don't know whether statements live
on disk or in S3, or whether the database is H2 or PostgreSQL. They only see interfaces such as
`StatementRepository`, `StatementStorage` and `KeyProvider`. The adapters implement those interfaces. All the
wiring lives in the `bootstrap` package: `AdapterConfig` picks the storage, key, token and notification
implementations, the two JDBC repositories are found by component scanning, and `UseCaseConfig` builds the
use cases from whatever is there.

I didn't want this to be a convention people have to remember, so `HexagonalArchitectureTest` checks it on
every build. If someone imports Spring into the domain, or makes a controller call a service class directly
instead of going through its interface, the build fails.

I considered splitting it into separate Maven modules, which would make the boundaries physical. For a
service with six use cases that felt like a lot of build ceremony, and the architecture test gives the same
guarantee more cheaply. If the service grew into several areas I'd split it then.

Every outbound interface (storage, database, keys, audit, notifications) already has at least two
implementations, an in-memory one for tests and a real one. So swapping file storage for S3, or the local
key for a cloud key service, means writing one new class and changing one line of configuration.

## Design decisions

### Plain SQL instead of JPA

There are three tables and two main objects, and the most important operation in the whole system is a
single SQL statement that uses up a download link. I wrote the persistence with Spring's `JdbcClient` and
Flyway migrations rather than JPA. The domain objects are immutable records built straight from the query
results, so there's no separate entity layer and no mapping code.

The same SQL runs on H2 (in PostgreSQL mode) and on real PostgreSQL. I wrote the repository tests once, as
abstract "contract" tests, and run them against an in-memory fake, against H2, and against PostgreSQL in a
Testcontainer. If the fake I use in fast tests ever behaves differently from the real database, those tests
catch it.

### The link token is the password

A download link has to work without the customer being logged in, for example when it's opened from an
email. So the random part of the link is effectively a one-time password: 32 bytes from a secure random
generator.

The database only ever stores a SHA-256 hash of the token, never the token itself. So a copy of the database
is no use for downloading anything. The token only appears in three places: the API response that creates
the link, the notification sent to the customer, and the download URL. I took care that it never ends up in
logs, metrics, error messages or the audit table, and there's a test that checks the logs and every metric
after a full download.

### Every failure looks the same

If a download link is unknown, expired, revoked, already used, or the file has been tampered with, the
customer always gets the same 404 with the same body. Different messages would let anyone holding a leaked
link work out what state it's in. The real reason is still recorded, in an audit table that only the
application writes to. On PostgreSQL a database trigger stops anyone updating or deleting audit rows.

Audit writes run in their own transaction, so they survive even if the main operation is rolled back. They
also never fail the customer's request. If the audit table is unavailable, the error is logged and counted
in a metric instead.

### Using up a link safely when two requests arrive at once

Reading the count and then incrementing it would let two requests that arrive together both pass the check.
So the check and the increment happen in one SQL statement:

```sql
UPDATE download_link
   SET download_count = download_count + 1, last_downloaded_at = :now
 WHERE id = :id AND revoked_at IS NULL AND expires_at > :now AND download_count < max_downloads
```

If it updates one row, the download goes ahead. If it updates none, someone else got there first. The
database's row lock takes care of the rest. A test fires 64 threads at the same link at once, on both H2 and
PostgreSQL, and checks that only one succeeds. Another does the same over HTTP.

### Decrypt first, then use up the link

The service reads and decrypts the file before it uses up the link. If the file is missing or the
decryption key is wrong, that's the bank's fault, and it shouldn't cost the customer their one download.
The downside is that in a race several requests might decrypt the file and then all but one lose.
Statements are capped at 10 MB, so that wasted work is small.

### Not streaming the download

I'd normally stream a file download rather than loading it all into memory. Here that conflicts with the
encryption. AES-GCM can only confirm that a file hasn't been tampered with once it has read all of it. If I
streamed, a tampered file would already be half sent before the check failed, and the customer would get a
broken download instead of the clean 404.

So the service decrypts the whole statement in memory, checks it, and only then sends it. That's safe
because uploads are limited to 10 MB, and a filter caps the number of downloads in progress at 16. If more
arrive, they get a 503 with a `Retry-After` header. If statements ever got much bigger, the fix would be to
encrypt them in chunks, each with its own check. The database already records an encryption format version,
so a second format could sit alongside the first.

### Encryption at rest

Each statement is encrypted with AES-256-GCM using its own random key. That key is then encrypted with a
master key, the key-encryption key, which lives outside the database (an environment variable here, a
cloud key service in production). The encrypted file goes to storage, while the encrypted key and the other
details needed to decrypt it go in the database row. Someone who steals the files alone, or the database
alone, can't read anything.

The encryption is tied to the statement's id, so a file can't be swapped for another statement's file
without the check failing. The file's check leaves out the master key's id; only the small per-statement key
is bound to it. That means you can rotate the master key by re-encrypting those small keys, without touching
any files. A test covers this.

With the `dev` profile on, if no master key is set, the service falls back to a built-in development key and
prints a large warning. Without `dev` it refuses to start without a key, and in production it also refuses
the development key itself.

### Security configuration

There are two Spring Security filter chains. The first covers the public paths: the download URL, health
checks, Swagger, and the development token endpoint. The second covers everything else. It requires a valid
login token and ends with a rule that denies anything not explicitly allowed. That means a new endpoint is
closed until someone decides who can use it, rather than open because someone forgot.

Login tokens are standard JWTs. In development the service signs its own with an RSA key it generates at
startup. In production it checks them against the bank's identity provider, and it insists the token was
issued for this service specifically. A token meant for some other bank system is rejected. The caller's
identity always comes from the token, never from anything in the request. An admin can upload statements and
revoke links but can't create a download link for a customer, because that would hand them the customer's
password to the file.

Production startup is guarded. If the service starts with the `prod` profile and finds the development key,
no key, an `http` address instead of `https`, anything other than a PostgreSQL database, the demo data
switched on, Swagger switched on, no token audience, or no trusted load balancer, it refuses to start. It
lists everything that's wrong in one message, before it has touched the database.

### Where links point, and how customers get them

Download links are built from a configured base address (`APP_PUBLIC_BASE_URL`), never from the incoming
request's `Host` header. Otherwise someone could send a request with a forged header and get the service to
hand out links pointing at their own site.

Sending the link to the customer happens through a `NotificationPort` interface. The only implementation
here writes a log line with the token blanked out. In a real deployment it would be swapped for one that sends
an email or SMS, without touching the rest of the code.

## How I used TDD

I built this test-first, one thin slice of functionality at a time. Each slice went red, green, refactor: a
failing test for the behaviour I wanted, just enough code to pass it, then a clean-up with the tests still
green. The git history shows this. Almost every change is a pair of commits, a `test:` commit with the
failing tests and then a `feat:` or `fix:` commit that makes them pass. A few small late fixes went in as a
single commit with their test.

I worked through the slices in this order:

1. A walking skeleton: an app that starts, a health check, the security chains, the architecture test and
   the Docker build, all with nothing in them yet.
2. The domain objects and their rules, such as when a link counts as expired.
3. Encryption and token generation.
4. Uploading a statement, first against in-memory fakes and then against the real database and file system.
5. The upload endpoint over HTTP, with logins.
6. Creating links and listing statements.
7. Using up a link, including the concurrency tests.
8. The public download endpoint, and the first full end-to-end test.
9. Listing and revoking links.
10. The demo data.
11. Production hardening.
12. The coverage gate and the remaining documentation.

Doing it in that order meant there was a working, runnable service from very early on, and every slice
after that kept the build green.

Some notes on how the tests are written:

- I used hand-written fakes, such as an in-memory repository, instead of a mocking library. Tests against
  fakes describe behaviour, and they don't break every time the internals change.
- The fakes are kept honest by the contract tests mentioned above. The same tests run against each fake and
  against the real implementation.
- Time comes in as a `Clock`, so tests can fix it. That lets me check that a link is valid one millisecond
  before expiry and invalid at the moment it expires.
- The fast tests don't need Docker, so they're quick to run while working. The slower PostgreSQL tests run in
  `./mvnw verify` and in CI.
- When I found bugs later on, I wrote a failing test for each one before fixing it.

## Configuration

The main settings. All of them have defaults that work locally.

| Variable | Default | What it's for |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `h2,dev,demo` in the image | Which mode to run in. |
| `APP_DATA_DIR` | `/data` in the image | Where the H2 database and the encrypted files go. |
| `APP_PUBLIC_BASE_URL` | `http://localhost:8080` | The address used in download links. Change it if you change the port. Must be https in production. |
| `APP_PORT` | `8080` | Compose only: the port on your machine. |
| `APP_CRYPTO_KEK` | not set (dev key) | The master key, as base64 of 32 random bytes. Required in production. |
| `APP_CRYPTO_KEK_ID` | `local-kek-v1` | A name for the master key, stored with each statement to support rotation. |
| `APP_JWT_ISSUER_URI` | not set | The identity provider. Production only, and required there (or a JWK set URI instead). |
| `APP_JWT_AUDIENCE` | not set | The audience every login token must carry. Production only, and required there. |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | not set | Production only, and required there: a pattern matching the load balancer's addresses. Forwarded headers from anyone else are ignored. |
| `APP_LINK_TTL` | `PT24H` | How long a link lasts, between 1 minute and 30 days. |
| `APP_LINK_MAX_DOWNLOADS` | `1` | How many times a link can be used, between 1 and 10. |
| `APP_DOWNLOAD_MAX_CONCURRENT` | `16` | How many downloads can be in progress at once. |

The modes are `h2` or `postgres` for the database, `dev` for the token endpoint and the development key,
`demo` for the sample data, and `prod`. Swagger is on in every mode except `prod`. A production run would
look like `SPRING_PROFILES_ACTIVE=postgres,prod`, with the master key coming from a secrets manager, TLS
handled by a load balancer in front, and the identity provider settings filled in.

## What I'd do next

Things I left out to keep to the brief:

- An S3 storage adapter and a cloud key-management adapter. The interfaces are already there.
- Sending the link by email or SMS. For now the customer gets the link in the API response, and the
  `NotificationPort` implementation only logs that one was sent.
- Deleting statements, a retention policy, and a clean-up job for old links and audit rows.
- Proper master key rotation. The data model supports it, but the key provider only holds one key, so
  changing `APP_CRYPTO_KEK_ID` or the key today would make existing statements unreadable. It needs a list of
  retired keys plus a job that re-encrypts the per-statement keys.
- Chunked encryption for very large statements.
- Rate limiting at the load balancer. I didn't add it inside the service, because 256-bit tokens can't
  realistically be guessed anyway.
- A health check that tells a slow database from a dead one. Today an unreachable database gives a 503
  after three seconds, and the readiness probe covers the database and the storage folder.
- Pinning the Docker base images to exact digests and scanning them for vulnerabilities in CI. The GitHub
  actions are already pinned, and Dependabot is set up to keep everything current.

## A note on versions

I went with Spring Boot 4.1 because open-source support for the 3.5 line ended in June 2026. Spring Boot 4
moved some test annotations into new packages, renamed the Testcontainers artifacts, and switched to
Jackson 3, so some imports look different from older Spring Boot projects.
