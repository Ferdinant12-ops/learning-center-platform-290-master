# ESTUDIO — Cómo entender y replicar el Learning Center Platform

Guía para comprender la arquitectura del proyecto de ejemplo (`learning-center-platform`) y
replicarla en el examen. Todos los fragmentos son del contexto real `profiles` (el molde más limpio:
tiene entidad, CRUD, ACL y eventos).

---

## 0. La idea central (léela primero)

> **La arquitectura es SIEMPRE la misma. Solo cambia el dominio.**

Cada "cosa" que el examen te pida gestionar (Policy, Claim, Course...) se construye repitiendo el
**mismo conjunto de carpetas y archivos**. Si aprendes UN contexto, sabes hacer todos.

Hay **3 tipos de módulo**:
1. **`shared`** → base reutilizable. Se **copia casi tal cual** (solo cambias el paquete). No es del caso.
2. **Un bounded context** (`profiles`, `contracts`...) → el patrón que repites por cada entidad.
3. **Cruces entre contextos** → ACL (consultar otro contexto) y eventos (avisar a otro contexto).

---

## 1. Anatomía de un bounded context (las 4 capas)

```
<contexto>/
├── domain/          ← reglas puras, SIN Spring ni JPA (el "qué")
├── application/     ← orquesta la lógica = CQRS (el "cómo")
├── infrastructure/  ← JPA/BD (el "dónde se guarda")
└── interfaces/      ← REST (el "cómo entra y sale")
```

Regla de oro de dependencias: **domain no depende de nadie**; application depende de domain;
infrastructure e interfaces dependen de domain/application. Nunca al revés.

---

## 2. Los 2 flujos que debes visualizar

### FLUJO POST (crear)
```
JSON → Resource → [CommandFromResourceAssembler] → Command
→ Controller → CommandService.handle() → valida + crea Aggregate
→ Repository.save() → adapter → [PersistenceAssembler] → @Entity → BD
→ Result<Aggregate> → [ResourceFromEntityAssembler] → JSON (201)
```

### FLUJO GET (listar/leer)
```
Controller → QueryService.handle(GetXQuery) → Repository.findAll/findById
→ Aggregate(s) → [ResourceFromEntityAssembler] → JSON (200)
```

Cada archivo que verás abajo es **una parada de estos dos flujos**.

---

## 3. Recorrido archivo por archivo (molde: contexto `profiles`)

### 3.1. DOMAIN — el corazón

**`domain/model/aggregates/Profile.java`** — el Aggregate Root (tu entidad de negocio).
- Extiende `AbstractDomainAggregateRoot` (del shared) → para poder registrar eventos.
- **No lleva anotaciones JPA** (eso va en la Persistence Entity aparte).
- Tiene un constructor que recibe el Command.
```java
public class Profile extends AbstractDomainAggregateRoot<Profile> {
    @Getter @Setter private Long id;
    @Getter private PersonName name;
    private EmailAddress emailAddress;
    // ...
    public Profile(CreateProfileCommand command) { /* arma el aggregate */ }

    public void onCreated() {                       // registra evento de dominio
        registerDomainEvent(ProfileCreatedEvent.from(this));
    }
}
```
> **Replicar:** crea tu aggregate (ej. `Policy`), con sus atributos como campos, un constructor
> desde el Command, y los **métodos de negocio** que pida el caso (ej. `coverageStatus()`,
> `isEligibleForClaim()`).

**`domain/model/commands/CreateProfileCommand.java`** — intención de escribir. Un `record` simple.
```java
public record CreateProfileCommand(String firstName, String lastName, String email, ...) {}
```
> **Replicar:** un record con los datos que llegan para crear. Aquí puedes validar en el constructor
> compacto (obligatorios, rangos, fecha no futura...).

**`domain/model/queries/GetAllProfilesQuery.java`, `GetProfileByIdQuery.java`** — intención de leer.
Records (a veces vacíos, a veces con un id).

**`domain/model/valueobjects/EmailAddress.java`** — Value Object: un `record` que **se valida a sí mismo**.
```java
public record EmailAddress(String address) {
    public EmailAddress {
        if (address == null || address.isBlank()) throw new IllegalArgumentException("...");
        if (!EMAIL_PATTERN.matcher(address).matches()) throw new IllegalArgumentException("...");
    }
}
```
> **Replicar:** enums (ej. `ClaimStatus`) y VOs con formato/validación (ej. `Period`, un código con regex).

