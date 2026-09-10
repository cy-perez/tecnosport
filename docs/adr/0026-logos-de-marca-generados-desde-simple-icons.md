# ADR 0026. Los logos de marca se generan, y su librería no llega a producción

Fecha: 2026-09-10. Estado: aceptada.

## Contexto

El pie enlaza los perfiles de Facebook, Instagram y WhatsApp, y los enlazaba
como texto. Se pidió ponerles su logo, con autorización explícita para agregar la
dependencia que hiciera falta — que es lo que `CLAUDE.md` exige preguntar.

`lucide`, la fuente de iconos del proyecto desde `ADR-0020`, **no sirve para
esto**: retiró los iconos de marca de su set. Se comprobó contra el paquete
instalado, no de memoria: `Phone`, `Mail`, `TriangleAlert`, `CircleCheck`,
`MapPin`, `Truck` y `Clock` existen; `Facebook` e `Instagram` no.

La fuente razonable es `simple-icons`: mantiene 3.000 marcas, las actualiza
cuando una se rediseña, y el paquete es CC0-1.0. Pero al abrirlo antes de
instalarlo aparecieron dos hechos que deciden la forma de la solución:

- **`index.mjs` pesa 5,2 MB**, con todas las marcas como constantes en un solo
  archivo, y cada una con un *getter* (`get svg()`). Confiar en que el
  empaquetador lo sacuda entero es confiar en algo que no se midió.
- **`simple-icons/icons/*` expone archivos `.svg`, no módulos.** No hay un
  `import { siFacebook } from 'simple-icons/icons/facebook'`: el único camino JS
  es el archivo de 5,2 MB.

Para **tres** logos, eso es una dependencia de producción que pesa más que el
sitio. Y contradice dos cosas ya escritas: la regla de `iconos.ts` ("solo se
agrega un icono cuando hay una pantalla que lo pide") y el propio `ADR-0020`,
donde 38,6 kB en el paquete inicial bastaron para mover un componente entero de
sitio.

## Decisión

**`simple-icons` entra como `devDependency`, y de ella sale un archivo
generado.**

- `tools/generar-iconos-marca.mjs` lee los `.svg` de
  `node_modules/simple-icons/icons/`, extrae el `path` y el título de las tres
  marcas que el sitio enlaza, y escribe
  `apps/web/src/app/shared/ui/icono/marcas.generado.ts` — **4,5 kB**, tres
  cadenas. Se corre con `npm run iconos-marca`.
- El archivo es generado y **no se edita a mano**, con el mismo trato que
  `packages/marca` recibe de `copiar-marca.mjs` y que `tokens.css` tiene por la
  regla dura #3.
- El generador **falla** si un `viewBox` no es `0 0 24 24`. Escalar un logo en
  silencio lo deforma, y un logo deformado es un problema de marca, no de CSS.
- `shared/ui/icono/ts-icono-marca` los pinta, y es **otro componente**, no un
  modo de `ts-icono`. Un logo de marca es un `path` relleno; el `<svg>` de
  `ts-icono` es de trazo (`fill="none"`, `stroke="currentColor"`, grosor 1,5), y
  pasar una silueta por ahí la deja invisible o la convierte en un contorno.
  Añadirle un `[relleno]` habría convertido un componente con una regla en uno
  con una excepción.
- Lo que sí se comparte, porque no es decoración: caja de 24, `currentColor`,
  tamaño desde la escala de espacio, y `aria-hidden` con `focusable="false"`.
  **El logo va acompañando al nombre, nunca en su lugar**: es `aria-hidden`, así
  que un enlace de solo logo se queda sin nombre accesible.

## Alternativas

**Copiar los tres `path` a mano, sin dependencia.** Es lo que había antes de
preguntar, y funciona. Se descartó porque un logo copiado se queda viejo sin que
nadie se entere: Instagram y Facebook se rediseñan, y entonces hay que volver a
buscar el SVG bueno a mano. Con la librería pinneada, actualizarlos es
`npm update simple-icons && npm run iconos-marca`.

**`simple-icons` en producción, confiando en el *tree shaking*.** Es lo que la
mayoría de proyectos hace. Se descartó por lo medido arriba: 5,2 MB de entrada
JS que el empaquetador tiene que analizar en cada build, y un *getter* por icono
que no garantiza que la constante se pueda descartar. No se midió el resultado
final porque no hacía falta: el archivo generado deja el problema en cero.

**Los logos como archivos SVG estáticos en `assets/`.** Pierde el color
heredado: un `<img>` no hereda `currentColor`, así que en tema oscuro habría que
servir dos copias de cada logo o teñirlo con filtros CSS.

## Consecuencias

- El bundle de producción carga tres cadenas de `path`. `simple-icons` no está
  en el grafo de producción y **no se puede importar desde `apps/web`** sin que
  el lint de dependencias lo note — es una `devDependency` de la raíz.
- **Un rediseño de marca no lo detecta nadie automáticamente.** Hay que subir la
  versión y regenerar; es el mismo precio que se aceptó para el calendario de
  festivos en `ADR-0024`, y por el mismo motivo: explícito y en un comando.
- Una regeneración a medias —un `path` vacío, un selector que dejó de encajar—
  pintaría un `<svg>` sin dibujo sin que nada fallara. Lo cubre una prueba:
  las tres marcas traen un `path` que empieza en `M` y mide más de 100
  caracteres.
- El registro de `iconos.ts` creció con los cinco iconos de interfaz que pidieron
  las pantallas de este cambio: teléfono, correo, ubicación, horario y envío.
  Sigue vigente la regla de que no se agregan "por si acaso".
