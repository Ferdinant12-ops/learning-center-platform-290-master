# Proyecto Whirlpool Certified Care - Service Management API

Guía paso a paso para construir, desde cero, un RESTful API en Spring Boot que da soporte a la
gestión de **Policies** (contratos de cobertura extendida) y **Claims** (reclamos de soporte) de
la plataforma Whirlpool Certified Care. Aplica Domain-Driven Design, arquitectura por capas
(domain, application, interfaces, infrastructure), patrón CQRS, patrón Assembler, un Anti-Corruption
Layer (ACL) entre bounded contexts y eventos de dominio, organizado en los bounded contexts
`contracts`, `entitlement` y `shared`.

> [!NOTE]
> Este proyecto usa el nombre `eb11990u20241a290` (patrón `eb<NRC>u<código-estudiante>`) y el package
> raíz `com.whirlpool.care.platform.u20241a290`. Reemplace el **NRC** y el **código** por los que
> indique su PDF el día del examen. Author: **Jose Fernando Flores Pinchi** (código U20241A290).

## Requisitos previos

- JDK 26.
- Maven 3.9+ (o abrir el proyecto en IntelliJ IDEA, que trae Maven integrado).
- MySQL 8+ instalado y en ejecución.
- Postman, cURL o Swagger UI para probar los endpoints.

## 1. Creación del proyecto

Cree el proyecto con Spring Initializr. Más información en: https://start.spring.io/

> [!TIP]
> **Primer paso (recomendado):** use el siguiente enlace, que abre Spring Initializr ya
> preconfigurado (Spring Web, Spring Data JPA, Validation, MySQL Driver, Lombok, DevTools). Solo
> presione **GENERATE** para descargar el proyecto base:
>
> https://start.spring.io/#!type=maven-project&language=java&platformVersion=4.0.6&packaging=jar&configurationFileFormat=properties&jvmVersion=26&groupId=com.whirlpool.care.platform&artifactId=eb11990u20241a290&packageName=com.whirlpool.care.platform.u20241a290&dependencies=data-jpa,validation,web,devtools,mysql,lombok
>
> Antes de generar, **reemplace** en el formulario el `Artifact` (`eb11990u20241a290`) y el
> `Package name` (`com.whirlpool.care.platform.u20241a290`) por los suyos según el PDF del examen.

Configuración equivalente si lo hace manualmente en https://start.spring.io/ :

```
Project:      Maven
Language:     Java
Spring Boot:  4.x (use la versión que indique su PDF)
Group:        com.whirlpool.care.platform
Artifact:     eb11990u20241a290
Name:         eb11990u20241a290
Package name: com.whirlpool.care.platform.u20241a290
Packaging:    Jar
Java:         26
```

Dependencias:

```
Spring Web
Spring Data JPA
Validation
MySQL Driver
Lombok
Spring Boot DevTools
```

**Generar** y **descomprimir** el proyecto, luego **abrirlo** en el IDE.

### 1.1. Dependencias adicionales en el pom.xml

`springdoc-openapi` NO existe como dependencia en Spring Initializr; agréguela a mano. También la
librería `pluralize` (usada por la estrategia de nombres de base de datos):

```xml
<!-- OpenAPI / Swagger UI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.0.3</version>
</dependency>
<!-- Pluralize: usada por la estrategia de nombres snake-case + plural -->
<dependency>
    <groupId>io.github.encryptorcode</groupId>
    <artifactId>pluralize</artifactId>
    <version>1.0.0</version>
</dependency>
```

> [!IMPORTANT]
> NO agregue Spring Security. El enunciado indica que **Security, CORS y Testing NO forman parte
> del alcance**, por lo que no se solicita token/JWT.

## 2. Creación de la base de datos

Cargue MySQL Workbench o la consola `mysql` y cree la base de datos (esquema) `whirlpool_os`:

```sql
CREATE DATABASE IF NOT EXISTS whirlpool_os;
```

> [!NOTE]
> En MySQL, "esquema" y "base de datos" son sinónimos. La configuración usa
> `createDatabaseIfNotExist=true`, así que también se creará automáticamente si no existe.