**`domain/repositories/ProfileRepository.java`** — el "puerto": una **interface** (sin implementación).
```java
public interface ProfileRepository {
    Optional<Profile> findById(Long id);
    List<Profile> findAll();
    Profile save(Profile profile);
    boolean existsByEmailAddress(EmailAddress emailAddress);
}
```
> **Replicar:** declara los métodos que tu caso necesite (`save`, `findById`, `findAll`, `existsBy...`, `count`).

---

### 3.2. APPLICATION — la lógica (CQRS = separar escrituras de lecturas)

**`application/commandservices/ProfileCommandService.java`** — interface de **escrituras**.
```java
public interface ProfileCommandService {
    Result<Profile, ApplicationError> handle(CreateProfileCommand command);
}
```

**`application/internal/commandservices/ProfileCommandServiceImpl.java`** — la lógica de negocio real.
```java
@Service
public class ProfileCommandServiceImpl implements ProfileCommandService {
    private final ProfileRepository profileRepository;   // inyección por constructor
    // ...
    public Result<Profile, ApplicationError> handle(CreateProfileCommand command) {
        try {
            if (profileRepository.existsByEmailAddress(new EmailAddress(command.email())))
                return Result.failure(ApplicationError.conflict("Profile", "...already exists"));
            var profile = new Profile(command);
            return Result.success(profileRepository.save(profile));
        } catch (IllegalArgumentException e) {
            return Result.failure(ApplicationError.validationError("Profile", e.getMessage()));
        }
    }
}
```
> **Replicar:** aquí van las **reglas de negocio** (unicidad, validar contra otro contexto vía ACL,
> emitir eventos). Devuelve `Result.success(...)` o `Result.failure(ApplicationError...)`.

**`application/queryservices/ProfileQueryService.java` + `internal/.../ProfileQueryServiceImpl.java`** —
lecturas. El Impl solo delega al repositorio.
```java
@Service
public class ProfileQueryServiceImpl implements ProfileQueryService {
    public List<Profile> handle(GetAllProfilesQuery query) { return profileRepository.findAll(); }
    public Optional<Profile> handle(GetProfileByIdQuery query) { return profileRepository.findById(query.profileId()); }
}
```

---

### 3.3. INFRASTRUCTURE — la persistencia (JPA)

Aquí está el truco DDD: el dominio (`Profile`) y la tabla (`ProfilePersistenceEntity`) son **clases
distintas**, y un **assembler** las traduce.

**`infrastructure/persistence/jpa/entities/ProfilePersistenceEntity.java`** — la clase que SÍ es tabla.
```java
@Entity
@Table(name = "profiles")                     // ← @Table evita que se pluralice mal
public class ProfilePersistenceEntity extends AuditableAbstractPersistenceEntity {
    // AuditableAbstractPersistenceEntity ya aporta id, createdAt, updatedAt
    @Convert(converter = EmailAddressPersistenceConverter.class)
    @Column(name = "email_address", nullable = false, unique = true)
    private EmailAddress emailAddress;
    // ...
}
```
> **Replicar:** una `@Entity` por aggregate, que **extiende `AuditableAbstractPersistenceEntity`**
> (auditoría automática). Si necesitas Optimistic Locking, agrega aquí `@Version private Long version;`.

**`infrastructure/persistence/jpa/repositories/ProfilePersistenceRepository.java`** — Spring Data.
```java
@Repository
public interface ProfilePersistenceRepository extends JpaRepository<ProfilePersistenceEntity, Long> {
    // métodos derivados o @Query si necesitas
}
```

**`infrastructure/persistence/jpa/assemblers/ProfilePersistenceAssembler.java`** — traduce dominio ⇄ entidad.
```java
public static Profile toDomainFromPersistence(ProfilePersistenceEntity e) { return new Profile(...); }
public static ProfilePersistenceEntity toPersistenceFromDomain(Profile p) { /* set... */ }
```

