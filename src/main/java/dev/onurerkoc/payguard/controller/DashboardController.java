package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.security.PayGuardUserDetails;
import dev.onurerkoc.payguard.service.VirtualCardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class DashboardController {

    private final VirtualCardService virtualCardService;

    public DashboardController(VirtualCardService virtualCardService) {
        this.virtualCardService = virtualCardService;
    }

    @GetMapping("/")
    public String dashboard(
            Model model,
            @AuthenticationPrincipal PayGuardUserDetails user) {

        // Adminin müşteri profili olmadığı için kart paneline yönlendirilmez.
        if (user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            return "redirect:/admin";
        }

        // Giriş yapan kişinin e-postasını HTML sayfasına gönderir.
        model.addAttribute("email", user.getUsername());

        // Müşteri ID'sini URL'den değil, giriş yapan hesaptan alır.
        // Servis yalnızca bu müşteriye ait, numarası maskelenmiş kartları getirir.
        model.addAttribute(
                "cards",
                virtualCardService.getCardsByCustomerId(user.getCustomerId())
        );

        // Thymeleaf, templates/dashboard.html dosyasını gösterir.
        return "dashboard";
    }
}