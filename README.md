# Certified Care - Service Management API 

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
- PostgreSQL or MySQL (schema `whirlpool_os`), selectable via Spring profile
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

## Database selection (Spring profiles)
Both drivers are bundled. Choose the database in `src/main/resources/application.properties`:
```properties
spring.profiles.active=postgres   # or: mysql
```
- `postgres` -> `application-postgres.properties` (jdbc:postgresql://localhost:5432/whirlpool_os)
- `mysql` -> `application-mysql.properties` (jdbc:mysql://localhost:3306/whirlpool_os)

> The Whirlpool exam statement requests **MySQL**; set `spring.profiles.active=mysql` for that delivery.
> Adjust the datasource `username` / `password` in the corresponding profile file.

## Running
1. Create the schema in the chosen database:
   - PostgreSQL: `CREATE DATABASE whirlpool_os;` then, connected to it, `CREATE SCHEMA IF NOT EXISTS whirlpool_os;`
   - MySQL: `CREATE DATABASE whirlpool_os;` (schema and database are the same)
2. Set `spring.profiles.active` and adjust credentials in the matching profile file.
3. Run the application (IntelliJ IDEA, or `mvn spring-boot:run`).
4. API: `http://localhost:8290` - Swagger UI: `http://localhost:8290/swagger-ui/index.html`

## Internationalization
Error messages are localized via the `Accept-Language` header (`en` / `es`).

## Author
- **Jose Fernando Flores Pinchi** - code U20241A290