**`infrastructure/persistence/jpa/adapters/ProfileRepositoryImpl.java`** — implementa el puerto del dominio
usando Spring Data + el assembler. **Aquí también se publican los eventos** al guardar algo nuevo.
```java
@Repository
public class ProfileRepositoryImpl implements ProfileRepository {
    public Profile save(Profile profile) {
        boolean isNew = profile.getId() == null;
        var saved = ProfilePersistenceAssembler.toDomainFromPersistence(
                        jpa.save(ProfilePersistenceAssembler.toPersistenceFromDomain(profile)));
        if (isNew) {                                     // publica el evento tras persistir
            saved.onCreated();
            saved.domainEvents().forEach(eventPublisher::publishEvent);
            saved.clearDomainEvents();
        }
        return saved;
    }
    // findById/findAll/exists... delegan a jpa + assembler
}
```
> **Nota:** en tu repo Whirlpool yo publico el evento desde el **command service** (más simple). Las dos
> formas son válidas; esta (publicar en el adapter) es la del ejemplo.

---

### 3.4. INTERFACES — el REST

**`interfaces/rest/ProfilesController.java`** — los endpoints.
```java
@RestController
@RequestMapping(value = "/api/v1/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProfilesController {
    @PostMapping
    public ResponseEntity<?> createProfile(@Valid @RequestBody CreateProfileResource resource) {
        var command = CreateProfileCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = profileCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ProfileResourceFromEntityAssembler::toResourceFromEntity, HttpStatus.CREATED);
    }
    @GetMapping
    public ResponseEntity<List<ProfileResource>> getAllProfiles() {
        var list = profileQueryService.handle(new GetAllProfilesQuery());
        return ResponseEntity.ok(list.stream().map(ProfileResourceFromEntityAssembler::toResourceFromEntity).toList());
    }
}
```

**`interfaces/rest/resources/CreateProfileResource.java`** — el JSON de entrada (record, con `@Schema`/validaciones).
**`interfaces/rest/resources/ProfileResource.java`** — el JSON de salida (record; **NO incluyas auditoría**).
**`interfaces/rest/transform/CreateProfileCommandFromResourceAssembler.java`** — Resource → Command.
**`interfaces/rest/transform/ProfileResourceFromEntityAssembler.java`** — Aggregate → Resource.
```java
public static ProfileResource toResourceFromEntity(Profile e) {
    return new ProfileResource(e.getId(), e.getFullName(), e.getEmailAddress(), e.getStreetAddress());
}
```

---

## 4. Patrones ENTRE contextos (lo que da más puntos)

### 4.1. ACL — cuando un contexto necesita datos de otro

Ejemplo: `learning` necesita saber de `profiles`. **Nunca** importa el modelo interno del otro; usa una
**fachada (facade)**.

- **`profiles/interfaces/acl/ProfilesContextFacade.java`** (interface, lo que el contexto EXPONE):
```java
public interface ProfilesContextFacade {
    Long fetchProfileIdByEmail(String email);
}
```
- **`profiles/application/acl/ProfilesContextFacadeImpl.java`** (implementación, usa sus propios services):
```java
@Service
public class ProfilesContextFacadeImpl implements ProfilesContextFacade {
    public Long fetchProfileIdByEmail(String email) {
        return profileQueryService.handle(new GetProfileByEmailQuery(new EmailAddress(email)))
                .map(Profile::getId).orElse(0L);
    }
}
```
- **`learning/application/internal/outboundservices/acl/ExternalProfileService.java`** (el que CONSUME):
```java
@Service
public class ExternalProfileService {
    private final ProfilesContextFacade profilesContextFacade;   // inyecta la fachada del otro contexto
    public Optional<ProfileId> fetchProfileByEmail(String email) {
        var id = profilesContextFacade.fetchProfileIdByEmail(email);
        return id == 0L ? Optional.empty() : Optional.of(new ProfileId(id));
    }
}
```
> **Patrón ACL = 2 archivos en el contexto dueño (Facade + FacadeImpl) + 1 en el contexto consumidor
> (ExternalXService).** En Whirlpool: `entitlement` valida la `Policy` de `contracts` así.

### 4.2. EVENTOS — cuando algo pasa y otro contexto debe reaccionar

