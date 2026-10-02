package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.UserRegistrationRequest;
import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;
import dev.onurerkoc.payguard.exception.InvalidPasswordException;
import dev.onurerkoc.payguard.service.UserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationPageController {

    private final UserRegistrationService registrationService;

    public RegistrationPageController(UserRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @InitBinder("request")
    public void configureRegistrationFields(WebDataBinder binder) {
        // Formdan rol veya müşteri ID'si gibi ek alanlar kabul edilmez.
        binder.setAllowedFields("firstName", "lastName", "email", "password");
    }

    @GetMapping("/register")
    public String showRegistrationPage(Model model) {
        model.addAttribute("request", new UserRegistrationRequest());
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("request") UserRegistrationRequest request,
            BindingResult errors,
            RedirectAttributes redirectAttributes) {

        if (errors.hasErrors()) {
            request.setPassword(null);
            return "register";
        }

        try {
            // Mevcut servis müşteri ve USER hesabını birlikte oluşturur.
            registrationService.register(request);
        } catch (EmailAlreadyExistsException exception) {
            errors.rejectValue("email", "email.exists", exception.getMessage());
        } catch (InvalidPasswordException exception) {
            errors.rejectValue("password", "password.invalid", exception.getMessage());
        }

        // Açık şifreyi yeniden gösterilen formda veya yönlendirmede tutma.
        request.setPassword(null);
        if (errors.hasErrors()) {
            return "register";
        }

        redirectAttributes.addFlashAttribute("registrationSuccess", true);
        return "redirect:/register";
    }
}
