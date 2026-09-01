---
description: Implementa una funcionalidad completa respetando las capas
---

Vas a implementar: $ARGUMENTS

Antes de escribir una línea, lee `CLAUDE.md`, `docs/01-arquitectura.md`,
`docs/02-modelo-datos.md` y el `CLAUDE.md` de la aplicación que corresponda.

Preséntame un plan con:

1. Qué entra en `domain` y qué invariantes tiene.
2. Qué casos de uso y qué puertos nuevos aparecen en `application`.
3. Qué adaptadores hay que escribir en `infrastructure`, incluidas las
   migraciones de Flyway.
4. Qué endpoints y DTO en `presentation`.
5. Qué se prueba en cada capa, y específicamente qué prueba fallaría si la lógica
   se rompiera.
6. Qué archivos tocas, en orden.

Para ahí y espera mi aprobación. No escribas código hasta que lo apruebe.

Cuando lo apruebe, implementa una capa a la vez, empezando por `domain`, y para
después de cada una para que la revise. Un commit por capa, con mensaje
convencional en español y el alcance del monorepo.
