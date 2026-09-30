package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.security.PayGuardUserDetails;
import dev.onurerkoc.payguard.service.CustomerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    private final CustomerService customerService;

    public AdminPageController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String dashboard(
            Model model,
            @AuthenticationPrincipal PayGuardUserDetails user) {

        model.addAttribute("email", user.getUsername());
        model.addAttribute("customers", customerService.getAllCustomers());
        return "admin-dashboard";
    }
}
