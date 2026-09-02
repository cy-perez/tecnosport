# ADR 0010. Datos de siembra por perfil de Spring, nunca en una migración

Fecha: 2026-09-02. Estado: aceptada.

## Contexto

El catálogo de ejemplo (productos, variantes, atributos de las tres líneas)
hace falta para desarrollar y probar localmente. La forma más simple de
insertarlo sería una migración Flyway más — pero una migración corre
siempre, en todos los entornos, incluida producción (`docs/07-infra-gcp.md`:
las migraciones se aplican como paso propio del despliegue). Un catálogo de
ejemplo con precios y existencias inventados no puede llegar nunca a la base
de datos real del negocio.

## Decisión

`SembradorCatalogo` es un `ApplicationRunner` anotado con `@Profile("local")`
en `infrastructure/catalogo/siembra`. El perfil `local` no se activa en
`application.yml` (eso lo heredaría el jar empaquetado que corre en Cloud
Run): se fija solo en `bootstrap/build.gradle.kts`, en la tarea `bootRun`,
así que únicamente `gradlew.bat bootRun` en una máquina de desarrollo lo
dispara. Es idempotente: si ya hay productos, no hace nada.

## Alternativas

Migración Flyway con los datos: más simple de escribir, pero el catálogo de
ejemplo quedaría insertado en producción el día del primer despliegue, y
alguien tendría que acordarse de limpiarlo a mano — exactamente el tipo de
paso manual que se olvida.

## Consecuencias

Producción arranca con el catálogo vacío hasta que exista una vía real de
carga (panel administrativo de Fase 4, o una migración de datos deliberada y
revisada aparte). Cualquier siembra de datos futura (inventario de ejemplo,
usuarios de prueba) sigue este mismo patrón: perfil de Spring, nunca
Flyway.
