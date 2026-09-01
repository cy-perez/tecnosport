# ADR 0007. Monorepo

Fecha: 2026-08-30. Estado: aceptada.

## Contexto
Backend en Java con Gradle, frontend en Angular con npm, un kit de marca que
ambos consumen, infraestructura en Terraform y un contrato OpenAPI que une las
dos aplicaciones. Un solo desarrollador.

## Decisión
Un solo repositorio. `apps/api` con su Gradle autocontenido, `apps/web` y
`packages/*` como workspaces de npm, `infra` con Terraform, `docs` y `.claude` en
la raíz.

## Alternativas
Repositorios separados: obliga a versionar y publicar el kit de marca y los
contratos como paquetes, y a coordinar despliegues entre repos. Para un
desarrollador es ceremonia sin beneficio.

## Consecuencias
Un cambio de contrato se hace en un solo commit y una sola revisión: el backend,
el cliente generado y el frontend viajan juntos. A cambio, la integración continua
necesita detección de cambios por ruta para no reconstruir todo en cada commit, y
el repositorio crece más rápido.

Ventaja concreta con Claude Code: el contexto completo del proyecto está en un
solo lugar y los documentos se referencian con rutas relativas estables.
