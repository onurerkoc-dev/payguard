package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.CustomerCreateRequest;
import dev.onurerkoc.payguard.dto.CustomerResponse;
import dev.onurerkoc.payguard.dto.CustomerUpdateRequest;
import dev.onurerkoc.payguard.exception.CustomerHasVirtualCardsException;
import dev.onurerkoc.payguard.exception.CustomerNotFoundException;
import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;
import dev.onurerkoc.payguard.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


// CustomerController ve ilgili Spring MVC bileşenlerini test için hazırlar.
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    // Gerçek sunucu açmadan istek göndermemizi sağlar.
    @Autowired
    private MockMvc mockMvc;

    // Controller'ın kullandığı servisi sahte bir servisle değiştirir.
    @MockitoBean
    private CustomerService customerService;

    @Test
    void createCustomer_whenRequestIsValid_shouldReturnCreated() throws Exception {

        // GIVEN: Sahte servisin döndüreceği müşteri cevabını hazırlıyoruz.
        CustomerResponse response = new CustomerResponse(
                1L,
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        when(customerService.createCustomer(any(CustomerCreateRequest.class)))
                .thenReturn(response);

        // Postman'da Body kısmına yazdığımız JSON'un aynısı.
        String requestBody = """
            {
                "firstName": "Onur",
                "lastName": "Erkoç",
                "email": "onur@example.com"
            }
            """;

        // WHEN: Controller'a geçerli bir POST isteği gönderiyoruz.
        // THEN: HTTP durum kodunu ve JSON cevabını doğruluyoruz.
        mockMvc.perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Onur"))
                .andExpect(jsonPath("$.lastName").value("Erkoç"))
                .andExpect(jsonPath("$.email").value("onur@example.com"));

        // Controller'ın servise gönderdiği gerçek request nesnesini yakalıyoruz.
        ArgumentCaptor<CustomerCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(CustomerCreateRequest.class);

        verify(customerService).createCustomer(requestCaptor.capture());

        // Gelen JSON'un DTO alanlarına doğru aktarıldığını doğruluyoruz.
        CustomerCreateRequest capturedRequest = requestCaptor.getValue();

        assertEquals("Onur", capturedRequest.getFirstName());
        assertEquals("Erkoç", capturedRequest.getLastName());
        assertEquals("onur@example.com", capturedRequest.getEmail());
    }
    @Test
    void createCustomer_whenRequestIsInvalid_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Ad ve soyad boş, e-posta formatı geçersiz.
        String requestBody = """
            {
                "firstName": "",
                "lastName": "",
                "email": "gecersiz-email"
            }
            """;

        // WHEN: Geçersiz JSON ile müşteri oluşturma isteği gönderiyoruz.
        mockMvc.perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: Validation hatası nedeniyle 400 dönmeli.
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.firstName")
                        .value("Ad alanı zorunludur"))
                .andExpect(jsonPath("$.lastName")
                        .value("Soyadı alanı zorunludur"))
                .andExpect(jsonPath("$.email")
                        .value("Geçerli bir email adresi giriniz"));

        // Validation isteği Controller metoduna ulaşmadan durdurduğu için
        // service kesinlikle çağrılmamalı.
        verify(customerService, never())
                .createCustomer(any(CustomerCreateRequest.class));
    }
    @Test
    void getCustomerById_whenCustomerExists_shouldReturnOk()
            throws Exception {

        // GIVEN: Sahte servisin döndüreceği müşteri cevabı.
        CustomerResponse response = new CustomerResponse(
                1L,
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        when(customerService.getCustomerById(1L))
                .thenReturn(response);

        // WHEN: ID'si 1 olan müşteriyi istiyoruz.
        mockMvc.perform(
                        get("/api/customers/{id}", 1L)
                )

                // THEN: 200 OK ve doğru JSON değerleri dönmeli.
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Onur"))
                .andExpect(jsonPath("$.lastName").value("Erkoç"))
                .andExpect(jsonPath("$.email").value("onur@example.com"));

        // URL'den gelen id'nin servise doğru aktarıldığını kontrol ediyoruz.
        verify(customerService).getCustomerById(1L);
    }
    @Test
    void getCustomerById_whenCustomerDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: Service, ID'si 99 olan müşteriyi bulamıyor.
        when(customerService.getCustomerById(99L))
                .thenThrow(
                        new CustomerNotFoundException(
                                "Müşteri bulunamadı: 99"
                        )
                );

        // WHEN: Var olmayan müşteri için GET isteği gönderiyoruz.
        mockMvc.perform(
                        get("/api/customers/{id}", 99L)
                )

                // THEN: GlobalExceptionHandler 404 ve hata JSON'u döndürmeli.
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Müşteri bulunamadı: 99"));

        verify(customerService).getCustomerById(99L);
    }
    @Test
    void getAllCustomers_whenCustomersExist_shouldReturnOkWithCustomerList()
            throws Exception {

        // GIVEN: Sahte servisin döndüreceği iki müşteri.
        List<CustomerResponse> responses = List.of(
                new CustomerResponse(
                        1L,
                        "Onur",
                        "Erkoç",
                        "onur@example.com"
                ),
                new CustomerResponse(
                        2L,
                        "Ayşe",
                        "Yılmaz",
                        "ayse@example.com"
                )
        );

        when(customerService.getAllCustomers())
                .thenReturn(responses);

        // WHEN: Bütün müşteriler için GET isteği gönderiyoruz.
        mockMvc.perform(
                        get("/api/customers")
                )

                // THEN: 200 OK ve iki elemanlı JSON dizisi dönmeli.
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.length()").value(2))

                // İlk müşteri
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].firstName").value("Onur"))
                .andExpect(jsonPath("$[0].lastName").value("Erkoç"))
                .andExpect(jsonPath("$[0].email")
                        .value("onur@example.com"))

                // İkinci müşteri
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].firstName").value("Ayşe"))
                .andExpect(jsonPath("$[1].lastName").value("Yılmaz"))
                .andExpect(jsonPath("$[1].email")
                        .value("ayse@example.com"));

        verify(customerService).getAllCustomers();
    }
    @Test
    void getAllCustomers_whenNoCustomersExist_shouldReturnEmptyList()
            throws Exception {

        // GIVEN: Sistemde hiç müşteri bulunmuyor.
        when(customerService.getAllCustomers())
                .thenReturn(List.of());

        // WHEN: Bütün müşteriler için GET isteği gönderiyoruz.
        mockMvc.perform(
                        get("/api/customers")
                )

                // THEN: 200 OK ve boş JSON dizisi dönmeli.
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().json("[]"))
                .andExpect(jsonPath("$.length()").value(0));

        verify(customerService).getAllCustomers();
    }
    @Test
    void updateCustomer_whenRequestIsValid_shouldReturnOk()
            throws Exception {

        // GIVEN: Service'in döndüreceği güncellenmiş müşteri.
        CustomerResponse response = new CustomerResponse(
                1L,
                "Onur Can",
                "Erkoç",
                "new@example.com"
        );

        when(customerService.updateCustomer(
                eq(1L),
                any(CustomerUpdateRequest.class)
        )).thenReturn(response);

        String requestBody = """
            {
                "firstName": "Onur Can",
                "lastName": "Erkoç",
                "email": "new@example.com"
            }
            """;

        // WHEN: Güncelleme isteğini gönderiyoruz.
        mockMvc.perform(
                        put("/api/customers/{id}", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: 200 OK ve güncellenmiş bilgiler dönmeli.
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Onur Can"))
                .andExpect(jsonPath("$.lastName").value("Erkoç"))
                .andExpect(jsonPath("$.email")
                        .value("new@example.com"));

        // Service'e gönderilen DTO'yu yakalıyoruz.
        ArgumentCaptor<CustomerUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(CustomerUpdateRequest.class);

        verify(customerService).updateCustomer(
                eq(1L),
                requestCaptor.capture()
        );

        CustomerUpdateRequest capturedRequest =
                requestCaptor.getValue();

        // URL'deki ID verify içinde, JSON alanları burada doğrulanıyor.
        assertEquals("Onur Can", capturedRequest.getFirstName());
        assertEquals("Erkoç", capturedRequest.getLastName());
        assertEquals("new@example.com", capturedRequest.getEmail());
    }
    @Test
    void updateCustomer_whenRequestIsInvalid_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Ad ve soyad boş, e-posta formatı geçersiz.
        String requestBody = """
            {
                "firstName": "",
                "lastName": "",
                "email": "gecersiz-email"
            }
            """;

        // WHEN: Geçersiz güncelleme isteği gönderiyoruz.
        mockMvc.perform(
                        put("/api/customers/{id}", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: Validation hataları 400 olarak dönmeli.
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.firstName")
                        .value("Ad alanı zorunludur"))
                .andExpect(jsonPath("$.lastName")
                        .value("Soyadı alanı zorunludur"))
                .andExpect(jsonPath("$.email")
                        .value("Geçerli bir email adresi giriniz"));

        // Validation başarısız olduğu için service'e ulaşılmamalı.
        verify(customerService, never()).updateCustomer(
                eq(1L),
                any(CustomerUpdateRequest.class)
        );
    }
    @Test
    void updateCustomer_whenEmailAlreadyExists_shouldReturnConflict()
            throws Exception {

        // GIVEN: Güncellemede kullanılmak istenen e-posta zaten mevcut.
        when(customerService.updateCustomer(
                eq(1L),
                any(CustomerUpdateRequest.class)
        )).thenThrow(
                new EmailAlreadyExistsException(
                        "Bu email adresi zaten kullanılıyor"
                )
        );

        // JSON geçerli; hata validation'dan değil iş kuralından gelecek.
        String requestBody = """
            {
                "firstName": "Onur",
                "lastName": "Erkoç",
                "email": "used@example.com"
            }
            """;

        // WHEN: Güncelleme isteğini gönderiyoruz.
        mockMvc.perform(
                        put("/api/customers/{id}", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: GlobalExceptionHandler 409 dönmeli.
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Bu email adresi zaten kullanılıyor"));

        verify(customerService).updateCustomer(
                eq(1L),
                any(CustomerUpdateRequest.class)
        );
    }
    @Test
    void deleteCustomer_whenCustomerCanBeDeleted_shouldReturnNoContent()
            throws Exception {

        // GIVEN:
        // Mockito'da void metotlar varsayılan olarak hiçbir şey yapmaz.
        // Bu nedenle başarılı silme için ayrıca when() yazmamız gerekmez.

        // WHEN: ID'si 1 olan müşteriyi silme isteği gönderiyoruz.
        mockMvc.perform(
                        delete("/api/customers/{id}", 1L)
                )

                // THEN: Başarılı silme işleminde 204 dönmeli.
                .andExpect(status().isNoContent())

                // 204 cevabında response body bulunmamalı.
                .andExpect(content().string(""));

        // URL'deki ID'nin servise doğru aktarıldığını doğruluyoruz.
        verify(customerService).deleteCustomer(1L);
    }
    @Test
    void deleteCustomer_whenCustomerHasVirtualCards_shouldReturnConflict()
            throws Exception {

        // GIVEN: Service, müşterinin sanal kartı olduğu için silmeye izin vermiyor.
        doThrow(
                new CustomerHasVirtualCardsException(
                        "Sanal kartı bulunan müşteri silinemez"
                )
        )
                .when(customerService)
                .deleteCustomer(1L);

        // WHEN: Müşteriyi silme isteği gönderiyoruz.
        mockMvc.perform(
                        delete("/api/customers/{id}", 1L)
                )

                // THEN: GlobalExceptionHandler 409 Conflict dönmeli.
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Sanal kartı bulunan müşteri silinemez"));

        verify(customerService).deleteCustomer(1L);
    }
    @Test
    void deleteCustomer_whenCustomerDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: Service, ID'si 99 olan müşteriyi bulamıyor.
        doThrow(
                new CustomerNotFoundException(
                        "Müşteri bulunamadı: 99"
                )
        )
                .when(customerService)
                .deleteCustomer(99L);

        // WHEN: Var olmayan müşteri için silme isteği gönderiyoruz.
        mockMvc.perform(
                        delete("/api/customers/{id}", 99L)
                )

                // THEN: GlobalExceptionHandler 404 dönmeli.
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Müşteri bulunamadı: 99"));

        verify(customerService).deleteCustomer(99L);
    }
    @Test
    void createCustomer_whenEmailAlreadyExists_shouldReturnConflict()
            throws Exception {

        // GIVEN: Service, gönderilen e-postanın kullanımda olduğunu söylüyor.
        when(customerService.createCustomer(
                any(CustomerCreateRequest.class)
        )).thenThrow(
                new EmailAlreadyExistsException(
                        "Bu email adresi zaten kullanılıyor"
                )
        );

        // JSON validation açısından geçerli.
        String requestBody = """
            {
                "firstName": "Onur",
                "lastName": "Erkoç",
                "email": "onur@example.com"
            }
            """;

        // WHEN: Müşteri oluşturma isteği gönderiyoruz.
        mockMvc.perform(
                        post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: E-posta çakışması nedeniyle 409 dönmeli.
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Bu email adresi zaten kullanılıyor"));

        verify(customerService)
                .createCustomer(any(CustomerCreateRequest.class));
    }
    @Test
    void updateCustomer_whenCustomerDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: Service, ID'si 99 olan müşteriyi bulamıyor.
        when(customerService.updateCustomer(
                eq(99L),
                any(CustomerUpdateRequest.class)
        )).thenThrow(
                new CustomerNotFoundException(
                        "Müşteri bulunamadı: 99"
                )
        );

        // JSON geçerli; hata validation nedeniyle oluşmayacak.
        String requestBody = """
            {
                "firstName": "Onur",
                "lastName": "Erkoç",
                "email": "onur@example.com"
            }
            """;

        // WHEN: Var olmayan müşteriyi güncelleme isteği gönderiyoruz.
        mockMvc.perform(
                        put("/api/customers/{id}", 99L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: GlobalExceptionHandler 404 dönmeli.
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Müşteri bulunamadı: 99"));

        verify(customerService).updateCustomer(
                eq(99L),
                any(CustomerUpdateRequest.class)
        );
    }
}