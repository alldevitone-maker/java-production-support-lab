# Java Production Support Lab

[Português](README.md) | [English](README.en.md)

![Java smoke checks](https://github.com/alldevitone-maker/java-production-support-lab/actions/workflows/java.yml/badge.svg)

A hands-on L2/L3 production support lab for a fictional payments API. Practice incident triage, correlation IDs, idempotency, event timelines and SQL investigations with controlled fault injection.

Java 17, standard library only. In-memory data. No real payments or provider integrations. The operations console is currently in Portuguese.

## Quick start

Requirements: JDK 17 or newer. The smoke checks also require a POSIX shell and curl.

```bash
git clone https://github.com/alldevitone-maker/java-production-support-lab.git
cd java-production-support-lab
sh run.sh
```

Open http://localhost:8080 to use the console. Run commands from the repository root because the server reads `ui/index.html` from that directory. On Windows, use the manual compilation commands below or Git Bash for shell scripts.

```bash
mkdir -p out
javac -d out src/main/java/lab/payments/*.java
java -cp out lab.payments.PaymentSupportLab
```

## Exercise a payment

```bash
curl -i -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-001' \
  -H 'Idempotency-Key: order-1001' \
  -d '{"customerId":"cust-42","amount":19.99,"currency":"BRL","paymentMethod":"PIX"}'
```

Use `PIX`, `CREDIT_CARD` or `DEBIT_CARD` as the payment method. Repeat a sequential request with the same idempotency key to retrieve the original payment. To inject a fault, add `X-Lab-Bug` with one of the identifiers below.

| Scenario | Investigation focus |
|---|---|
| `duplicate-charge` | Repeated simulated settlement or card capture |
| `timeout-after-accept` | A 504 response despite an accepted payment |
| `stale-status` | Events and projected status disagree |
| `rounding-error` | Binary floating-point conversion changes rounding |
| `missing-correlation` | Correlation ID missing from logs |
| `wrong-http-status` | Invalid request incorrectly returns HTTP 200 |
| `null-response` | Empty downstream result mapped to HTTP 502 |
| `retry-storm` | Excessive downstream attempts |
| `duplicate-webhook` | Duplicate webhook events |
| `out-of-order-events` | An older event overwrites the projected status |

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Operations console |
| GET | `/health` | Liveness |
| GET | `/payments` | List session payments |
| POST | `/payments` | Create a simulated payment |
| GET | `/payments/{id}` | Payment details and events |
| GET | `/admin/scenarios` | Available fault identifiers |
| POST | `/admin/reset` | Clear in-memory exercises |

## Support runbook

1. Capture timestamp, endpoint, HTTP status, payment ID and correlation ID.
2. Query existing payments before retrying: a timeout does not prove rejection.
3. Compare event timestamps with the projected state and identify duplicate events.
4. Establish impact, scope and affected time window.
5. Escalate with sanitized evidence, reproduction steps and mitigation attempts.
6. Record root cause, fix, prevention and follow-up work.

## Validation

```bash
sh scripts/smoke-test.sh
python3 scripts/scenario-test.py
```

The scenario suite starts its own Java server. Do not run another instance on port 8080 during tests. GitHub Actions compiles Java 17 and runs both suites on pushes and pull requests.

## Architecture and limits

See [architecture](docs/ARCHITECTURE.md), [contribution guide](CONTRIBUTING.md) and [roadmap](docs/ROADMAP.md).

The server binds to `127.0.0.1:8080`. Data resets when the process exits. SQL files are PostgreSQL study examples and are not connected to the API. The JSON parser is deliberately minimal and does not implement the complete JSON specification. Idempotency is sequential and is not an atomic guarantee under concurrent requests. No authentication, real provider sandbox, live charges, production persistence or production readiness is claimed.

External documentation linked in the console provides conceptual background only. Provider names are illustrative labels, not active integrations or endorsements.

## License

[MIT](LICENSE). Copyright 2026 Giovane.
