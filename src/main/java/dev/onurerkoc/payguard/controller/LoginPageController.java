package dev.onurerkoc.payguard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String showLoginPage() {
        // GET sayfayı gösterir; POST /login isteğini Spring Security işler.
        return "login";
    }
}
