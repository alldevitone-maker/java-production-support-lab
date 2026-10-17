# PayOps Lab

[Português](README.md) | [English](README.en.md)

![PayOps checks](https://github.com/alldevitone-maker/java-production-support-lab/actions/workflows/java.yml/badge.svg)

A hands-on L2/L3 support laboratory built with **React, Java 17, Spring Boot and SQL**. Simulate fictional Pix transfers between five institutions, inject incidents and inspect identifiers, projected status, connector attempts and event timelines.

[Online console](https://payops-java-support-lab.giovane-pereir914826.chatgpt.site) · [Architecture](docs/ARCHITECTURE.md) · [Deployment](docs/DEPLOYMENT.md) · [API](docs/API.md)

The Sites publication is an explicitly labeled **browser demo**. It does not run Java or connect to SQL. The full Spring Boot application can be deployed with the repository's Dockerfile and Render Blueprint. The UI never silently falls back from Java to the browser simulator.

## Run the full stack

Requirements: JDK 17+, Maven 3.6.3+, Node 22.12+ and npm.

```bash
git clone https://github.com/alldevitone-maker/java-production-support-lab.git
cd java-production-support-lab
sh run.sh
```

Open http://localhost:8080. React is built into the executable JAR; Spring serves the frontend and API from the same origin. H2 in-memory SQL is the default. Flyway automatically creates the schema and seeds five institutions and five connectors. Payments reset on restart.

For persistent PostgreSQL storage:

```bash
cp .env.example .env
# Choose POSTGRES_PASSWORD in .env first.
docker compose up --build
```

Alternatively set `DATABASE_URL=jdbc:postgresql://HOST:5432/DB`, `DATABASE_USERNAME` and `DATABASE_PASSWORD`. Never commit credentials.

## Exercises

The catalog includes a normal flow plus duplicate settlement, timeout after acceptance, stale status, injected amount discrepancy, missing log correlation, wrong HTTP status, empty downstream response, excessive retries, duplicate webhook, out-of-order event and connector unavailability.

Each payment carries synthetic `txid`, `endToEndId`, `paymentId` and `correlationId`. The `E00000000` participant portion is fictional and does not claim Pix/SPI interoperability. Connector latency is configured exercise data, not a live bank measurement. All five institution names are fictional; no bank calls or financial operations occur.

The backend reads the institution catalog from SQL. Connector strategies are selected by database profile, not institution names. Flyway seed data is generated from `catalog/institutions.json`, which also powers the browser demo fixture.

## API and sessions

An anonymous browser session is identified by a random UUID stored in `sessionStorage`. There is no login. API requests use `X-Lab-Session`; POST requests also use `Idempotency-Key`. Queries and resets are scoped to this session. The unique `(session_id, idempotency_key)` constraint prevents concurrent duplicate creation; reusing a key with another payload returns 409.

At most 100 operations are stored per session, with a simple global capacity of 10,000 records. Scheduled cleanup removes entries older than 24 hours. This is an educational demo with synthetic data, not production banking software.

## Checks

```bash
python3 scripts/generate-seed.py --check
npm ci --prefix frontend
npm run test --prefix frontend
npm run build --prefix frontend
mvn -B -f backend/pom.xml verify
```

GitHub Actions runs migrations and backend integration tests against both H2 and PostgreSQL, tests React and the browser simulation, and builds a runnable JAR. See [roadmap](docs/ROADMAP.md) for deployment status and remaining work.

The original framework-free Java implementation is preserved under [legacy](legacy/). [MIT license](LICENSE). Copyright 2026 Giovane.
