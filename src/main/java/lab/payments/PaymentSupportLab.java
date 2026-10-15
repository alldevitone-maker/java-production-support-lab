package lab.payments;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** Local-only payment support exercise with controlled fault injection. */
public final class PaymentSupportLab {
    private static final Map<String, Payment> PAYMENTS = new ConcurrentHashMap<>();
    private static final Map<String, String> IDEMPOTENCY = new ConcurrentHashMap<>();
    private static final Map<String, String> BUGS = Map.ofEntries(
            Map.entry("duplicate-charge", "Duplicate simulated payment or capture"),
            Map.entry("timeout-after-accept", "Accepted request reported as timeout"),
            Map.entry("stale-status", "Authorization event does not update projected status"),
            Map.entry("rounding-error", "Amount rounded with binary floating point"),
            Map.entry("missing-correlation", "Correlation ID omitted from application log"),
            Map.entry("wrong-http-status", "Validation error mapped to HTTP 200"),
            Map.entry("null-response", "Empty downstream response"),
            Map.entry("retry-storm", "Retry policy repeats downstream request"),
            Map.entry("duplicate-webhook", "Webhook event delivered twice"),
            Map.entry("out-of-order-events", "Older event overwrites latest projected state")
    );

    private PaymentSupportLab() {}

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/", PaymentSupportLab::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("{\"level\":\"INFO\",\"service\":\"payment-support-lab\",\"event\":\"started\",\"port\":8080}");
    }

    private static void handle(HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath();
        String method = x.getRequestMethod();
        String correlation = x.getRequestHeaders().getFirst("X-Correlation-Id");
        if (correlation == null || correlation.isBlank()) correlation = "corr-" + UUID.randomUUID();
        String bug = x.getRequestHeaders().getFirst("X-Lab-Bug");
        String logCorrelation = "missing-correlation".equals(bug) ? "" : correlation;
        log("request", logCorrelation, "method=" + method + " path=" + path + (bug == null ? "" : " bug=" + bug));

        try {
            if ("GET".equals(method) && "/".equals(path)) {
                String html = Files.readString(Path.of("ui/index.html"), StandardCharsets.UTF_8);
                replyHtml(x, 200, html);
            } else if ("GET".equals(method) && "/health".equals(path)) {
                reply(x, 200, "{\"status\":\"UP\",\"service\":\"payment-support-lab\"}");
            } else if ("GET".equals(method) && "/admin/scenarios".equals(path)) {
                reply(x, 200, json(BUGS));
            } else if ("POST".equals(method) && "/admin/reset".equals(path)) {
                PAYMENTS.clear(); IDEMPOTENCY.clear();
                reply(x, 200, "{\"reset\":true}");
            } else if ("POST".equals(method) && "/payments".equals(path)) {
                createPayment(x, correlation, logCorrelation, bug);
            } else if ("GET".equals(method) && "/payments".equals(path)) {
                listPayments(x);
            } else if ("GET".equals(method) && path.startsWith("/payments/")) {
                getPayment(x, path.substring("/payments/".length()));
            } else {
                reply(x, 404, "{\"error\":\"NOT_FOUND\",\"correlationId\":" + quote(correlation) + "}");
            }
        } catch (IllegalArgumentException e) {
            int status = "wrong-http-status".equals(bug) ? 200 : 400;
            reply(x, status, "{\"error\":\"INVALID_REQUEST\",\"message\":" + quote(e.getMessage()) + ",\"correlationId\":" + quote(correlation) + "}");
        } catch (Exception e) {
            log("error", logCorrelation, "type=" + e.getClass().getSimpleName());
            reply(x, 500, "{\"error\":\"INTERNAL_ERROR\",\"correlationId\":" + quote(correlation) + "}");
        }
    }

    private static void createPayment(HttpExchange x, String correlation, String logCorrelation, String bug) throws IOException {
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String customer = field(body, "customerId");
        String currency = field(body, "currency");
        String amountText = field(body, "amount");
        String method = field(body, "paymentMethod");
        String provider = field(body, "provider");
        if (customer == null || customer.isBlank()) throw new IllegalArgumentException("customerId is required");
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a 3-letter uppercase code");
        if (amountText == null) throw new IllegalArgumentException("amount is required");
        BigDecimal amount;
        try { amount = new BigDecimal(amountText); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("amount must be numeric"); }
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        if (method == null) method = "PIX";
        if (!List.of("PIX", "CREDIT_CARD", "DEBIT_CARD").contains(method)) throw new IllegalArgumentException("paymentMethod must be PIX, CREDIT_CARD or DEBIT_CARD");
        if (provider == null || provider.isBlank()) provider = "LAB_ACQUIRER";

        if ("rounding-error".equals(bug)) {
            amount = new BigDecimal(amount.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        } else {
            amount = amount.setScale(2, RoundingMode.HALF_UP);
        }
        String key = x.getRequestHeaders().getFirst("Idempotency-Key");
        boolean callerProvidedKey = key != null && !key.isBlank();
        if (!callerProvidedKey) key = "no-key-" + UUID.randomUUID();
        String priorPaymentId = IDEMPOTENCY.get(key);
        if (callerProvidedKey && priorPaymentId != null && !"duplicate-charge".equals(bug)) {
            Payment prior = PAYMENTS.get(priorPaymentId);
            if (prior != null) {
                log("idempotent_replay", logCorrelation, "paymentId=" + priorPaymentId);
                reply(x, 200, prior.toJson());
                return;
            }
        }
        String paymentId = "pay-" + UUID.randomUUID().toString().substring(0, 8);
        String initialStatus = "PIX".equals(method) ? "SETTLED" : "AUTHORIZED";
        Payment p = new Payment(paymentId, customer, amount, currency, initialStatus, correlation, key, method, provider);
        p.addEvent("PAYMENT_ACCEPTED", Instant.now());
        if ("PIX".equals(method)) {
            p.addEvent("PIX_RECEIVED", Instant.now());
            p.addEvent("PIX_SETTLED", Instant.now());
        } else {
            p.addEvent("PAYMENT_AUTHORIZED", Instant.now());
        }
        if ("stale-status".equals(bug)) p.status = "PENDING";
        if ("null-response".equals(bug)) { p.status = "UNKNOWN"; p.addEvent("DOWNSTREAM_EMPTY_RESPONSE", Instant.now()); }

        boolean pix = "PIX".equals(method);
        int captures = pix ? 0 : 1;
        int attempts = 1;
        if ("duplicate-charge".equals(bug)) {
            if (pix) p.addEvent("PIX_SETTLED_DUPLICATE", Instant.now());
            else captures = 2;
        }
        if ("retry-storm".equals(bug)) {
            attempts = 4;
            if (!pix) captures = 4;
        }
        if ("out-of-order-events".equals(bug)) {
            p.status = "PENDING";
            p.events.add(0, new Event("PAYMENT_PENDING", Instant.now().minusSeconds(60)));
        }
        p.captures = captures;
        p.attempts = attempts;
        if (!pix) p.addEvent("CAPTURE_ATTEMPTED", Instant.now());
        if ("duplicate-webhook".equals(bug)) p.addEvent("WEBHOOK_DELIVERED", Instant.now());
        p.addEvent("WEBHOOK_DELIVERED", Instant.now());
        PAYMENTS.put(paymentId, p);
        IDEMPOTENCY.put(key, paymentId);
        log("payment_created", logCorrelation, "paymentId=" + paymentId + " status=" + p.status + " attempts=" + attempts + " captures=" + captures);

        if ("timeout-after-accept".equals(bug)) {
            reply(x, 504, "{\"error\":\"GATEWAY_TIMEOUT\",\"correlationId\":" + quote(correlation) + "}");
            return;
        }
        if ("null-response".equals(bug)) {
            reply(x, 502, "{\"error\":\"EMPTY_DOWNSTREAM_RESPONSE\",\"correlationId\":" + quote(correlation) + ",\"paymentId\":" + quote(paymentId) + "}");
            return;
        }
        reply(x, 201, p.toJson());
    }

    private static void getPayment(HttpExchange x, String id) throws IOException {
        Payment p = PAYMENTS.get(id);
        if (p == null) { reply(x, 404, "{\"error\":\"PAYMENT_NOT_FOUND\"}"); return; }
        reply(x, 200, p.toJson());
    }

    private static void listPayments(HttpExchange x) throws IOException {
        List<Payment> items = PAYMENTS.values().stream()
                .sorted(Comparator.comparing((Payment p) -> p.createdAt).reversed())
                .toList();
        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) body.append(',');
            body.append(items.get(i).toJson());
        }
        reply(x, 200, body.append(']').toString());
    }

    private static void log(String event, String correlation, String detail) {
        System.out.println("{\"timestamp\":" + quote(Instant.now().toString()) + ",\"level\":\"INFO\",\"service\":\"payment-api\",\"event\":" + quote(event) + ",\"correlationId\":" + quote(correlation == null ? "" : correlation) + ",\"detail\":" + quote(detail) + "}");
    }

    private static String field(String json, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(name) + "\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([-+]?[0-9]+(?:\\.[0-9]+)?))").matcher(json);
        return m.find() ? (m.group(1) != null ? m.group(1) : m.group(2)) : null;
    }

    private static void reply(HttpExchange x, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(status, bytes.length);
        try (var out = x.getResponseBody()) { out.write(bytes); }
    }

    private static void replyHtml(HttpExchange x, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(status, bytes.length);
        try (var out = x.getResponseBody()) { out.write(bytes); }
    }

    private static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String json(Map<String, String> map) {
        StringBuilder b = new StringBuilder("{");
        map.forEach((k, v) -> { if (b.length() > 1) b.append(','); b.append(quote(k)).append(':').append(quote(v)); });
        return b.append('}').toString();
    }

    private static final class Payment {
        final String id, customer, currency, correlationId, idempotencyKey;
        final BigDecimal amount;
        final String paymentMethod, provider;
        final Instant createdAt = Instant.now();
        String status;
        int captures, attempts = 1;
        final List<Event> events = new ArrayList<>();
        Payment(String id, String customer, BigDecimal amount, String currency, String status, String correlationId, String idempotencyKey, String paymentMethod, String provider) {
            this.id=id; this.customer=customer; this.amount=amount; this.currency=currency; this.status=status; this.correlationId=correlationId; this.idempotencyKey=idempotencyKey; this.paymentMethod=paymentMethod; this.provider=provider;
        }
        void addEvent(String type, Instant at) { events.add(new Event(type, at)); }
        String toJson() {
            StringBuilder es = new StringBuilder("[");
            for (int i=0; i<events.size(); i++) { Event e=events.get(i); if (i>0) es.append(','); es.append("{\"type\":").append(quote(e.type)).append(",\"at\":").append(quote(e.at.toString())).append('}'); }
            return "{\"paymentId\":"+quote(id)+",\"customerId\":"+quote(customer)+",\"amount\":"+amount.toPlainString()+",\"currency\":"+quote(currency)+",\"status\":"+quote(status)+",\"paymentMethod\":"+quote(paymentMethod)+",\"provider\":"+quote(provider)+",\"createdAt\":"+quote(createdAt.toString())+",\"correlationId\":"+quote(correlationId)+",\"idempotencyKey\":"+quote(idempotencyKey)+",\"attempts\":"+attempts+",\"captures\":"+captures+",\"events\":"+es.append("]}");
        }
    }
    private record Event(String type, Instant at) {}
}
