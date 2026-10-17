-- 1. Suspected duplicate capture / repeated attempts.
SELECT p.payment_id, p.correlation_id, p.idempotency_key, count(a.attempt_id) AS attempts,
       p.amount, p.status
FROM payments p LEFT JOIN payment_attempts a USING (payment_id)
GROUP BY p.payment_id, p.correlation_id, p.idempotency_key, p.amount, p.status
HAVING count(a.attempt_id) > 1
ORDER BY attempts DESC;

-- 2. Same idempotency key reused across different payment IDs.
SELECT idempotency_key, count(DISTINCT payment_id) AS payment_count,
       array_agg(payment_id ORDER BY created_at) AS payment_ids
FROM payments
GROUP BY idempotency_key
HAVING count(DISTINCT payment_id) > 1;

-- 3. Payments stuck in intermediate states for more than five minutes.
SELECT payment_id, status, correlation_id, created_at
FROM payments
WHERE status IN ('PENDING', 'PROCESSING', 'UNKNOWN')
  AND created_at < now() - interval '5 minutes'
ORDER BY created_at;

-- 4. Duplicate webhook deliveries; unique event identity should be idempotent.
SELECT event_id, payment_id, count(*) AS deliveries,
       min(received_at) AS first_seen, max(received_at) AS last_seen
FROM webhook_events
GROUP BY event_id, payment_id
HAVING count(*) > 1;

-- 5. Timeline for one incident. Replace the parameter with a payment ID.
SELECT p.payment_id, p.correlation_id, p.status, p.amount,
       a.attempt_number, a.outcome, a.created_at AS attempt_at,
       w.event_type, w.received_at AS webhook_at, w.processed_at
FROM payments p
LEFT JOIN payment_attempts a USING (payment_id)
LEFT JOIN webhook_events w USING (payment_id)
WHERE p.payment_id = :payment_id
ORDER BY coalesce(a.created_at, w.received_at), a.attempt_number;
