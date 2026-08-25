package dev.onurerkoc.payguard.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CustomerUpdateRequest {

    @NotBlank(message = "Ad alanı zorunludur")
    @Size(max = 50, message = "Ad en fazla 50 karakter olabilir")
    private String firstName;

    @NotBlank(message = "Soyadı alanı zorunludur")
    @Size(max = 50, message = "Soyadı en fazla 50 karakter olabilir")
    private String lastName;

    @NotBlank(message = "Email alanı zorunludur")
    @Email(message = "Geçerli bir email adresi giriniz")
    @Size(max = 150, message = "Email en fazla 150 karakter olabilir")
    private String email;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}