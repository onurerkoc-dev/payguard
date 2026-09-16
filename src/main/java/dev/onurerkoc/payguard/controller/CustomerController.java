package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.CustomerCreateRequest;
import dev.onurerkoc.payguard.dto.CustomerResponse;
import dev.onurerkoc.payguard.dto.CustomerUpdateRequest;
import dev.onurerkoc.payguard.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import dev.onurerkoc.payguard.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;

@Tag(
        name = "Müşteriler",
        description = "Müşteri profili oluşturma, görüntüleme, güncelleme ve silme işlemleri"
)
@SecurityRequirement(name = OpenApiConfig.BASIC_AUTH_SCHEME)
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(
            CustomerService customerService) {

        this.customerService = customerService;
    }
    @Operation(
            summary = "Yeni müşteri oluşturur",
            description = "Yalnızca ADMIN rolüne sahip kullanıcılar tarafından kullanılabilir."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(
            @Valid @RequestBody CustomerCreateRequest request) {

        return customerService.createCustomer(request);
    }

    @Operation(
            summary = "Tüm müşterileri listeler",
            description = "Yalnızca ADMIN rolüne sahip kullanıcılar tarafından kullanılabilir."
    )
    @GetMapping
    public List<CustomerResponse> getAllCustomers() {

        return customerService.getAllCustomers();
    }
    @Operation(
            summary = "Müşteri profilini getirir",
            description = "Giriş yapan kullanıcı yalnızca kendi müşteri profilini görüntüleyebilir."
    )
    @GetMapping("/{id}")
    public CustomerResponse getCustomerById(
            @PathVariable Long id) {

        return customerService.getCustomerById(id);
    }
    @Operation(
            summary = "Müşteri profilini günceller",
            description = "Müşterinin ad ve soyadını günceller; e-posta adresini değiştirmez."
    )
            @PutMapping("/{id}")
    public CustomerResponse updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest request) {

        return customerService.updateCustomer(id, request);
    }
    @Operation(
            summary = "Müşteri hesabını siler",
            description = "Sanal kartı bulunmayan müşteriyi ve bağlı kullanıcı hesabını silerek oturumu sonlandırır."
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(
            @PathVariable Long id,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {

        customerService.deleteCustomer(id);

        /*
         * Veritabanındaki hesap silindikten sonra mevcut
         * HTTP session ve SecurityContext de kapatılır.
         */
        SecurityContextLogoutHandler logoutHandler =
                new SecurityContextLogoutHandler();

        logoutHandler.logout(
                request,
                response,
                authentication
        );
    }
}