## 3. Configuración de application.properties

Reemplace `src/main/resources/application.properties`:

```properties
spring.application.name=eb11990u20241a290

# Spring DataSource Configuration (MySQL, esquema whirlpool_os)
spring.datasource.url=jdbc:mysql://localhost:3306/whirlpool_os?createDatabaseIfNotExist=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Spring Data JPA / Hibernate Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.hibernate.naming.physical-strategy=com.whirlpool.care.platform.u20241a290.shared.infrastructure.persistence.jpa.configuration.strategy.SnakeCaseWithPluralizedTablePhysicalNamingStrategy

# Puerto de escucha del API
server.port=8290

# Valores tomados desde el pom.xml
documentation.application.description=@project.description@
documentation.application.version=@project.version@

# Bundles de mensajes i18n
spring.messages.basename=messages
spring.messages.encoding=UTF-8
```

> [!NOTE]
> Cambie `root/root` por sus credenciales reales de MySQL.

## 4. Estructura de paquetes

Dentro de `src/main/java/com/whirlpool/care/platform/u20241a290`:

```markdown
- 📂 contracts            (bounded context de Policy)
  - 📂 application
    - 📁 acl
    - 📁 commandservices
    - 📁 queryservices
    - 📂 internal
      - 📁 commandservices
      - 📁 queryservices
      - 📁 eventhandlers
  - 📂 domain
    - 📂 model
      - 📁 aggregates      (Policy)
      - 📁 commands        (SeedPoliciesCommand, ProcessClaimCreatedCommand)
      - 📁 queries
      - 📁 valueobjects    (CoverageStatus)
    - 📁 repositories
  - 📂 infrastructure/persistence/jpa
    - 📁 adapters
    - 📁 assemblers
    - 📁 entities          (PolicyPersistenceEntity con @Version)
    - 📁 repositories
  - 📂 interfaces
    - 📁 acl               (ContractsContextFacade)
    - 📂 rest
      - 📁 resources
      - 📁 transform
- 📂 entitlement          (bounded context de Claim)
  - 📂 application
    - 📁 commandservices
    - 📁 queryservices
    - 📂 internal
      - 📁 commandservices
      - 📁 queryservices
      - 📁 outboundservices/acl  (ExternalPolicyService)
  - 📂 domain/model
    - 📁 aggregates        (Claim)
    - 📁 commands
    - 📁 queries
    - 📁 valueobjects      (ClaimStatus)
  - 📂 domain/repositories
  - 📂 infrastructure/persistence/jpa (adapters, assemblers, entities, repositories)
  - 📂 interfaces/rest (resources, transform)
- 📂 shared
  - 📂 application/result  (Result, ApplicationError)
  - 📂 domain/model
    - 📁 aggregates        (AbstractDomainAggregateRoot)
    - 📁 valueobjects      (Period)
    - 📁 events            (ClaimCreatedEvent)
  - 📂 infrastructure
    - 📁 documentation/openapi/configuration
    - 📁 i18n/configuration
    - 📁 persistence/jpa/configuration/strategy
    - 📁 persistence/jpa/entities   (AuditableAbstractPersistenceEntity)
  - 📂 interfaces/rest (GlobalExceptionHandler, resources, transform)
```

## 5. Bounded Context Shared

Contiene los elementos base reutilizables. Debe seguir las especificaciones del proyecto de ejemplo
**Learning Center Platform**.

- **Result** y **ApplicationError** (`application/result`): tipo sellado (sealed interface) que modela
  éxito/fallo con `map`, `flatMap`, `recover`; y un record de error con factory methods
  (`validationError`, `notFound`, `businessRuleViolation`, `conflict`, `unexpected`).
- **AbstractDomainAggregateRoot** (`domain/model/aggregates`): clase base de aggregate roots que
  extiende `AbstractAggregateRoot` de Spring Data (soporte de eventos de dominio).
