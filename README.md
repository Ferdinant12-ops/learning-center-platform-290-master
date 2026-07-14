# Whirlpool Certified Care - Service Management API (`eb11990u20241a290`)

## Overview
`eb11990u20241a290` is a RESTful API for Whirlpool's Certified Care platform. It manages the
support operations of the Service Management Context, exposing two resources: **Policies**
(extended-care contracts) and **Claims** (support requests). The solution applies Domain-Driven
Design, layered architecture (domain, application, interfaces, infrastructure), the CQRS pattern
and an Anti-Corruption Layer between the `contracts` and `entitlement` bounded contexts.

## Tech Stack
- Java 26
- Spring Boot 4
- Spring Data JPA (Hibernate)
- MySQL (schema `whirlpool_os`)
- SpringDoc OpenAPI (Swagger UI)
- Lombok
- Maven

## Bounded Contexts
- **contracts** - the `Policy` aggregate, its coverage-status invariant and the `ClaimCreatedEvent` handler.
- **entitlement** - the `Claim` aggregate and its creation flow with policy validation through the ACL.
- **shared** - reusable base elements (`Period` value object, `Result`, auditing, naming strategy, i18n, error handling).

## Endpoints
- `POST /api/v1/claims` - registers a claim (validates the policy exists and its coverage is not expired). Returns `201 Created`.
- `GET /api/v1/policies` - returns the stored policies (with calculated coverage status).

## Running
1. Create the MySQL database (or rely on `createDatabaseIfNotExist=true`): `CREATE DATABASE whirlpool_os;`
2. Adjust `spring.datasource.username` / `password` in `src/main/resources/application.properties`.
3. Run the application (IntelliJ IDEA, or `mvn spring-boot:run`).
4. API: `http://localhost:8290` - Swagger UI: `http://localhost:8290/swagger-ui/index.html`

## Internationalization
Error messages are localized via the `Accept-Language` header (`en` / `es`).

## Author
- **Jose Fernando Flores Pinchi** - code U20241A290
