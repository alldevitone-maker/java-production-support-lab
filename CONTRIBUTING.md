# Contributing

[Português](README.md) | [English](README.en.md)

Use JDK 17+, Maven and Node 22.12+. Before a pull request:

```bash
python3 scripts/generate-seed.py --check
npm ci --prefix frontend
npm run test --prefix frontend
npm run build --prefix frontend
mvn -B -f backend/pom.xml verify
```

Keep intentional faults explicit and deterministic. Preserve the distinction between the Java/SQL runtime and the browser demo. A network failure must never silently switch the frontend to a demo transport.

Use synthetic data only. Do not add real bank credentials, customer data or production logs. Parameterize SQL. Use an incremental Flyway migration for changes to a catalog already deployed in persistent storage.

Describe the trigger, resulting behavior and meaningful validation in a PR. The original framework-free lab is preserved in `legacy/`.
