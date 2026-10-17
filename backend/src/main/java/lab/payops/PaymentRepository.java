package lab.payops;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static lab.payops.Domain.*;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;
    public PaymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Institution> institutions() {
        return jdbc.query("SELECT i.*, c.id connector_id, c.profile, c.latency_ms, c.status connector_status " +
                        "FROM institutions i JOIN connectors c ON c.institution_id=i.id ORDER BY i.name",
                (r,n) -> new Institution(r.getString("id"), r.getString("name"), r.getString("kind"),
                        r.getString("color"), r.getString("initials"), r.getString("connector_id"),
                        r.getString("profile"), r.getInt("latency_ms"), r.getString("connector_status")));
    }
    public long count(String session) {
        return session == null ? jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class)
                : jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE session_id=?", Long.class, session);
    }
    public Optional<String> byKey(String session, String key) {
        return jdbc.query("SELECT id FROM payments WHERE session_id=? AND idempotency_key=?",
                (r,n)->r.getString(1),session,key).stream().findFirst();
    }
    public String requestHash(String id) { return jdbc.queryForObject("SELECT request_hash FROM payments WHERE id=?",String.class,id); }
    public void save(String session, String key, String hash, Payment p) {
        jdbc.update("INSERT INTO payments(id,session_id,idempotency_key,request_hash,source_id,destination_id,amount,"+
                        "settled_amount,currency,txid,end_to_end_id,correlation_id,status,scenario,created_at,simulated) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p.paymentId(),session,key,hash,p.sourceId(),p.destinationId(),p.amount(),p.settledAmount(),p.currency(),
                p.txid(),p.endToEndId(),p.correlationId(),p.status(),p.scenario(),OffsetDateTime.ofInstant(p.createdAt(),java.time.ZoneOffset.UTC),true);
        for (Event e:p.events()) jdbc.update("INSERT INTO payment_events VALUES(?,?,?,?,?,?,?)",
                java.util.UUID.randomUUID().toString(),p.paymentId(),e.eventId(),e.type(),
                OffsetDateTime.ofInstant(e.at(),java.time.ZoneOffset.UTC),e.detail(),e.sequence());
        for (Attempt a:p.attempts()) jdbc.update("INSERT INTO connector_attempts VALUES(?,?,?,?,?,?,?)",
                java.util.UUID.randomUUID().toString(),p.paymentId(),a.connectorId(),a.attempt(),a.responseCode(),a.latencyMs(),a.outcome());
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public List<Payment> list(String session) {
        return jdbc.query("SELECT * FROM payments WHERE session_id=? ORDER BY created_at DESC,id LIMIT 100",
                (r,n)->map(r),session);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public Optional<Payment> get(String session,String id) {
        return jdbc.query("SELECT * FROM payments WHERE session_id=? AND id=?",(r,n)->map(r),session,id).stream().findFirst();
    }
    private Payment map(ResultSet r) throws SQLException {
        String id=r.getString("id");
        var events=jdbc.query("SELECT * FROM payment_events WHERE payment_id=? ORDER BY sequence_no",
                (e,n)->new Event(e.getString("event_id"),e.getString("type"),instant(e,"occurred_at"),e.getString("detail"),e.getInt("sequence_no")),id);
        var attempts=jdbc.query("SELECT * FROM connector_attempts WHERE payment_id=? ORDER BY attempt_no",
                (a,n)->new Attempt(a.getString("connector_id"),a.getInt("attempt_no"),a.getInt("response_code"),a.getInt("simulated_latency_ms"),a.getString("outcome")),id);
        return new Payment(id,r.getString("source_id"),r.getString("destination_id"),r.getBigDecimal("amount"),
                r.getBigDecimal("settled_amount"),r.getString("currency"),r.getString("txid"),r.getString("end_to_end_id"),
                r.getString("correlation_id"),r.getString("status"),r.getString("scenario"),instant(r,"created_at"),events,attempts,true);
    }
    private Instant instant(ResultSet r,String field) throws SQLException { return r.getObject(field,OffsetDateTime.class).toInstant(); }
    public int reset(String session) { return jdbc.update("DELETE FROM payments WHERE session_id=?",session); }
    public int prune(Instant cutoff) { return jdbc.update("DELETE FROM payments WHERE created_at<?",OffsetDateTime.ofInstant(cutoff,java.time.ZoneOffset.UTC)); }
}
