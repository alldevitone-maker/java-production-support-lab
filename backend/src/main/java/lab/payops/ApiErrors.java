package lab.payops;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("error","REQUEST_REJECTED","message",e.getReason()==null?"Solicitação rejeitada.":e.getReason(),"simulated",true));}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("error","INVALID_REQUEST","message","Confira origem, destino, cenário e valor (duas casas, de 0,01 a 1.000.000 BRL).","simulated",true));}
}
