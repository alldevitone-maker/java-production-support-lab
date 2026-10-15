# Contributing

[Português](README.md) | [English](README.en.md)

Use JDK 17+. Run `sh scripts/smoke-test.sh` and `python3 scripts/scenario-test.py` from the repository root before opening a pull request. Both suites need port 8080 free. Keep changes focused and describe observed behavior, expected behavior and validation.

Intentional faults must remain explicit `X-Lab-Bug` scenarios. Do not silently turn new defects into exercises. Use synthetic data only. Never include credentials, real customer identifiers, payment card data or production logs.

For an issue, include Java version, operating system, scenario identifier, sanitized request/response and reproduction steps. Document limitations and avoid claiming production safety.
