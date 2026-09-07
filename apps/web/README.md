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

## Probar el asistente de captura 360 desde un teléfono

La cámara, el sensor de orientación y `crypto.subtle` solo existen en un
**contexto seguro**: HTTPS, o `localhost`. Desde un teléfono, `localhost` es el
propio teléfono, así que el asistente de captura no se puede probar sin HTTPS.

`proxy.conf.json` manda `/api` al backend, así que **un solo túnel al 4200 sirve
la aplicación y la API**: no hay contenido mixto ni una segunda URL que
configurar.

```
docker compose up -d                     # desde la raíz
gradlew.bat bootRun                      # en apps/api
npm run dev --workspace=apps/web -- --allowed-hosts   # desde la raíz
cloudflared tunnel --url http://localhost:4200
```

El túnel imprime una URL `https://algo-aleatorio.trycloudflare.com`. Dos cosas
tienen que conocerla:

- **El dev server**, por eso la bandera `--allowed-hosts` de arriba; sin ella el
  servidor rechaza el host del túnel y la página no carga. `angular.json` **no**
  lo resuelve: su `security.allowedHosts` de desarrollo dice `localhost` y solo
  acepta hosts exactos —el comodín `.trycloudflare.com` no le sirve—, y un host
  efímero no tiene por qué quedar versionado. La bandera de `ng serve` es
  booleana: sin valor, acepta cualquier host, que es justo lo que hace falta
  para un host que cambia en cada corrida.
- **El bucket de imágenes**, o el `PUT` firmado muere en el preflight de CORS:

  ```
  GCS_ORIGENES_CORS=http://localhost:4200,https://algo-aleatorio.trycloudflare.com \
    node infra/dev/bucket-imagenes.mjs
  ```

  Esa corrida solo reemplaza la configuración de CORS; el bucket, la cuenta de
  servicio y la llave quedan como están.

**Mientras el túnel esté arriba, la aplicación de desarrollo es accesible desde
internet**, panel de `ADMIN` incluido, con el usuario que crea `ADMIN_CORREO`. La
URL es aleatoria y efímera, pero es exposición real: bájalo al terminar
(`Ctrl+C`).

En iOS el permiso del sensor de orientación se pide con un gesto del usuario y
solo sobre HTTPS con certificado confiable — por eso un certificado autofirmado
en la LAN no basta ahí, y sí el túnel.

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
  core/        interceptores, guards, configuración, tema, i18n, cliente http
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

## Carrito (Fase 2 — cerrada)

Botón "agregar al carrito" en la ficha, badge de cantidad en el encabezado y
`/es/carrito` (cambiar cantidad, eliminar línea) funcionan contra el backend
real. `CarritoStore` es un servicio singleton, no una función de fábrica
como las de `catalogo` — necesario para que encabezado, ficha y la página
del carrito compartan la misma señal de `carritoId`. Es también la única
vitrina que no precarga en el resolver de ruta (`ADR-0011`): el carrito es
anónimo y vive en `localStorage`, así que no hay nada que el servidor pueda
precargar. Detalle en `apps/web/CLAUDE.md`.

## Cliente de la API

Se genera desde el OpenAPI del backend con `npm run contratos` y queda en
`packages/contratos`. No se escriben a mano las interfaces de respuesta.

## Reglas de código

En el `CLAUDE.md` de esta carpeta.
