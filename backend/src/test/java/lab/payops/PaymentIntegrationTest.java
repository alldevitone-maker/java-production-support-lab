package lab.payops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentService service;
    @Autowired PaymentRepository repo;
    private String session(){return UUID.randomUUID().toString();}
    private String body(String scenario,String amount){return "{\"sourceId\":\"aurora\",\"destinationId\":\"vertice\",\"amount\":"+amount+",\"currency\":\"BRL\",\"scenario\":\""+scenario+"\"}";}
    private JsonNode create(String session,String key,String scenario,int http)throws Exception {
        return json.readTree(mvc.perform(post("/api/payments").header("X-Lab-Session",session).header("Idempotency-Key",key)
                .contentType("application/json").content(body(scenario,"49.90"))).andExpect(status().is(http)).andReturn().getResponse().getContentAsString());
    }
    @Test void sqlCatalogContainsExactlyFiveConfiguredInstitutions()throws Exception{
        mvc.perform(get("/api/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$.runtime").value("SPRING_BOOT_SQL"))
                .andExpect(jsonPath("$.institutions.length()").value(5)).andExpect(jsonPath("$.scenarios.length()").value(12));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connectors",Integer.class)).isEqualTo(5);
    }
    @ParameterizedTest
    @CsvSource({"normal,201,SETTLED","duplicate-charge,201,SETTLED","timeout-after-accept,504,SETTLED",
            "stale-status,201,PENDING","rounding-error,201,REVIEW","missing-correlation,201,SETTLED",
            "wrong-http-status,200,REJECTED","null-response,502,UNKNOWN","retry-storm,201,SETTLED",
            "duplicate-webhook,201,SETTLED","out-of-order-events,201,PENDING","connector-unavailable,503,FAILED"})
    void scenarioPersistsExpectedStateAndIdentifiers(String scenario,int http,String expected)throws Exception{
        String session=session();JsonNode result=create(session,"key",scenario,http);JsonNode p=result.get("payment");
        assertThat(p.get("status").asText()).isEqualTo(expected);
        assertThat(p.get("txid").asText()).matches("SIMTX[a-f0-9]{27}");
        assertThat(p.get("endToEndId").asText()).matches("E00000000[0-9]{12}[a-f0-9]{11}");
        assertThat(p.get("simulated").asBoolean()).isTrue();
        String id=p.get("paymentId").asText();
        mvc.perform(get("/api/payments/"+id).header("X-Lab-Session",session)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_events WHERE payment_id=?",Integer.class,id)).isPositive();
        if(scenario.equals("retry-storm"))assertThat(p.get("attempts").size()).isEqualTo(4);
        if(scenario.equals("rounding-error"))assertThat(p.get("settledAmount").decimalValue()).isNotEqualByComparingTo(p.get("amount").decimalValue());
        if(scenario.equals("duplicate-webhook"))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT event_id FROM payment_events WHERE payment_id=? GROUP BY event_id HAVING COUNT(*)>1) repeated",Integer.class,id)).isEqualTo(1);
    }
    @Test void idempotencyReplaysAndRejectsDifferentContent()throws Exception{
        String session=session();String id=create(session,"replay","normal",201).at("/payment/paymentId").asText();
        JsonNode replay=create(session,"replay","normal",200);
        assertThat(replay.at("/payment/paymentId").asText()).isEqualTo(id);assertThat(replay.get("replayed").asBoolean()).isTrue();
        mvc.perform(post("/api/payments").header("X-Lab-Session",session).header("Idempotency-Key","replay")
                .contentType("application/json").content(body("normal","10.00"))).andExpect(status().isConflict());
        assertThat(repo.count(session)).isEqualTo(1);
    }
    @Test void concurrentRequestsCreateExactlyOnePayment()throws Exception{
        String session=session();ExecutorService pool=Executors.newFixedThreadPool(8);CountDownLatch start=new CountDownLatch(1);
        try{
            var jobs=IntStream.range(0,8).mapToObj(n->pool.submit(()->{start.await();return service.create(session,"same","corr-test",new Domain.PaymentRequest("aurora","vertice",new BigDecimal("10.00"),"BRL","normal")).payment().paymentId();})).toList();
            start.countDown();var ids=new java.util.HashSet<String>();for(var job:jobs)ids.add(job.get(20,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);assertThat(repo.count(session)).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void sessionsCannotReadOrClearEachOthersPayments()throws Exception{
        String a=session(),b=session();String id=create(a,"same","normal",201).at("/payment/paymentId").asText();
        create(b,"same","normal",201);
        mvc.perform(get("/api/payments/"+id).header("X-Lab-Session",b)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/payments").header("X-Lab-Session",b)).andExpect(status().isOk());
        assertThat(repo.count(a)).isEqualTo(1);assertThat(repo.count(b)).isZero();
    }
    @ParameterizedTest @CsvSource({"0","-1","1000000.01","1.005"})
    void invalidAmountsAreRejectedWithoutWrites(String amount)throws Exception{
        String session=session();mvc.perform(post("/api/payments").header("X-Lab-Session",session).header("Idempotency-Key","invalid")
                .contentType("application/json").content(body("normal",amount))).andExpect(status().isBadRequest());
        assertThat(repo.count(session)).isZero();
    }
    @Test void sameBankAndUnknownBankAreRejected()throws Exception{
        for(String destination:new String[]{"aurora","unknown"})mvc.perform(post("/api/payments").header("X-Lab-Session",session()).header("Idempotency-Key","invalid")
                .contentType("application/json").content(body("normal","10").replace("vertice",destination))).andExpect(status().isBadRequest());
    }
}
