# packages/contratos

Cliente TypeScript generado desde el contrato OpenAPI del backend. Es código
generado: no se edita a mano.

## Los dos archivos, y por qué son dos

- `openapi.json` es la **instantánea** del contrato: lo que la aplicación sirve
  en `/api/openapi.json`. También es generado.
- `src/tipos.ts` sale de esa instantánea.

Hasta el 21 de septiembre de 2026 solo existía el segundo, y el primero había
que pedírselo a un backend vivo. Eso obligaba a que la única comprobación de que
el cliente estuviera al día viviera en integración continua —levantando
PostgreSQL y `bootRun`—, o sea a enterarse después del empujón. Con la
instantánea guardada, cada eslabón tiene su guardián y los dos corren en la
máquina de quien programa:

| Qué se vigila | Quién | Dónde corre |
|---|---|---|
| Que `openapi.json` sea lo que la aplicación sirve | `ContratoOpenApiTest` | `gradlew build` |
| Que `src/tipos.ts` corresponda a `openapi.json` | `tools/verificar-contratos.mjs` | `npm run verificar` |

## Regenerar

Mover el contrato son dos pasos, uno por eslabón, y los dos archivos se
commitean juntos con el cambio del backend que los movió:

```
cd apps/api && gradlew.bat :bootstrap:test --tests "*ContratoOpenApiTest" -PactualizarContrato=true
npm run contratos
```

El segundo no necesita red ni Docker: lee la instantánea del repositorio.

`npm run contratos-al-dia` responde la otra pregunta —si el cliente guardado ya
corresponde a la instantánea— sin escribir nada. Es lo que corre
`npm run verificar`.

## Uso

`apps/web` importa estos tipos solo dentro de la capa `infrastructure` de cada
funcionalidad, donde se mapean a los modelos de dominio del frontend. Ningún
componente ve un DTO generado.

## Cuando cambia la API

1. Se cambia el backend.
2. Se regeneran los dos archivos.
3. TypeScript rompe en los mapeadores afectados. Esa es la señal, y es
   deliberada: un cambio de contrato tiene que fallar en compilación, no en
   producción.
