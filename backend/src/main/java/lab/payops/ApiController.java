package lab.payops;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static lab.payops.Domain.*;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final PaymentRepository repo;private final PaymentService service;
    public ApiController(PaymentRepository repo,PaymentService service){this.repo=repo;this.service=service;}
    @GetMapping("/catalog")
    public Map<String,Object> catalog(){return Map.of("institutions",repo.institutions(),"scenarios",SCENARIOS,"runtime","SPRING_BOOT_SQL","simulated",true);}
    @GetMapping("/payments")
    public List<Payment> list(@RequestHeader("X-Lab-Session") String session){return repo.list(session(session));}
    @GetMapping("/payments/{id}")
    public Payment get(@RequestHeader("X-Lab-Session") String session,@PathVariable String id){return repo.get(session(session),id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Pagamento não encontrado nesta sessão."));}
    @PostMapping("/payments")
    public ResponseEntity<?> create(@RequestHeader("X-Lab-Session") String session,
                                    @RequestHeader("Idempotency-Key") String key,
                                    @RequestHeader(value="X-Correlation-Id",required=false) String correlation,
                                    @Valid @RequestBody PaymentRequest request){
        if(!key.matches("[a-zA-Z0-9_-]{1,80}"))throw new ResponseStatusException(BAD_REQUEST,"Chave idempotente inválida.");
        if(correlation==null)correlation="corr-"+UUID.randomUUID();
        if(!correlation.matches("[a-zA-Z0-9_-]{1,80}"))throw new ResponseStatusException(BAD_REQUEST,"Correlation ID inválido.");
        var result=service.create(session(session),key,correlation,request);
        var body=new java.util.LinkedHashMap<String,Object>();body.put("payment",result.payment());body.put("replayed",result.replayed());body.put("simulated",true);
        if(result.error()!=null)body.put("error",result.error());
        return ResponseEntity.status(result.httpStatus()).header("X-Correlation-Id",correlation).body(body);
    }
    @DeleteMapping("/payments")
    public Map<String,Object> reset(@RequestHeader("X-Lab-Session") String session){return Map.of("deleted",repo.reset(session(session)),"simulated",true);}
    private String session(String value){
        try {return UUID.fromString(value).toString();}
        catch(IllegalArgumentException e){throw new ResponseStatusException(BAD_REQUEST,"Sessão de laboratório inválida.");}
    }
}
