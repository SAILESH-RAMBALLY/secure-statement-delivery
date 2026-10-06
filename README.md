# Secure Statement Delivery

A Spring Boot service that stores customer account statements as encrypted PDF files and issues
secure, time-limited, single-use download links to customers.

> Brief: "Develop a system to store customer account statements as PDF files and provide secure,
> time-limited download links to customers."

## Build

Requires JDK 21 and Docker (for the integration tests).

```bash
./mvnw verify
```

## Run

```bash
docker build -t secure-statements .
docker run --rm -p 8080:8080 -v statements-data:/data secure-statements
```

Then open <http://localhost:8080/actuator/health>.

## Test

```bash
./mvnw test              # unit, slice and H2 end-to-end tests (no Docker needed)
./mvnw verify            # adds Testcontainers (PostgreSQL) integration tests
./mvnw verify -DskipITs  # skip the Docker-backed integration tests
```

_This README grows with each implementation slice._
