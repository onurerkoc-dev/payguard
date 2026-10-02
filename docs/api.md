# API rehberi

[← PayGuard](../README.md)

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