- **Period** (`domain/model/valueobjects`): **Value Object** con `startDate` y `endDate`, valida que
  `endDate` no sea anterior a `startDate` y expone `contains(LocalDate)`.
- **ClaimCreatedEvent** (`domain/model/events`): evento de integración con `policyId`, `claimId`,
  `reportedAt`.
- **AuditableAbstractPersistenceEntity** (`infrastructure/persistence/jpa/entities`): `@MappedSuperclass`
  con `@EntityListeners(AuditingEntityListener.class)`, aporta `id`, `createdAt` (`@CreatedDate`) y
  `updatedAt` (`@LastModifiedDate`).
- **SnakeCaseWithPluralizedTablePhysicalNamingStrategy** (`infrastructure/.../strategy`): convierte a
  snake_case y pluraliza nombres de tabla (usa `pluralize`).
- **OpenApiConfiguration** y **LocaleConfiguration**: documentación Swagger y resolución de idioma por
  `Accept-Language` (Inglés por defecto; Inglés/Español).
- **Interfaces REST compartidas**: `ErrorResource`, `MessageResource`, `ErrorResponseAssembler`,
  `ResponseEntityAssembler` y `GlobalExceptionHandler` (`@RestControllerAdvice`).

## 6. Bounded Context Contracts (Policy)

- **CoverageStatus** (enum): `ACTIVE`, `EXPIRED`. NO se persiste.
- **Policy** (aggregate root): `id`, `policyNumber` (único, formato `WHP-XXXXX`), `applianceModel`,
  `coveragePeriod` (Period), `lastClaimId` (nullable). Métodos de negocio:
  - `coverageStatus()`: invariante calculado -> `ACTIVE` si hoy está dentro del `coveragePeriod`,
    `EXPIRED` en caso contrario.
  - `isEligibleForClaim()`: retorna `false` mientras `lastClaimId` tenga valor (estado IN_PROGRESS).
  - `assignLastClaim(claimId)`.
- **PolicyRepository** (domain): `save`, `findById`, `findAll`, `existsByPolicyNumber`, `count`.
- **Application (CQRS)**: `PolicyCommandService` (seeding + `ProcessClaimCreatedCommand`) y
  `PolicyQueryService` (all / by id), con sus `Impl`.
- **Seeding** (`ApplicationReadyEventHandler` + `PolicyCommandServiceImpl`): al iniciar puebla:

  | id | policyNumber | applianceModel          | startDate  | endDate    |
  |----|--------------|-------------------------|------------|------------|
  | 1  | WHP-10001    | KitchenAid-Dishwasher   | 2026-01-01 | 2027-12-31 |
  | 2  | WHP-10002    | Whirlpool-Refrigerator  | 2026-01-01 | 2028-06-15 |
  | 3  | WHP-10003    | Maytag-Washer           | 2024-01-01 | 2025-01-01 |
  | 4  | WHP-10004    | JennAir-Oven            | 2026-01-01 | 2029-01-20 |

- **ClaimCreatedEventHandler** (`application/internal/eventhandlers`): escucha `ClaimCreatedEvent` y
  delega en `PolicyCommandService.handle(ProcessClaimCreatedCommand)`, que:
  - es **idempotente** (si `claimId` ya fue procesado -> lo salta),
  - se ejecuta dentro de una **única transacción** (`@Transactional`),
  - aplica **Optimistic Locking** (columna `@Version` en `PolicyPersistenceEntity`),
  - actualiza `lastClaimId`.
- **ACL** (`interfaces/acl/ContractsContextFacade` + `application/acl/ContractsContextFacadeImpl`):
  expone `existsPolicyById(policyId)` e `isCoverageExpired(policyId)` para el otro bounded context.
- **Infrastructure**: `PolicyPersistenceEntity` (`@Table(name = "policies")`, `@Version`),
  `PolicyPersistenceRepository`, `PolicyPersistenceAssembler`, `PolicyRepositoryImpl`.
- **Interfaces REST**: `PoliciesController` (`GET /api/v1/policies`), `PolicyResource` (incluye
  `coverageStatus` calculado y `eligibleForClaim`, sin auditoría) y su assembler.

