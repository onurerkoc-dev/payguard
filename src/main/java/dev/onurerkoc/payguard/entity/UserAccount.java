package dev.onurerkoc.payguard.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Giriş sırasında kullanılacak benzersiz e-posta adresi.
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    // Açık şifreyi değil, BCrypt ile üretilen hash'i saklar.
    @Column(nullable = false, length = 255)
    private String passwordHash;

    // Veritabanında USER veya ADMIN olarak saklanır.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    // Hesabın giriş yapmasına izin verilip verilmediğini belirtir.
    @Column(nullable = false)
    private boolean enabled;

    // Bir müşteri en fazla bir giriş hesabına bağlanabilir.
    // Müşteri profili olmayan yönetici hesapları için null olabilir.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", unique = true)
    private Customer customer;

    // JPA'nın veritabanından nesne oluşturabilmesi için gereklidir.
    protected UserAccount() {
    }
    // Yeni kayıt olan normal kullanıcının hesabını oluşturur.
    public UserAccount(
            String email,
            String passwordHash,
            Customer customer) {

        // Normal kullanıcı hesabı mutlaka bir müşteriye bağlı olmalıdır.
        if (customer == null) {
            throw new IllegalArgumentException(
                    "Kullanıcı hesabı bir müşteriye bağlı olmalıdır"
            );
        }

        this.email = email;
        this.passwordHash = passwordHash;
        this.customer = customer;

        // Kullanıcı kayıt olurken rol seçemez.
        this.role = UserRole.USER;

        // Yeni hesabın giriş yapmasına izin verilir.
        this.enabled = true;
    }
    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Customer getCustomer() {
        return customer;
    }
}