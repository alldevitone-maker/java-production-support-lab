package lab.payops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.UUID;
import static lab.payops.Domain.*;

@Service
public class PaymentService {
    private static final Logger LOG=LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository repo;
    private final ConnectorRegistry connectors;
    private final TransactionTemplate transactions;
    private final int sessionLimit,globalLimit;
    private final long retentionHours;
    public PaymentService(PaymentRepository repo, ConnectorRegistry connectors, TransactionTemplate transactions,
                          @Value("${payops.session-limit}") int sessionLimit,
                          @Value("${payops.global-limit}") int globalLimit,
                          @Value("${payops.retention-hours}") long retentionHours) {
        this.repo=repo;this.connectors=connectors;this.transactions=transactions;
        this.sessionLimit=sessionLimit;this.globalLimit=globalLimit;this.retentionHours=retentionHours;
    }
    /** Database uniqueness is the final guard against concurrent duplicate requests. */
    public Outcome create(String session,String key,String correlation,PaymentRequest request) {
        if (request.sourceId().equals(request.destinationId())) throw bad("Escolha instituições diferentes.");
        if (SCENARIOS.stream().noneMatch(s->s.id().equals(request.scenario()))) throw bad("Cenário desconhecido.");
        var institutions=repo.institutions();
        if(institutions.stream().noneMatch(i->i.id().equals(request.sourceId()))) throw bad("Origem desconhecida.");
        Institution target=institutions.stream().filter(i->i.id().equals(request.destinationId())).findFirst().orElseThrow(()->bad("Destino desconhecido."));
        String hash=fingerprint(request);
        var prior=repo.byKey(session,key);
        if(prior.isPresent()) return replay(session,prior.get(),hash);
        try {
            return transactions.execute(tx->{
                if(repo.count(session)>=sessionLimit)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Limite de 100 simulações por sessão. Limpe sua sessão para continuar.");
                if(repo.count(null)>=globalLimit)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Capacidade do laboratório atingida. Tente mais tarde.");
                Outcome outcome=simulate(request,target,correlation);
                repo.save(session,key,hash,outcome.payment());
                return outcome;
            });
        } catch(DuplicateKeyException e) {
            return repo.byKey(session,key).map(id->replay(session,id,hash)).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Identificador em conflito. Reenvie com nova chave."));
        }
    }
    private Outcome replay(String session,String id,String hash) {
        if(!repo.requestHash(id).equals(hash))throw new ResponseStatusException(HttpStatus.CONFLICT,"Chave de idempotência já usada com outro conteúdo.");
        return new Outcome(repo.get(session,id).orElseThrow(),200,null,true);
    }
    private Outcome simulate(PaymentRequest r,Institution target,String correlation) {
        String scenario=r.scenario();Instant now=Instant.now();
        String id=UUID.randomUUID().toString();
        String txid="SIMTX"+random().substring(0,27);
        // Reserved fictional participant portion; not a live Pix/SPI identifier.
        String endToEnd="E00000000"+DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC).format(now)+random().substring(0,11);
        var events=new ArrayList<Event>();var attempts=new ArrayList<Attempt>();
        add(events,"PAYMENT_ACCEPTED",now,"Solicitação fictícia aceita pelo motor Java.");
        int count="retry-storm".equals(scenario)?4:1;
        for(int n=1;n<=count;n++)attempts.add(connectors.simulate(target,n,scenario));
        String status="SETTLED",error=null;int http=201;
        BigDecimal amount=r.amount().setScale(2,RoundingMode.UNNECESSARY),settled=amount;
        if("connector-unavailable".equals(scenario)) {status="FAILED";settled=null;error="CONNECTOR_UNAVAILABLE";http=503;add(events,"CONNECTOR_UNAVAILABLE",now.plusMillis(1),"Conector simulado indisponível.");}
        else if("null-response".equals(scenario)){status="UNKNOWN";settled=null;error="EMPTY_DOWNSTREAM_RESPONSE";http=502;add(events,"DOWNSTREAM_EMPTY_RESPONSE",now.plusMillis(1),"Sem confirmação de resultado; não reenviar sem consultar.");}
        else if("wrong-http-status".equals(scenario)){status="REJECTED";settled=null;error="SIMULATED_REJECTION";http=200;add(events,"PAYMENT_REJECTED",now.plusMillis(1),"Erro intencional de contrato: rejeição com HTTP 200.");}
        else {
            add(events,"CONNECTOR_ACCEPTED",now.plusMillis(1),target.connectorId()+": resposta simulada; latência não representa medição real.");
            add(events,"PIX_SETTLED",now.plusMillis(2),"Liquidação exclusivamente fictícia.");
            if("duplicate-charge".equals(scenario)) add(events,"PIX_SETTLED",now.plusMillis(3),"Segunda liquidação intencional para o mesmo pagamento.");
            if("stale-status".equals(scenario))status="PENDING";
            if("out-of-order-events".equals(scenario)){status="PENDING";add(events,"PAYMENT_PENDING",now.minusSeconds(60),"Evento antigo aplicado após liquidação: falha de projeção.");}
            if("rounding-error".equals(scenario)){
                settled=amount.compareTo(new BigDecimal("0.01"))>0?amount.subtract(new BigDecimal("0.01")):amount.add(new BigDecimal("0.01"));
                status="REVIEW";add(events,"AMOUNT_DIVERGENCE",now.plusMillis(4),"Solicitado "+amount+"; liquidado simulado "+settled+". Discrepância injetada de um centavo.");
            }
            String eventId=UUID.randomUUID().toString();
            events.add(new Event(eventId,"WEBHOOK_DELIVERED",now.plusMillis(5),"Confirmação simulada.",events.size()+1));
            if("duplicate-webhook".equals(scenario))events.add(new Event(eventId,"WEBHOOK_DELIVERED",now.plusMillis(6),"Entrega repetida com o mesmo eventId.",events.size()+1));
            if("timeout-after-accept".equals(scenario)){http=504;error="GATEWAY_TIMEOUT";}
        }
        LOG.info("event=connector_result correlationId={} paymentId={} scenario={} simulated=true",
                "missing-correlation".equals(scenario)?"":correlation,id,scenario);
        if("missing-correlation".equals(scenario))add(events,"LOG_CORRELATION_MISSING",now.plusMillis(7),"Log do conector emitido sem correlationId; a transação preserva o ID.");
        return new Outcome(new Payment(id,r.sourceId(),r.destinationId(),amount,settled,r.currency(),txid,endToEnd,correlation,status,scenario,now,events,attempts,true),http,error,false);
    }
    private static void add(ArrayList<Event> events,String type,Instant at,String detail){events.add(new Event(UUID.randomUUID().toString(),type,at,detail,events.size()+1));}
    private static String random(){return UUID.randomUUID().toString().replace("-","");}
    private String fingerprint(PaymentRequest r){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((r.sourceId()+"|"+r.destinationId()+"|"+r.amount().setScale(2)+"|"+r.currency()+"|"+r.scenario()).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    @Scheduled(fixedDelay=3600000)
    public void expire(){repo.prune(Instant.now().minusSeconds(retentionHours*3600));}
}
