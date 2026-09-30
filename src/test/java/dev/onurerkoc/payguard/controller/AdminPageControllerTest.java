package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.config.SecurityConfig;
import dev.onurerkoc.payguard.dto.CustomerResponse;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.security.PayGuardUserDetails;
import dev.onurerkoc.payguard.security.PayGuardUserDetailsService;
import dev.onurerkoc.payguard.service.CustomerService;
import dev.onurerkoc.payguard.service.VirtualCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminPageController.class, DashboardController.class})
@Import(SecurityConfig.class)
class AdminPageControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private VirtualCardService virtualCardService;
    @MockitoBean
    private PayGuardUserDetailsService userDetailsService;

    @Test
    void home_whenAdminHasNoCustomer_shouldRedirectWithoutLoadingCards() throws Exception {
        mockMvc.perform(get("/").with(user(admin())))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin"));

        verifyNoInteractions(customerService, virtualCardService);
    }

    @Test
    void home_whenCustomerLogsIn_shouldKeepCustomerDashboard() throws Exception {
        Customer customer = new Customer("Onur", "Erkoç", "customer@example.com");
        ReflectionTestUtils.setField(customer, "id", 7L);
        PayGuardUserDetails principal = new PayGuardUserDetails(
                new UserAccount("customer@example.com", "test-password-hash", customer)
        );
        when(virtualCardService.getCardsByCustomerId(7L)).thenReturn(List.of());

        mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("email", "customer@example.com"))
                .andExpect(content().string(containsString("Hesap paneli")));

        verify(virtualCardService).getCardsByCustomerId(7L);
        verifyNoInteractions(customerService);
    }

    @Test
    void dashboard_whenAdmin_shouldRenderCustomersAndLogoutForm() throws Exception {
        List<CustomerResponse> customers = List.of(
                new CustomerResponse(7L, "Onur", "Erkoç", "customer@example.com")
        );
        when(customerService.getAllCustomers()).thenReturn(customers);

        mockMvc.perform(get("/admin").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dashboard"))
                .andExpect(model().attribute("email", "admin@example.com"))
                .andExpect(model().attribute("customers", customers))
                .andExpect(content().string(containsString("Yönetici paneli")))
                .andExpect(content().string(containsString("customer@example.com")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        verify(customerService).getAllCustomers();
        verifyNoInteractions(virtualCardService);
    }

    @Test
    void dashboard_whenNoCustomers_shouldRenderEmptyState() throws Exception {
        when(customerService.getAllCustomers()).thenReturn(List.of());

        mockMvc.perform(get("/admin").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Henüz kayıtlı müşteri yok.")))
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    void dashboard_whenRegularUser_shouldForbidAccessBeforeLoadingCustomers() throws Exception {
        mockMvc.perform(get("/admin").with(user("customer@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(customerService, virtualCardService);
    }

    @Test
    void dashboard_whenAnonymous_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(customerService, virtualCardService);
    }

    @Test
    void dashboard_shouldEscapeCustomerInputInHtml() throws Exception {
        when(customerService.getAllCustomers()).thenReturn(List.of(
                new CustomerResponse(7L, "<script>alert(1)</script>", "Test", "customer@example.com")
        ));

        mockMvc.perform(get("/admin").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    private PayGuardUserDetails admin() {
        return new PayGuardUserDetails(UserAccount.createAdmin(
                "admin@example.com", "test-password-hash"
        ));
    }
}