## 7. Bounded Context Entitlement (Claim)

- **ClaimStatus** (enum): `OPEN`, `IN_PROGRESS`, `RESOLVED`, `DENIED`, con `fromString`.
- **Claim** (aggregate root): `id`, `policyId`, `issueDescription`, `claimStatus`, `reportedAt`.
- **CreateClaimCommand**: valida obligatorios y que `reportedAt` no sea futuro.
- **ClaimRepository** (domain): `save`, `findById`.
- **ExternalPolicyService** (`application/internal/outboundservices/acl`): ACL de salida que usa
  `ContractsContextFacade` para validar la política sin acoplarse al modelo del otro contexto.
- **ClaimCommandServiceImpl**: al registrar un claim
  1. valida vía ACL que la política exista (si no -> `notFound`),
  2. rechaza si la cobertura está `EXPIRED` (-> `businessRuleViolation`, HTTP 422),
  3. persiste el claim,
  4. emite `ClaimCreatedEvent` (con `ApplicationEventPublisher`).
- **Interfaces REST**: `ClaimsController` (`POST /api/v1/claims`, HTTP 201, sin auditoría en el
  response), `CreateClaimResource` (recibe `reportedAt` como String `yyyy-MM-dd HH:mm:ss`),
  `ClaimResource` y sus assemblers.

## 8. Bundles de mensajes i18n

En `src/main/resources`, `messages.properties` (Inglés) y `messages_es.properties` (Español) con las
claves usadas por el `ErrorResponseAssembler` (por ejemplo `error.policy-not-found.message`,
`error.business-rule-violation.message`, `error.validation.message`).

## 9. Clase principal

Agregue `@EnableJpaAuditing` a la clase principal `Eb11990u20241a290Application` (además de
`@SpringBootApplication`) para activar la auditoría automática de `createdAt` / `updatedAt`.

## 10. Ejecución

Ejecute el proyecto desde IntelliJ IDEA (botón Run sobre la clase principal) o con Maven:

```bash
mvn spring-boot:run
```

API disponible en `http://localhost:8290` y Swagger en `http://localhost:8290/swagger-ui/index.html`.

## 11. Probar los endpoints

### GET policies

```bash
curl http://localhost:8290/api/v1/policies
```

### POST claim (política activa -> 201 Created)

```bash
curl -X POST http://localhost:8290/api/v1/claims \
  -H "Content-Type: application/json" \
  -H "Accept-Language: en" \
  -d "{ \"policyId\": 1, \"issueDescription\": \"Compressor not cooling\", \"claimStatus\": \"OPEN\", \"reportedAt\": \"2026-07-13 10:30:00\" }"
```

Respuesta esperada (HTTP 201):

```json
{
  "id": 1,
  "policyId": 1,
  "issueDescription": "Compressor not cooling",
  "claimStatus": "OPEN",
  "reportedAt": "2026-07-13 10:30:00"
}
```

### POST claim sobre política EXPIRED (policyId 3 -> 422)

```bash
curl -X POST http://localhost:8290/api/v1/claims \
  -H "Content-Type: application/json" \
  -H "Accept-Language: es" \
  -d "{ \"policyId\": 3, \"issueDescription\": \"Motor issue\", \"claimStatus\": \"OPEN\", \"reportedAt\": \"2026-07-13 10:30:00\" }"
```

Debe retornar HTTP 422 (Business Rule Violation) porque la cobertura de la política 3 está EXPIRED.
Tras registrar un claim válido, verifique con `GET /api/v1/policies` que la política asociada muestra
`lastClaimId` poblado y `eligibleForClaim: false`.

## 12. Verificar la persistencia

```sql
SELECT * FROM whirlpool_os.policies;
SELECT * FROM whirlpool_os.claims;
```

Las columnas estarán en snake_case (`policy_number`, `appliance_model`, `coverage_start_date`,
`coverage_end_date`, `last_claim_id`, `version`, `created_at`, `updated_at`).

## Author

- **Jose Fernando Flores Pinchi** - código U20241A290
