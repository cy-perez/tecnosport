# ADR 0001. Arquitectura estricta por capas en módulos separados

Fecha: 2026-08-30. Estado: aceptada.

## Contexto
Un desarrollador, un proyecto de larga vida, y una app móvil futura que consumirá
el mismo núcleo. La disciplina de capas por convención se rompe sola.

## Decisión
Cinco módulos Gradle en `apps/api`: `domain`, `application`, `infrastructure`,
`presentation`, `bootstrap`. Las dependencias permitidas se declaran en cada
`build.gradle.kts` y ArchUnit falla el build si se rompen. En el frontend, la
misma separación por funcionalidad, verificada con reglas de ESLint.

## Alternativas
Paquetes en un solo módulo: más simple, pero nada impide un import prohibido.
Microservicios: costo operativo injustificado para este volumen.

## Consecuencias
Más ceremonia al crear una funcionalidad y algo más de código de mapeo. A cambio,
la arquitectura la valida el compilador y el núcleo es reutilizable por la app.
