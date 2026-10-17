package lab.payops;

import org.springframework.stereotype.Component;
import java.util.Map;
import static lab.payops.Domain.*;

/** Strategies are selected from SQL configuration, never from institution names. */
@Component
public class ConnectorRegistry {
    public interface Connector { Attempt simulate(Institution institution, int attempt, String scenario); }
    private static class ConfiguredConnector implements Connector {
        private final int overhead;
        ConfiguredConnector(int overhead) { this.overhead = overhead; }
        public Attempt simulate(Institution institution, int attempt, String scenario) {
            int code = "connector-unavailable".equals(scenario) ? 503
                    : "timeout-after-accept".equals(scenario) ? 504
                    : "null-response".equals(scenario) ? 502 : 200;
            return new Attempt(institution.connectorId(), attempt, code,
                    institution.latencyMs() + overhead, code == 200 ? "ACCEPTED" : "SIMULATED_FAILURE");
        }
    }
    private final Map<String, Connector> strategies = Map.of(
            "FAST", new ConfiguredConnector(0), "STANDARD", new ConfiguredConnector(30),
            "SLOW", new ConfiguredConnector(120));
    public Attempt simulate(Institution institution, int attempt, String scenario) {
        Connector connector = strategies.get(institution.profile());
        if (connector == null) throw new IllegalStateException("Unsupported connector profile");
        return connector.simulate(institution, attempt, scenario);
    }
}
