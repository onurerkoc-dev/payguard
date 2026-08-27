package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.VirtualCardCreateRequest;
import dev.onurerkoc.payguard.dto.VirtualCardResponse;
import dev.onurerkoc.payguard.service.VirtualCardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
Postman isteği
→ Controller isteği yakalar
→ Service’i çağırır
→ Service VirtualCard oluşturur
→ Repository MySQL’e kaydeder
→ Controller 201 Created cevabı döndürür
 */
@RestController
@RequestMapping("/api/customers")
public class VirtualCardController {

    private final VirtualCardService virtualCardService;

    public VirtualCardController(VirtualCardService virtualCardService) {
        this.virtualCardService = virtualCardService;
    }

    @PostMapping("/{customerId}/cards")
    public ResponseEntity<VirtualCardResponse> createCard(
            @PathVariable("customerId") Long customerId,
            @Valid @RequestBody VirtualCardCreateRequest request) {

        VirtualCardResponse response =
                virtualCardService.createCard(customerId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}