3 piezas:
1. **El evento** (`profiles/domain/model/events/ProfileCreatedEvent.java`) — un `record` con los datos.
2. **Se publica** al guardar (en el adapter, con `ApplicationEventPublisher`, ver 3.3).
3. **El handler** en el OTRO contexto escucha con `@EventListener`:
```java
@Service
public class ProfileCreatedEventHandler {     // en learning
    @EventListener
    public void on(ProfileCreatedIntegrationEvent event) {
        studentCommandService.handle(new CreateStudentByProfileIdCommand(event.profileId()));
    }
}
```
> El seeding inicial (`ApplicationReadyEventHandler`) es el mismo patrón, pero escuchando el
> `ApplicationReadyEvent` de Spring.
> En Whirlpool: `entitlement` emite `ClaimCreatedEvent`; `contracts` lo escucha y actualiza la `Policy`
> (con idempotencia + `@Transactional` + `@Version`).

---

## 5. RECETA de replicación (por cada entidad, en orden)

Para cada aggregate del examen, crea los archivos en este orden (sigue el flujo):

1. `domain/model/valueobjects/` → enums y VOs
2. `domain/model/aggregates/XAggregate.java`
3. `domain/model/commands/CreateXCommand.java` (+ validaciones)
4. `domain/model/queries/GetAllX.java`, `GetXById.java`
5. `domain/repositories/XRepository.java` (interface)
6. `application/commandservices/XCommandService.java` + `internal/.../XCommandServiceImpl.java`
7. `application/queryservices/XQueryService.java` + `internal/.../XQueryServiceImpl.java`
8. `infrastructure/persistence/jpa/entities/XPersistenceEntity.java` (`@Entity`, extends Auditable)
9. `infrastructure/persistence/jpa/repositories/XPersistenceRepository.java`
10. `infrastructure/persistence/jpa/assemblers/XPersistenceAssembler.java`
11. `infrastructure/persistence/jpa/adapters/XRepositoryImpl.java`
12. `interfaces/rest/resources/CreateXResource.java` + `XResource.java`
13. `interfaces/rest/transform/` (los 2 assemblers)
14. `interfaces/rest/XController.java`

Luego, si el caso lo pide: **ACL** (4.1), **eventos + handler** (4.2), **seeding**.

---

## 6. Qué RENOMBRAR exactamente (del ejemplo a tu caso)

| En el learning-center | Cámbialo por |
|---|---|
| Paquete `com.acme.center.platform` | tu paquete del PDF (`com.whirlpool.care.platform.u<código>`) |
| Contextos `profiles`, `learning`, `iam` | tus 2 contextos del PDF |
| `Profile`, `Student`, `Course` | tus aggregates |
| `EmailAddress`, `PersonName` | tus VOs/enums |
| `/api/v1/profiles` | tus endpoints del PDF |
| Nombres de columnas/tablas | según tu caso (snake_case + plural lo hace la naming strategy) |

**Lo que NO se toca:** todo el `shared`, la estructura de 4 capas, los patrones (CQRS, ACL, eventos,
assemblers, auditoría, Result, GlobalExceptionHandler).

---

## 7. Errores comunes (evítalos)

- Poner `@Entity` en el aggregate de dominio. ❌ Va en la Persistence Entity aparte.
- Olvidar `@Table(name="...")` → la tabla queda con nombre feo (`x_persistence_entities`).
- Incluir `createdAt`/`updatedAt` en el Resource de respuesta. ❌ La auditoría NO va en el response.
- Que un contexto importe el modelo interno de otro. ❌ Usa el ACL (facade).
- Olvidar `@EnableJpaAuditing` en la clase principal → no se poblan las fechas.
- Usar el puerto 8096 (el del ejemplo). Usa uno tuyo.

---

## 8. Referencia rápida del `shared` (se copia tal cual)

| Archivo | Para qué |
|---|---|
| `Result` / `ApplicationError` | modelar éxito/fallo sin excepciones |
| `AbstractDomainAggregateRoot` | base de aggregates + eventos |
| `AuditableAbstractPersistenceEntity` | id + createdAt/updatedAt automáticos |
| `SnakeCaseWithPluralizedTablePhysicalNamingStrategy` | snake_case + tablas en plural |
| `GlobalExceptionHandler` | traduce excepciones a respuestas HTTP |
| `ErrorResponseAssembler` / `ResponseEntityAssembler` | Result → ResponseEntity |
| `OpenApiConfiguration` | Swagger |
| `LocaleConfiguration` + `messages*.properties` | i18n EN/ES por Accept-Language |

---

*Documento de estudio — Jose Fernando Flores Pinchi. El código de referencia es
`learning-center-platform`; esta solución (`eb11990u20241a290`, caso Whirlpool) es un ejemplo aplicado
de todos estos patrones.*
