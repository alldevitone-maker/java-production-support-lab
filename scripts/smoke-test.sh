#!/usr/bin/env sh
set -eu
mkdir -p out
javac -d out src/main/java/lab/payments/*.java
java -cp out lab.payments.PaymentSupportLab > /tmp/java-production-support-lab.log 2>&1 &
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true' EXIT INT TERM

attempt=0
until curl -fsS http://127.0.0.1:8080/health >/dev/null; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 20 ]; then
    cat /tmp/java-production-support-lab.log
    exit 1
  fi
  sleep 1
done

curl -fsS http://127.0.0.1:8080/ | grep -q 'Console de operações'

normal=$(curl -fsS -X POST http://127.0.0.1:8080/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: smoke-normal' \
  -d '{"customerId":"test-customer","amount":12.34,"currency":"BRL"}')
printf '%s' "$normal" | grep -q '"status":"SETTLED"'
printf '%s' "$normal" | grep -q '"amount":12.34'
printf '%s' "$normal" | grep -q '"paymentMethod":"PIX"'

first=$(curl -fsS -X POST http://127.0.0.1:8080/payments \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: smoke-idem' \
  -d '{"customerId":"test-customer","amount":7.00,"currency":"BRL"}')
replay=$(curl -fsS -X POST http://127.0.0.1:8080/payments \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: smoke-idem' \
  -d '{"customerId":"test-customer","amount":7.00,"currency":"BRL"}')
first_id=$(printf '%s' "$first" | sed -n 's/.*"paymentId":"\([^"]*\)".*/\1/p')
replay_id=$(printf '%s' "$replay" | sed -n 's/.*"paymentId":"\([^"]*\)".*/\1/p')
[ -n "$first_id" ] && [ "$first_id" = "$replay_id" ]

bug=$(curl -fsS -X POST http://127.0.0.1:8080/payments \
  -H 'Content-Type: application/json' -H 'X-Lab-Bug: duplicate-charge' \
  -d '{"customerId":"test-customer","amount":8.50,"currency":"BRL","paymentMethod":"CREDIT_CARD"}')
printf '%s' "$bug" | grep -q '"captures":2'

curl -fsS http://127.0.0.1:8080/admin/scenarios | grep -q 'out-of-order-events'
printf '%s\n' 'Smoke checks passed.'
