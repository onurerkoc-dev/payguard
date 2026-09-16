# PayGuard

> Güvenli sanal kart yönetimi ve ödeme yetkilendirme akışlarını modelleyen Spring Boot REST API.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Tests](https://img.shields.io/badge/tests-146%20passing-brightgreen)](#test-stratejisi)
[![CI](https://github.com/onurerkoc-dev/payguard/actions/workflows/ci.yml/badge.svg)](https://github.com/onurerkoc-dev/payguard/actions/workflows/ci.yml)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

PayGuard; müşteri hesaplarını, sanal kartları ve kart işlemlerini yöneten; ödeme taleplerini kart durumu, bakiye ve kullanım limitlerine göre değerlendiren bir backend projesidir.

Projenin odağı yalnızca CRUD endpointleri oluşturmak değildir. Aynı ödeme isteğinin iki kez işlenmemesi, eş zamanlı bakiye güncellemelerinde veri kaybının engellenmesi, kullanıcının yalnızca kendi verisine erişebilmesi ve kritik iş kurallarının otomatik testlerle doğrulanması hedeflenmiştir.

## İçindekiler

- [Öne çıkan özellikler](#öne-çıkan-özellikler)
- [Mimari](#mimari)
- [Veri modeli](#veri-modeli)
- [Ödeme yetkilendirme akışı](#ödeme-yetkilendirme-akışı)
- [Güvenlik modeli](#güvenlik-modeli)
- [API endpointleri](#api-endpointleri)
- [API dokümantasyonu](#api-dokümantasyonu)
- [Teknoloji yığını](#teknoloji-yığını)
- [Ortam profilleri](#ortam-profilleri)
- [Projeyi çalıştırma](#projeyi-çalıştırma)
- [Test stratejisi](#test-stratejisi)
- [Proje yapısı](#proje-yapısı)
- [Tasarım kararları](#tasarım-kararları)
- [Yol haritası](#yol-haritası)
- [Proje durumu](#proje-durumu)
- [Geliştirici](#geliştirici)

## Öne çıkan özellikler

### Kullanıcı ve müşteri yönetimi

- Kullanıcı kaydı ve e-posta normalizasyonu
- BCrypt ile güvenli şifre hashleme
- Benzersiz e-posta kontrolü
- Müşteri bilgilerini görüntüleme, güncelleme ve silme
- Müşteri silindiğinde bağlı kullanıcı hesabını ve oturumu güvenli biçimde sonlandırma
- Eş zamanlı kayıt denemelerinde veritabanı kısıtını anlamlı API hatasına dönüştürme

### Sanal kart yönetimi

- Müşteriye bağlı sanal kart oluşturma
- Kartları listeleme ve kart numarasını özet yanıtlarda maskeleme
- Bakiye yükleme
- Kartı dondurma ve yeniden kullanıma açma
- Tek işlem ve günlük harcama limitlerini güncelleme
- İnternet ve yurt dışı ödeme izinlerini yönetme
- Sayfalama destekli işlem geçmişi

### Ödeme güvenliği

- Kartın dondurulma ve son kullanma tarihi kontrolü
- Yetersiz bakiye kontrolü
- Tek işlem ve günlük limit kontrolü
- İnternet ve yurt dışı işlem izinleri
- Onaylanan ve reddedilen işlemleri nedenleriyle kaydetme
- `Idempotency-Key` ile güvenli tekrar denemeleri
- İşlem anındaki kalan bakiyeyi saklayarak tekrar isteğinde aynı cevabı üretme
- JPA `@Version` optimistic locking ile eş zamanlı bakiye güncellemelerini koruma

## Mimari

PayGuard, sorumlulukları birbirinden ayıran katmanlı bir mimari kullanır.

```mermaid
flowchart TD
    Client["REST istemcisi / Postman"] --> Security["Spring Security Filter Chain"]
    Security --> Controller["Controller + DTO Validation"]
    Controller --> Service["Service + İş Kuralları"]
    Service --> Policy["Yetkilendirme Politikaları"]
    Service --> Repository["Spring Data JPA Repository"]
    Repository --> Database[("MySQL")]
```

| Katman | Sorumluluk |
|---|---|
| Controller | HTTP isteğini alır, doğrulanmış DTO'yu servise iletir ve HTTP cevabını üretir. |
| DTO | API'ye giren ve API'den çıkan verinin sözleşmesini tanımlar. |
| Service | Ödeme, limit, sahiplik ve hesap yaşam döngüsü gibi iş kurallarını uygular. |
| Security | Kimlik doğrulama, rol ve kaynak sahipliği kontrollerini gerçekleştirir. |
| Repository | Entity'lerin MySQL üzerinde kalıcı hâle getirilmesini sağlar. |
| Exception Handler | İş ve doğrulama hatalarını anlamlı HTTP cevaplarına dönüştürür. |

## Veri modeli

```mermaid
erDiagram
    CUSTOMER ||--o| USER_ACCOUNT : "hesaba sahiptir"
    CUSTOMER ||--o{ VIRTUAL_CARD : "kartlara sahiptir"
    VIRTUAL_CARD ||--o{ CARD_TRANSACTION : "işlemleri içerir"

    CUSTOMER {
        Long id PK
        String firstName
        String lastName
        String email UK
    }

    USER_ACCOUNT {
        Long id PK
        String email UK
        String passwordHash
        String role
        boolean enabled
        Long customerId FK
    }

    VIRTUAL_CARD {
        Long id PK
        Long version
        String cardNumber UK
        BigDecimal balance
        BigDecimal singleTransactionLimit
        BigDecimal dailyLimit
        boolean frozen
        Long customerId FK
    }

    CARD_TRANSACTION {
        Long id PK
        String idempotencyKey UK
        String type
        String status
        String declineReason
        BigDecimal amount
        BigDecimal balanceAfterTransaction
        Instant createdAt
        Long cardId FK
    }
```

## Ödeme yetkilendirme akışı

```mermaid
flowchart TD
    Request["Ödeme isteği + Idempotency-Key"] --> Existing{"Anahtar daha önce kullanıldı mı?"}
    Existing -- Evet --> Same{"İstek içeriği aynı mı?"}
    Same -- Evet --> Replay["Kaydedilmiş sonucu değişiklik yapmadan döndür"]
    Same -- Hayır --> Conflict["409 Idempotency Conflict"]
    Existing -- Hayır --> Rules{"Kart, izin, limit ve bakiye kontrolleri"}
    Rules -- Başarılı --> Approved["Bakiyeyi düşür ve APPROVED kaydet"]
    Rules -- Başarısız --> Declined["Bakiyeyi değiştirmeden DECLINED kaydet"]
```

Bir ödeme aşağıdaki nedenlerle reddedilebilir:

- `CARD_FROZEN`
- `CARD_EXPIRED`
- `INSUFFICIENT_BALANCE`
- `SINGLE_TRANSACTION_LIMIT_EXCEEDED`
- `DAILY_LIMIT_EXCEEDED`
- `ONLINE_TRANSACTIONS_DISABLED`
- `INTERNATIONAL_TRANSACTIONS_DISABLED`

## Güvenlik modeli

PayGuard, Spring Security üzerinde session tabanlı kimlik doğrulama kullanır. Geliştirme aşamasında form login ve HTTP Basic desteği aktiftir.

```mermaid
flowchart TD
    Anonymous["Anonim kullanıcı"] --> Register["Kayıt endpointi"]
    User["USER"] --> Own["Yalnızca kendi müşterisi ve kartları"]
    Admin["ADMIN"] --> Management["Müşteri oluşturma ve listeleme"]
```

| İşlem | Anonim | USER | ADMIN |
|---|:---:|:---:|:---:|
| Kullanıcı kaydı | ✅ | ✅ | ✅ |
| Kendi müşteri profilini görüntüleme | ❌ | ✅ | Sahiplik kuralına bağlı |
| Kendi profilini güncelleme veya silme | ❌ | ✅ | Sahiplik kuralına bağlı |
| Kendi sanal kartlarını yönetme | ❌ | ✅ | Sahiplik kuralına bağlı |
| Müşteri oluşturma ve tüm müşterileri listeleme | ❌ | ❌ | ✅ |

Uygulanan güvenlik önlemleri:

- Şifreler açık metin olarak değil, BCrypt hash olarak saklanır.
- Kullanıcı kayıt sırasında rol seçemez; yeni hesap her zaman `USER` olur.
- `@EnableMethodSecurity` ve `@PreAuthorize` ile servis katmanı korunur.
- `CustomerAccessPolicy`, giriş yapan kullanıcının hedef müşteri kaydının sahibi olduğunu doğrular.
- CSRF koruması açık bırakılmıştır.
- Durum değiştiren browser isteklerinin geçerli CSRF token taşıması gerekir.
- Başarılı hesap silme işleminden sonra session ve SecurityContext sonlandırılır.

> `permitAll`, CSRF kontrolünü kapatmaz. Bu nedenle kayıt endpointi herkese açık olsa da browser üzerinden yapılan `POST`, `PUT`, `PATCH` ve `DELETE` isteklerinde CSRF token gereklidir.

## API endpointleri

### Kimlik doğrulama

| Metot | Endpoint | Açıklama | Yetki |
|---|---|---|---|
| `POST` | `/api/auth/register` | Müşteri profili ve kullanıcı hesabı oluşturur. | Herkese açık |
| `POST` | `/login` | Spring Security form login işlemini gerçekleştirir. | Herkese açık |
| `POST` | `/logout` | Oturumu ve güvenlik bağlamını sonlandırır. | Giriş yapmış kullanıcı |

### Müşteriler

| Metot | Endpoint | Açıklama | Yetki |
|---|---|---|---|
| `POST` | `/api/customers` | Müşteri oluşturur. | `ADMIN` |
| `GET` | `/api/customers` | Tüm müşterileri listeler. | `ADMIN` |
| `GET` | `/api/customers/{id}` | Müşteri profilini getirir. | Kayıt sahibi |
| `PUT` | `/api/customers/{id}` | Ad ve soyadı günceller. | Kayıt sahibi |
| `DELETE` | `/api/customers/{id}` | Uygun müşteri ve bağlı hesabı siler, oturumu kapatır. | Kayıt sahibi |

### Sanal kartlar ve ödemeler

| Metot | Endpoint | Açıklama |
|---|---|---|
| `POST` | `/api/customers/{customerId}/cards` | Sanal kart oluşturur. |
| `GET` | `/api/customers/{customerId}/cards` | Müşterinin kartlarını listeler. |
| `GET` | `/api/customers/{customerId}/cards/{cardId}` | Kart detayını getirir. |
| `POST` | `/api/customers/{customerId}/cards/{cardId}/balance` | Karta bakiye yükler. |
| `PATCH` | `/api/customers/{customerId}/cards/{cardId}/freeze` | Kartı dondurur. |
| `PATCH` | `/api/customers/{customerId}/cards/{cardId}/unfreeze` | Kartı yeniden kullanıma açar. |
| `PATCH` | `/api/customers/{customerId}/cards/{cardId}/limits` | Kart limitlerini günceller. |
| `PATCH` | `/api/customers/{customerId}/cards/{cardId}/payment-settings` | İnternet ve yurt dışı ödeme izinlerini günceller. |
| `POST` | `/api/customers/{customerId}/cards/{cardId}/payments` | Ödeme isteğini yetkilendirir. |
| `GET` | `/api/customers/{customerId}/cards/{cardId}/transactions?page=0&size=10` | İşlem geçmişini sayfalı getirir. |

Bu bölümdeki bütün kart endpointleri giriş yapan kullanıcının yalnızca kendi `customerId` değeri için çalışır.

### Örnek ödeme isteği

Ödeme endpointi zorunlu bir `Idempotency-Key` header'ı bekler:

```http
POST /api/customers/7/cards/12/payments HTTP/1.1
Idempotency-Key: payment-2026-0001
Content-Type: application/json
```

```json
{
  "amount": 249.90,
  "merchantName": "Example Store",
  "onlineTransaction": true,
  "internationalTransaction": false
}
```

## API dokümantasyonu

PayGuard endpointleri, request/response modelleri ve validation kuralları
springdoc-openapi tarafından otomatik olarak OpenAPI 3 formatında belgelenir.

Uygulama `local` profiliyle çalışırken dokümantasyona aşağıdaki adreslerden
erişilebilir:

| Kaynak | Adres |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | `http://localhost:8080/v3/api-docs.yaml` |

Swagger UI üzerinden korunan GET endpointlerini denemek için sağ üstteki
`Authorize` butonu kullanılabilir. Kullanıcı adı olarak kayıtlı e-posta,
şifre olarak hesabın gerçek şifresi girilir.

Swagger, HTTP Basic bilgisini isteklerde `Authorization` header'ı ile gönderir.
Bu değer şifrelenmiş değil, Base64 kodlanmış olduğundan gerçek ortamlarda
uygulama mutlaka HTTPS üzerinden çalıştırılmalıdır.

OpenAPI entegrasyonu uygulamanın güvenlik kurallarını devre dışı bırakmaz.
Müşteri ve kart endpointlerinde kimlik doğrulama ve sahiplik kontrolü devam
eder. Durum değiştiren `POST`, `PUT`, `PATCH` ve `DELETE` isteklerinde CSRF
koruması açık kalır.

## Teknoloji yığını

| Alan | Teknoloji |
|---|---|
| Dil | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Web | Spring Web MVC (REST) |
| API dokümantasyonu | OpenAPI 3, Swagger UI, springdoc-openapi |
| Güvenlik | Spring Security, session authentication, BCrypt, CSRF |
| Persistence | Spring Data JPA, Hibernate |
| Veritabanı migration | Flyway |
| Veritabanı | MySQL |
| Doğrulama | Jakarta Validation |
| Test | JUnit 5, Mockito, MockMvc, Spring Security Test |
| Entegrasyon testi | Testcontainers + MySQL 8.4 |
| Yapılandırma | Spring Profiles (`local`, `test`, `prod`) |
| Build | Maven Wrapper |
| CI | GitHub Actions |

## Ortam profilleri

PayGuard, aynı uygulama kodunu farklı ortamlarda güvenli biçimde çalıştırmak
için Spring Profiles kullanır. Ortak ayarlar `application.properties`
dosyasında tutulur; veritabanı bağlantısı gibi ortama göre değişen değerler
ilgili profil dosyasından alınır.

```mermaid
flowchart TD
    Common["Ortak ayarlar"] --> Local["local: Yerel MySQL"]
    Common --> Test["test: Testcontainers MySQL"]
    Common --> Prod["prod: Sunucu değişkenleri"]
```

| Profil | Yapılandırma kaynağı | Kullanım amacı |
|---|---|---|
| `local` | `application-local.properties` | Geliştiricinin bilgisayarındaki MySQL veritabanı |
| `test` | `src/test/resources/application-test.properties` ve `@ServiceConnection` | Docker üzerinde geçici ve izole Testcontainers MySQL |
| `prod` | `application-prod.properties` ve environment variable'lar | Sunucu veya hosting ortamındaki production veritabanı |

Ortak yapılandırmada Hibernate yalnızca Flyway tarafından oluşturulan şemayı
doğrular:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
```

Production profili bağlantı bilgilerini kaynak koddan değil aşağıdaki
environment variable'lardan bekler:

```text
PAYGUARD_DB_URL
PAYGUARD_DB_USERNAME
PAYGUARD_DB_PASSWORD
PORT
```

Projede bilerek varsayılan profil tanımlanmamıştır. Böylece profil seçilmeden
başlatılan bir deployment'ın yanlışlıkla yerel veritabanına bağlanması yerine
uygulama güvenli biçimde bağlantı hatası vererek durur.

## Projeyi çalıştırma

### Gereksinimler

- Java 21
- Git
- MySQL 8+
- Testcontainers testleri için çalışan Docker Desktop

Kurulumları doğrulayın:

```powershell
java -version
docker --version
docker info
.\mvnw.cmd -version
```

Maven çıktısındaki Java sürümü de `21` olmalıdır.

### 1. Repoyu klonlayın

```bash
git clone https://github.com/onurerkoc-dev/payguard.git
cd payguard
```

### 2. MySQL veritabanını hazırlayın

```sql
CREATE DATABASE payguard
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER 'payguard_user'@'localhost'
    IDENTIFIED BY 'guvenli-bir-sifre';

GRANT ALL PRIVILEGES ON payguard.*
    TO 'payguard_user'@'localhost';

FLUSH PRIVILEGES;
```

### 3. Yerel ortam değişkenlerini tanımlayın

Geçerli PowerShell oturumunda veritabanı şifresini tanımlayın ve `local`
profilini etkinleştirin:

```powershell
$env:PAYGUARD_DB_PASSWORD="guvenli-bir-sifre"
$env:SPRING_PROFILES_ACTIVE="local"
```

macOS/Linux:

```bash
export PAYGUARD_DB_PASSWORD="guvenli-bir-sifre"
export SPRING_PROFILES_ACTIVE="local"
```

Environment variable değerleri yalnızca o terminal oturumu için geçerlidir.
Şifreyi herhangi bir `application*.properties` dosyasına veya Git geçmişine
eklemeyin.

### 4. Veritabanı migration'ları

Uygulama başlatıldığında Flyway, `src/main/resources/db/migration`
altındaki migration dosyalarını sürüm sırasına göre otomatik olarak çalıştırır:

```text
V1__initial_schema.sql
V2__add_payment_transaction_details.sql
V3__create_user_accounts.sql
```

- `V1`, temel müşteri, sanal kart ve işlem tablolarını oluşturur.
- `V2`, ödeme işleminin internet, yurt dışı ve işlem sonrası bakiye bilgilerini ekler.
- `V3`, Spring Security kullanıcı hesapları tablosunu oluşturur.
- Uygulanan migration'lar `flyway_schema_history` tablosunda kayıt altında tutulur.

Tabloları elle oluşturmak gerekmez. Hibernate şemayı değiştirmez;
`spring.jpa.hibernate.ddl-auto=validate` ayarıyla entity ve tablo yapılarının
uyumlu olduğunu doğrular.

Varsayılan yapılandırmada `baseline-on-migrate` açık değildir. Böylece Flyway
geçmişi bulunmayan dolu bir veritabanının yanlışlıkla sahiplenilmesi engellenir.

### 5. Uygulamayı başlatın

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Uygulama varsayılan olarak `http://localhost:8080` adresinde çalışır.
Başlangıç logunda aşağıdaki satır görülmelidir:

```text
The following 1 profile is active: "local"
```

### Production profili

Production ortamında uygulama başlamadan önce aşağıdaki değerler hosting
sağlayıcısı veya sunucu üzerinden tanımlanır:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:PAYGUARD_DB_URL="jdbc:mysql://db-host:3306/payguard"
$env:PAYGUARD_DB_USERNAME="payguard_user"
$env:PAYGUARD_DB_PASSWORD="production-sifresi"
$env:PORT="8080"
```

Bu değerler yalnızca örnektir; gerçek production bilgileri repoya eklenmez.

## Test stratejisi

PayGuard'ın güncel test tabanı **146 başarılı testten** oluşur.

```mermaid
flowchart TD
    Unit["Unit testleri"] --> Service["Service iş kuralları"]
    Web["MockMvc testleri"] --> Controller["HTTP + validation + CSRF"]
    Security["Method security testleri"] --> Authorization["Rol + sahiplik"]
    Integration["Testcontainers"] --> MySQL[("Gerçek MySQL 8.4")]
```

| Test türü | Doğruladığı alan |
|---|---|
| Unit test | Servis kuralları, ödeme kararları, idempotency ve hata senaryoları |
| Controller testi | Endpoint, durum kodu, JSON cevabı, validation ve CSRF davranışı |
| Security testi | Login, yanlış şifre, logout, session, rol ve kaynak sahipliği |
| Repository entegrasyon testi | Gerçek MySQL üzerindeki unique constraint ve kalıcılık davranışı |
| Optimistic locking testi | Aynı kartı eş zamanlı güncelleyen işlemlerde kayıp güncellemenin engellenmesi |
| Yaşam döngüsü testi | Müşteri silinirken bağlı kullanıcı hesabının tutarlı biçimde kaldırılması |

Tüm testleri çalıştırmak için:

```powershell
.\mvnw.cmd clean test
```

macOS/Linux:

```bash
./mvnw clean test
```

Testcontainers, entegrasyon testleri sırasında ihtiyaç duyulan izole
`mysql:8.4` container'larını otomatik olarak başlatır. Tam test paketi
çalışırken Docker Desktop'ta rastgele isim ve portlara sahip birden fazla
geçici MySQL container'ı görülebilir; bu normaldir.

Gerçek veritabanı kullanan context ve repository testleri
`@ActiveProfiles("test")` ile test profilini etkinleştirir. JDBC URL, kullanıcı
adı ve şifre dosyaya yazılmaz; `@ServiceConnection` bu değerleri çalışan
MySQL container'ından Spring Boot'a otomatik olarak aktarır. Mockito tabanlı
unit testleri gerçek veritabanına ihtiyaç duymadığı için bu profili kullanmaz.

`Ryuk` isimli yardımcı container, testler tamamlandığında geçici kaynakların
temizlenmesini yönetir. MySQL image'ı daha önce indirildiyse Docker aynı
image'ı yeniden indirmez.

Beklenen güncel sonuç:

```text
Tests run: 146, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Proje yapısı

```text
src
├── main
│   ├── java/dev/onurerkoc/payguard
│   │   ├── config       # Spring Security ve OpenAPI yapılandırması
│   │   ├── controller   # REST endpointleri
│   │   ├── dto          # Request ve response sözleşmeleri
│   │   ├── entity       # JPA domain modelleri
│   │   ├── exception    # Domain hataları ve global hata yönetimi
│   │   ├── repository   # Spring Data JPA repositoryleri
│   │   ├── security     # UserDetails ve sahiplik politikası
│   │   └── service      # İş kuralları ve transaction sınırları
│   └── resources
│       ├── db/migration                  # Flyway migration'ları
│       ├── application.properties        # Ortak ayarlar
│       ├── application-local.properties  # Yerel MySQL bağlantısı
│       └── application-prod.properties   # Production environment değişkenleri
└── test
    ├── java/dev/onurerkoc/payguard
    │   ├── config       # Testcontainers yapılandırması
    │   ├── controller   # MockMvc testleri
    │   ├── entity       # Entity davranış testleri
    │   ├── repository   # Gerçek MySQL entegrasyon testleri
    │   ├── security     # Authentication ve authorization testleri
    │   └── service      # Unit testler
    └── resources
        └── application-test.properties   # Test ortamı ayarları
```

## Tasarım kararları

### Neden session tabanlı authentication?

Proje, ileride eklenecek aynı-origin web arayüzüyle çalışacak şekilde tasarlanmıştır. Bu nedenle kimlik bilgisini browser tarafında elle saklamak yerine sunucu tarafından yönetilen session tercih edilmiştir.

### Neden idempotency?

Ağ problemi nedeniyle aynı ödeme isteği yeniden gönderilebilir. `Idempotency-Key`, tekrar isteğinin ikinci kez bakiye düşürmesini engeller. Aynı anahtar farklı ödeme verileriyle kullanılırsa istek çakışma olarak reddedilir.

### Neden optimistic locking?

Aynı kart bakiyesini iki transaction eş zamanlı değiştirebilir. `@Version`, eski veriyi kullanan ikinci transaction'ın ilk güncellemeyi sessizce ezmesini engeller.

### Neden Testcontainers?

Repository davranışları yalnızca mock veya H2 ile değil, üretimde kullanılan veritabanı ailesiyle doğrulanır. Testcontainers her test çalıştırmasında izole ve tekrarlanabilir bir MySQL ortamı sağlar.

### Neden Flyway?

Hibernate'in şemayı otomatik olarak güncellemesi yerine bütün veritabanı
değişiklikleri sürümlü SQL dosyalarıyla yönetilir. Böylece şemanın hangi
değişikliklerden geçtiği Git geçmişinden ve `flyway_schema_history`
tablosundan izlenebilir.

Yeni bir ortam V1'den başlayarak aynı migration sırasını çalıştırır.
Uygulanmış migration dosyaları değiştirilmez; sonraki değişiklikler V4,
V5 ve devam eden sürümler olarak eklenir.

### Neden ortam profilleri ayrıldı?

Yerel geliştirme, otomatik test ve production ortamları aynı bağlantı
bilgilerini kullanmaz. Spring Profiles sayesinde iş kodu değiştirilmeden yalnızca
ortama ait yapılandırma seçilir. Yerel şifre kaynak koda yazılmaz, testler
izole MySQL container'larında çalışır ve production bağlantısı yalnızca
sunucunun environment variable değerlerinden alınır.

### Neden DTO kullanılıyor?

Entity'ler doğrudan API sözleşmesi yapılmaz. DTO'lar istemcinin gönderebileceği alanları sınırlar, validation kurallarını taşır ve persistence modelinin dışarı sızmasını engeller.

## Yol haritası

### Tamamlanan temel

- [x] Müşteri ve sanal kart domain modeli
- [x] Ödeme yetkilendirme kuralları
- [x] Idempotency ve optimistic locking
- [x] Birim, web, güvenlik ve MySQL entegrasyon testleri
- [x] Session tabanlı Spring Security temeli
- [x] Rol ve müşteri sahipliği yetkilendirmesi
- [x] GitHub Actions ile otomatik test
- [x] Flyway ile sürümlü veritabanı migration'ları
- [x] Local, test ve production profillerini ayırma
- [x] OpenAPI 3 ve Swagger UI dokümantasyonu

### Sıradaki geliştirme sırası

| Sıra | Aşama | Neden bu sırada? |
|---:|---|---|
| 1 | Güvenli admin hesabı oluşturma akışı | Admin yetkili endpointlerin gerçek uygulama üzerinde kontrollü biçimde kullanılmasını sağlar. |
| 2 | Spring MVC, Thymeleaf ve Bootstrap kullanıcı paneli | Hazır backend özelliklerini aynı-origin, session tabanlı bir web arayüzüyle kullanılabilir hâle getirir. |
| 3 | Uygulamayı Docker ile paketleme | Uygulama ve MySQL'in farklı makinelerde tekrarlanabilir biçimde çalıştırılmasını kolaylaştırır. |

### Daha sonra değerlendirilecek geliştirmeler

- Spring Boot Actuator ile health ve uygulama durumu endpointleri
- Filtreleme ve gelişmiş sayfalama seçenekleri
- Test kapsamı raporu ve CI çıktılarının zenginleştirilmesi
- Production deployment dokümantasyonu

Bu sıra, projeyi gereksiz yere mikroservis, Kafka veya dağıtık sistem
karmaşıklığına taşımadan mevcut monolitik yapıyı tamamlamayı hedefler.

## Proje durumu

PayGuard aktif olarak geliştirilen bir portföy ve öğrenme projesidir. Mevcut
sürüm; REST backend, ödeme kuralları, veri tutarlılığı, güvenlik, OpenAPI
dokümantasyonu, migration yönetimi ve otomatik test altyapısını içerir. Son
kullanıcıya yönelik Spring MVC/Thymeleaf paneli henüz eklenmemiştir ve yol
haritasında yer almaktadır.

> Bu proje eğitim ve portföy amacıyla geliştirilmiştir. Üretilen kart numaraları sentetiktir; gerçek kart verisi veya gerçek para transferi için kullanılmamalıdır.

## Geliştirici

**Onur Erkoç**

- GitHub: [onurerkoc-dev](https://github.com/onurerkoc-dev)
- Portfolio: [onurerkoc.dev](https://onurerkoc.dev)
