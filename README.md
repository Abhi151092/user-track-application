# User Authentication Microservice

A production-style, standalone authentication microservice built with **Spring Boot 3.5**, **Java 21**, and **PostgreSQL**. It handles registration, login, JWT issuance, password reset, and password change.

## 1. Project Overview

This service owns user identity and credentials for a system: registration, authentication (JWT), forgot/reset password, and change password — nothing else. It's designed to be dropped in front of other services as the single source of truth for "who is this user."

## 2. Architecture

```
Client
  |
  v
Auth Controller
  |
  v
Auth Service (Registration / Authentication / PasswordReset / PasswordChange)
  |
  +---- User Repository
  |
  +---- Password Encoder (BCrypt)
  |
  +---- JWT Service
  |
  +---- Password Reset Service
              |
              v
       Password Reset Token Repository
              |
              v
          PostgreSQL
```

Requests flow through a stateless `JwtAuthenticationFilter` before reaching controllers. Controllers hold no business logic — they delegate to services, which use constructor injection throughout.

## 3. Technology Stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.x |
| Security | Spring Security 6, JWT (jjwt) |
| Persistence | Spring Data JPA + PostgreSQL |
| Migrations | Flyway |
| Validation | Jakarta Bean Validation |
| Docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Mockito, Testcontainers |
| Build | Maven |

## 4. Project Structure

```
com.example.userauth
 ├── config       # Security, OpenAPI configuration
 ├── controller   # REST controllers
 ├── dto          # Request/response DTOs
 ├── entity       # JPA entities + enums
 ├── exception    # Custom exceptions + GlobalExceptionHandler
 ├── repository   # Spring Data repositories
 ├── security     # JwtService, JwtAuthenticationFilter, UserDetailsService
 ├── service      # Business logic
 ├── mapper       # Entity <-> DTO mapping
 └── util         # TokenUtil (secure token generation/hashing)
```

## 5. PostgreSQL Setup

```bash
createdb user_auth_db
# or via psql:
psql -U postgres -c "CREATE DATABASE user_auth_db;"
```

Flyway will create the schema automatically on startup — no manual DDL needed.

## 6. Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `user_auth_db` | Database name |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | *(dev default — override in prod)* | HMAC signing secret, 256+ bits |
| `JWT_EXPIRATION` | `3600` | Access token lifetime, in seconds |
| `PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES` | `15` | Reset token TTL |
| `LOGIN_MAX_FAILED_ATTEMPTS` | `5` | Failed attempts before temporary lockout |
| `LOGIN_LOCKOUT_DURATION_MINUTES` | `15` | Lockout duration |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated allowed origins |

**Never commit real secrets.** Set these via your shell, `.env`, or your orchestrator's secret manager.

## 7. Running Locally

```bash
export DB_HOST=localhost DB_PORT=5432 DB_NAME=user_auth_db DB_USERNAME=postgres DB_PASSWORD=postgres
export JWT_SECRET="$(openssl rand -base64 48)"

mvn clean install
mvn spring-boot:run
```

The service starts on `http://localhost:8080`.

## 8. Running with Docker

```bash
docker compose up --build
```

This starts PostgreSQL and the service together, with Flyway migrations applied automatically on boot.

## 9. Flyway Migrations

| File | Purpose |
|---|---|
| `V1__create_users_table.sql` | `users` table, UUID PK, unique email, status/role checks |
| `V2__create_password_reset_tokens_table.sql` | `password_reset_tokens` table, FK to `users`, indexed `token_hash` |

Migrations run automatically at startup (`spring.flyway.enabled=true`). To add new schema changes, add `V3__...sql`, etc. — never edit an already-applied migration.

## 10. API Endpoints

| Method | Path | Auth required | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | No | Create a new user |
| POST | `/api/v1/auth/login` | No | Authenticate, receive JWT |
| POST | `/api/v1/auth/forgot-password` | No | Request a reset token (dev: logged) |
| POST | `/api/v1/auth/reset-password` | No | Reset password using a valid token |
| POST | `/api/v1/auth/change-password` | Yes (Bearer JWT) | Change password for the logged-in user |

## 11. Sample curl Commands

**Register**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Abhimanyu",
    "lastName": "Kumar",
    "email": "abhimanyu@example.com",
    "password": "Password@123",
    "phoneNumber": "9876543210"
  }'
```

**Login**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "abhimanyu@example.com", "password": "Password@123"}'
```

**Forgot password**
```bash
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email": "abhimanyu@example.com"}'
```

**Reset password**
```bash
curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token": "<token-from-dev-logs>", "newPassword": "NewPassword@123"}'
```

**Change password (authenticated)**
```bash
curl -X POST http://localhost:8080/api/v1/auth/change-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"currentPassword": "Password@123", "newPassword": "NewPassword@123"}'
```

## 12. JWT Authentication Explanation

On login, the service issues a signed JWT (HMAC-SHA) containing:
- `sub`: the user's email
- `role`: `USER` or `ADMIN`
- `iat` / `exp`: issued-at and expiration timestamps

Clients send it as `Authorization: Bearer <token>`. `JwtAuthenticationFilter` validates the signature and expiry on every request, loads the user via `CustomUserDetailsService`, and populates the Spring Security context. No server-side session state is kept — the API is fully stateless.

## 13. Password Reset Flow

1. Client calls `/forgot-password` with an email.
2. The service always returns the same generic message, regardless of whether the email exists, to avoid account enumeration.
3. If the account exists, a cryptographically random token is generated; only its SHA-256 **hash** is stored, with an expiry (default 15 minutes). The raw token is logged (development only — replace with a real email provider in production).
4. Client calls `/reset-password` with the raw token and a new password.
5. The service hashes the incoming token, looks it up, checks it isn't expired or already used, updates the password, marks the token used, and invalidates any other active tokens for that user.

## 14. Security Considerations

- Passwords hashed with `BCryptPasswordEncoder`; plaintext is never persisted or logged.
- Reset tokens are stored as SHA-256 hashes; the raw token exists only in transit and in the (dev-only) log line.
- JWT secret is read from `JWT_SECRET` — never hardcoded. Use a 256-bit+ random value in production.
- CSRF is disabled because the API is stateless and token-based (no cookies/sessions).
- CORS is restricted to `CORS_ALLOWED_ORIGINS`.
- A simple in-memory `LoginAttemptService` locks an account out temporarily after repeated failed logins. **This is per-instance state** — replace with a shared store (e.g., Redis) before running multiple instances.
- `GlobalExceptionHandler` returns a consistent error shape and never leaks stack traces.
- Use HTTPS/TLS termination (e.g., at a load balancer or ingress) in production — this service does not terminate TLS itself.

## 15. Running Tests

```bash
# Unit tests only
mvn test

# All tests, including Testcontainers-backed integration tests (requires Docker)
mvn verify
```

Unit tests cover `RegistrationService`, `AuthenticationService`, `PasswordResetService`, `PasswordChangeService`, and controller-level validation via `MockMvc`. `AuthIntegrationTest` spins up a real PostgreSQL container and exercises the register → login flow end-to-end through Flyway and Spring Security.

## 16. Swagger URL

Once running: **http://localhost:8080/swagger-ui.html**

OpenAPI JSON: `http://localhost:8080/v3/api-docs`
