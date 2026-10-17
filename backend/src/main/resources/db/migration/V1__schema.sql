CREATE TABLE institutions (
 id VARCHAR(32) PRIMARY KEY, name VARCHAR(80) NOT NULL, kind VARCHAR(80) NOT NULL,
 color VARCHAR(7) NOT NULL, initials VARCHAR(4) NOT NULL
);
CREATE TABLE connectors (
 id VARCHAR(64) PRIMARY KEY, institution_id VARCHAR(32) NOT NULL UNIQUE REFERENCES institutions(id),
 profile VARCHAR(20) NOT NULL, latency_ms INTEGER NOT NULL CHECK(latency_ms >= 0),
 status VARCHAR(20) NOT NULL DEFAULT 'READY'
);
CREATE TABLE payments (
 id VARCHAR(36) PRIMARY KEY, session_id VARCHAR(36) NOT NULL,
 idempotency_key VARCHAR(80) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 source_id VARCHAR(32) NOT NULL REFERENCES institutions(id), destination_id VARCHAR(32) NOT NULL REFERENCES institutions(id),
 amount NUMERIC(16,2) NOT NULL CHECK(amount > 0), settled_amount NUMERIC(16,2), currency VARCHAR(3) NOT NULL,
 txid VARCHAR(35) NOT NULL UNIQUE, end_to_end_id VARCHAR(32) NOT NULL UNIQUE,
 correlation_id VARCHAR(80) NOT NULL, status VARCHAR(24) NOT NULL,
 scenario VARCHAR(40) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 simulated BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE(session_id, idempotency_key), CHECK(source_id <> destination_id)
);
CREATE INDEX payments_session_time ON payments(session_id, created_at);
CREATE TABLE payment_events (
 id VARCHAR(36) PRIMARY KEY, payment_id VARCHAR(36) NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
 event_id VARCHAR(36) NOT NULL, type VARCHAR(50) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
 detail VARCHAR(240) NOT NULL, sequence_no INTEGER NOT NULL
);
CREATE INDEX events_payment ON payment_events(payment_id, sequence_no);
CREATE TABLE connector_attempts (
 id VARCHAR(36) PRIMARY KEY, payment_id VARCHAR(36) NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
 connector_id VARCHAR(64) NOT NULL REFERENCES connectors(id), attempt_no INTEGER NOT NULL,
 response_code INTEGER NOT NULL, simulated_latency_ms INTEGER NOT NULL, outcome VARCHAR(32) NOT NULL
);
