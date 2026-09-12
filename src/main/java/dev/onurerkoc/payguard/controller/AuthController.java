package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.UserRegistrationRequest;
import dev.onurerkoc.payguard.dto.UserRegistrationResponse;
import dev.onurerkoc.payguard.service.UserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRegistrationService userRegistrationService;

    public AuthController(
            UserRegistrationService userRegistrationService) {

        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserRegistrationResponse register(
            @Valid @RequestBody UserRegistrationRequest request) {

        return userRegistrationService.register(request);
    }
}