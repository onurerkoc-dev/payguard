package dev.onurerkoc.payguard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
/*
Service exception fırlatır
→ GlobalExceptionHandler exception’ı yakalar
→ Map.of hata mesajını oluşturur
→ ResponseEntity 400 durum kodunu ekler
→ Jackson Map’i JSON’a çevirir
→ Postman JSON cevabını görür
 */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCustomerNotFound(
            CustomerNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", exception.getMessage()));
    }
    @ExceptionHandler(InvalidCardLimitException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCardLimit(
            InvalidCardLimitException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errors);
    }
    /*
    *
    *
    *
    *
    * Service kartı bulamaz
*→ VirtualCardNotFoundException fırlatır
*→ GlobalExceptionHandler exception’ı yakalar
*→ HTTP durumunu 404 yapar
*→ Map JSON hata cevabını oluşturur
     */
    @ExceptionHandler(VirtualCardNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleVirtualCardNotFound(
            VirtualCardNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", exception.getMessage()));
    }
    @ExceptionHandler(CustomerHasVirtualCardsException.class)
    public ResponseEntity<Map<String, String>> handleCustomerHasVirtualCards(
            CustomerHasVirtualCardsException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }
    /*
Aynı kart iki farklı transaction tarafından aynı anda güncellenirse
oluşan optimistic locking hatasını anlamlı API cevabına dönüştürür.
*/
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>>
    handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "message",
                        "Kart bilgileri başka bir işlem tarafından güncellendi. "
                                + "Lütfen işlemi tekrar deneyin."
                ));
    }
}