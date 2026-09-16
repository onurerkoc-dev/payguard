package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.UserRegistrationRequest;
import dev.onurerkoc.payguard.dto.UserRegistrationResponse;
import dev.onurerkoc.payguard.service.UserRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
        name = "Kimlik Doğrulama",
        description = "Kullanıcı kaydı ve kimlik doğrulama işlemleri"
)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRegistrationService userRegistrationService;

    public AuthController(
            UserRegistrationService userRegistrationService) {

        this.userRegistrationService = userRegistrationService;
    }
    @Operation(
            summary = "Yeni kullanıcı kaydı oluşturur",
            description = "Müşteri profiliyle bağlantılı, USER rolüne sahip yeni bir hesap oluşturur."
    )
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserRegistrationResponse register(
            @Valid @RequestBody UserRegistrationRequest request) {

        return userRegistrationService.register(request);
    }
}