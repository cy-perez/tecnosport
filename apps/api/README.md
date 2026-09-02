# apps/api

Backend de TecnoSport. Java 21, Spring Boot 4.1.0, Gradle multi-módulo con
Kotlin DSL, PostgreSQL 16, Flyway, springdoc-openapi.

## Módulos

| Módulo | Contenido | Depende de |
|---|---|---|
| `domain` | Entidades, objetos de valor, reglas de negocio. Java puro | nada |
| `application` | Casos de uso y puertos | `domain` |
| `infrastructure` | JPA, Wompi, Cloud Storage, correo, Flyway | `application`, `domain` |
| `presentation` | Controladores REST, DTO, manejo de errores | `application`, `domain` |
| `bootstrap` | Clase principal, configuración de Spring, perfiles | todos |

La regla de dependencia la verifica ArchUnit y su incumplimiento rompe el build.

## Ejecutar

Requiere la base de datos levantada desde la raíz del monorepo con
`docker compose up -d`.

```
gradlew.bat bootRun
```

- API: http://localhost:8080/api/v1
- OpenAPI: http://localhost:8080/api/openapi.json
- Documentación navegable: http://localhost:8080/api/docs
- Salud: http://localhost:8080/api/v1/salud

`gradlew.bat bootRun` activa el perfil `local` (fijado en
`bootstrap/build.gradle.kts`, no en `application.yml` — nunca se cuela en el
jar empaquetado). Con él, `SembradorCatalogo` inserta un catálogo de ejemplo
la primera vez que arranca contra una base vacía. El jar de producción nunca
siembra nada; ver `docs/adr/0010-datos-de-siembra-por-perfil.md`.

## Catálogo (Fase 1 — cerrada)

`GET /api/v1/productos` (filtro, orden, cursor, texto), `GET
/api/v1/productos/{slug}`, `GET /api/v1/categorias` y `GET /api/v1/marcas`
funcionan contra PostgreSQL real. Contrato completo en `docs/03-api.md`.

## Pruebas

```
gradlew.bat test          unitarias y de integración
gradlew.bat build         lo anterior más ArchUnit y Spotless
```

Las pruebas de integración usan Testcontainers con PostgreSQL 16 y requieren
Docker corriendo. No se usa H2 en ningún caso.

## Migraciones

En `infrastructure/src/main/resources/db/migration`, nombradas
`V{n}__descripcion_en_espanol.sql`. Se aplican al arrancar en local y como paso
separado del despliegue en producción. Una migración ya aplicada no se edita: se
crea otra. `ddl-auto` está en `validate` en todos los entornos.

## Configuración

Variables en `.env.local` de la raíz del monorepo, con `.env.example` como
plantilla. Se leen con clases `@ConfigurationProperties` tipadas. Si falta una
variable obligatoria, la aplicación no arranca. Lista completa en
`docs/07-infra-gcp.md`.

## Reglas de código

En el `CLAUDE.md` de esta carpeta.
