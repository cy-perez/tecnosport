# apps/web

Frontend de TecnoSport. Angular 22.5 con SSR e hidratación, standalone, zoneless
con signals, SCSS, Transloco (español e inglés), TanStack Query, Angular CDK,
Vitest y PWA.

## Ejecutar

Desde la raíz del monorepo:

```
npm run dev --workspace=apps/web
```

Requiere el backend en http://localhost:8080. La URL de la API es configurable y
no está escrita en el código.

Sitio: http://localhost:4200

## Comandos

```
npm run dev --workspace=apps/web        servidor de desarrollo
npm test --workspace=apps/web           Vitest
npm run build --workspace=apps/web      build de producción con SSR
npm run lint --workspace=apps/web       ESLint, incluye límites entre capas
```

`test:e2e` (Playwright, ver `docs/06-testing.md`) todavía no está configurado.
Se añade cuando haya recorridos reales que verificar.

## Estructura

```
src/app/
  core/        interceptores, guards, configuración, tema, i18n
  shared/      componentes del sistema de diseño (prefijo ts-)
  features/    catalogo, carrito, checkout, cuenta, admin, captura360
  layout/      header, footer, menú móvil
src/assets/
  marca/       copiado desde packages/marca. Generado, no se edita
  i18n/        traducciones de Transloco
```

Cada funcionalidad repite las capas del backend: `domain`, `application`,
`infrastructure`, `presentation`.

## Sistema visual

Los tokens, las fuentes y los logos vienen de `packages/marca` y se enlazan en
`angular.json` en este orden, antes de los estilos propios:

```
src/assets/marca/fuentes.css
src/assets/marca/tokens.css
src/styles.scss
```

No se escribe ningún color ni medida literal: todo sale de las variables CSS.
Detalle en `docs/04-ui-marca.md`.

## Catálogo (Fase 1 — cerrada)

`/es/productos` y `/en/productos` (rejilla con filtros y paginación por
cursor) y `/es/productos/:slug` (ficha con galería y selector de variante)
funcionan contra el backend real. Contenido todavía en un solo idioma —
`docs/05-i18n.md` documenta el estado actual. Visor 360 pendiente, Fase 5.

## Cliente de la API

Se genera desde el OpenAPI del backend con `npm run contratos` y queda en
`packages/contratos`. No se escriben a mano las interfaces de respuesta.

## Reglas de código

En el `CLAUDE.md` de esta carpeta.
