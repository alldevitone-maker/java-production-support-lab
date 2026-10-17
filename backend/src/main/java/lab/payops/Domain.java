package lab.payops;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Domain {
    private Domain() {}
    public record Institution(String id, String name, String kind, String color, String initials,
                              String connectorId, String profile, int latencyMs, String connectorStatus) {}
    public record PaymentRequest(
            @NotBlank @Pattern(regexp="[a-z]{2,32}") String sourceId,
            @NotBlank @Pattern(regexp="[a-z]{2,32}") String destinationId,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer=7, fraction=2) BigDecimal amount,
            @NotBlank @Pattern(regexp="BRL") String currency,
            @NotBlank @Size(max=40) String scenario) {}
    public record Event(String eventId, String type, Instant at, String detail, int sequence) {}
    public record Attempt(String connectorId, int attempt, int responseCode, int latencyMs, String outcome) {}
    public record Payment(String paymentId, String sourceId, String destinationId, BigDecimal amount,
                          BigDecimal settledAmount, String currency, String txid, String endToEndId,
                          String correlationId, String status, String scenario, Instant createdAt,
                          List<Event> events, List<Attempt> attempts, boolean simulated) {}
    public record Outcome(Payment payment, int httpStatus, String error, boolean replayed) {}
    public record Scenario(String id, String title, String hint) {}
    public static final List<Scenario> SCENARIOS = List.of(
            new Scenario("normal", "Fluxo normal", "Origem, conector, liquidação e confirmação em sequência."),
            new Scenario("duplicate-charge", "Liquidação duplicada", "Compare eventos de liquidação para o mesmo pagamento."),
            new Scenario("timeout-after-accept", "Timeout após aceite", "A resposta é 504, mas o pagamento foi registrado. Consulte antes de reenviar."),
            new Scenario("stale-status", "Status desatualizado", "Compare o estado projetado com o evento de liquidação."),
            new Scenario("rounding-error", "Divergência de centavos", "Compare valor solicitado e valor liquidado simulado."),
            new Scenario("missing-correlation", "Correlação ausente no log", "O ID está na transação, mas falta no log do conector."),
            new Scenario("wrong-http-status", "Código HTTP incorreto", "A rejeição simulada retorna HTTP 200. Inspecione o corpo."),
            new Scenario("null-response", "Resposta vazia", "A integração não confirmou o resultado: HTTP 502 e estado UNKNOWN."),
            new Scenario("retry-storm", "Excesso de tentativas", "Observe quatro tentativas sem política de backoff."),
            new Scenario("duplicate-webhook", "Webhook duplicado", "O mesmo eventId aparece em duas entregas."),
            new Scenario("out-of-order-events", "Evento fora de ordem", "Um evento antigo chega depois da liquidação."),
            new Scenario("connector-unavailable", "Conector indisponível", "HTTP 503 na tentativa e pagamento FAILED.")
    );
}
