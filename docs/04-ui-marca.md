# Interfaz y marca

El sistema visual ya existe y está cerrado. Viene de dos kits entregados
fuera del monorepo y se vendoriza a mano en `packages/marca`. Este documento
explica cómo se instala en Angular y qué no se puede tocar.

## De dónde viene

Dos entregas, cada una con su alcance:

- **`tecno-sport-marca`** — el manual de marca completo: logo en todas sus
  variantes (positivo, negativo, mono, isotipo, vertical, horizontal, sobre
  grafito), iconografía de app (iOS/Android), plantillas de redes sociales y
  la versión de imprenta. Es la identidad de la empresa, no depende de que
  exista un sitio web.
- **`tecnosport-kit-ui`** — el kit de interfaz web. Extiende
  `marca/tokens.json` sin reinventar ningún color ni tipografía: agrega lo
  que una interfaz necesita y una identidad no tiene — estados (hover,
  pressed, foco, deshabilitado), modo oscuro derivado, breakpoints, medidas
  de header y las tres tipografías autoalojadas en `.woff2`.

Las dos llegan como carpetas sueltas (por eso `tecnosport-kit-ui/` y
`tecno-sport-marca/` están en `.gitignore`: son un punto de entrega, no algo
que se commitea tal cual). Lo que el sitio realmente usa se copia a mano a
`packages/marca`, y cada entrega queda archivada ahí mismo como `.zip` con la
fecha, para no perder la procedencia. `packages/marca/dist/app`,
`dist/imprenta`, `dist/raster` y `dist/social` ya están vendorizados pero
ningún proyecto los usa todavía — quedan listos para cuando exista la app
móvil o una pieza de marketing/impresión (`CLAUDE.md`: "la app móvil viene
después").

**Cómo resincronizar cuando llega una entrega nueva:** copiar a
`packages/marca` solo lo que cambió, sin tocar `tokens.css`/`fuentes.css`
salvo que `tokens.json` haya cambiado de verdad (ver "Lo que no se toca" más
abajo). No hace falta correr `generador/kit_ui.py` si ninguna decisión de
diseño cambió — la mayoría de entregas son activos nuevos (más variantes de
logo, más plantillas), no cambios de tokens.

## Qué se copia y adónde

```
packages/marca/fuentes/     -> apps/web/src/assets/marca/fuentes/  (con licencias)
packages/marca/fuentes.css  -> apps/web/src/assets/marca/fuentes.css
packages/marca/tokens.css   -> apps/web/src/assets/marca/tokens.css
packages/marca/logo/*.svg   -> apps/web/src/assets/marca/logo/
dist/web del kit de marca   -> apps/web/public/  (favicon, manifest, apple-touch-icon)
```

La copia es un paso del build, no una acción manual. En `angular.json`, en este
orden:

```json
"styles": [
  "src/assets/marca/fuentes.css",
  "src/assets/marca/tokens.css",
  "src/tailwind.css",
  "src/styles.scss"
]
```

`src/tailwind.css` va **después** de `tokens.css` y no es negociable: las
utilidades de Tailwind referencian variables que ya tienen que estar declaradas
cuando llegan. Ver "Cómo se escriben los estilos" más abajo.

Los tokens son CSS puro, así que atraviesan el encapsulamiento de estilos: dentro
de cualquier componente `var(--color-primario)` funciona sin `::ng-deep`.

Las licencias OFL se distribuyen con las fuentes. Es condición de la licencia, no
un detalle.

## Las tipografías van recortadas al alfabeto latino

Desde el 22 de septiembre de 2026 los `woff2` del kit **no son los que sube
Google**: se recortan al rango `latin` + `latin-ext` antes de comprimir. Las
familias vienen con cirílico, griego y vietnamita dentro, y este sitio no escribe
ni una letra de eso — IBM Plex Sans pesaba 225 KB y pesa 92. Las cuatro juntas
pasaron de 487 a 271 KB.

Se recorta a `latin-ext` y no solo a `latin` porque el catálogo lo escriben
proveedores: un nombre de producto con una letra centroeuropea no puede salir en
tofu por ahorrar 8 KB. El rango vive en `RANGO_LATINO`, en
`packages/marca/generador/fuentes.py`, y ahí es donde se añade lo que falte.
Nunca se edita el `.woff2`.

**Esto necesita `fonttools` y `brotli`** (`pip install fonttools brotli`), y no
solo para regenerar: `generador/comprobar_fuentes.py` mira el `cmap` de cada
archivo guardado y exige los caracteres que el sitio escribe, más que una familia
variable conserve su eje `wght`. Es la tercera propiedad de `npm run kit`, y sin
la librería **no se puede comprobar**: en local avisa, en integración continua
falla, porque "no comprobado" no es "está bien". El trabajo de web las instala.

Un recorte de más no rompe nada que se vea —el navegador dibuja la eñe con la
fuente de respaldo y la palabra sale con una letra de otra familia—, y por eso
tiene guardián en vez de confianza.

Cuando el recorte se aplica sobre lo ya guardado y no sobre una descarga nueva,
va `python3 generador/fuentes.py --out fuentes --desde-local`: así el cambio no
arrastra además la versión de hoy de cada familia, que es otra cosa y se mezcla
mal en el mismo commit.

Y cuando lo que cambia es una **línea del `@font-face`** y no los archivos, va
`python3 generador/fuentes.py --out fuentes --rehacer-css`: reescribe
`fuentes.css` leyendo el que ya hay —de ahí saca familia, archivo y peso de cada
cara— y no toca un solo `woff2` ni abre la red. Existe porque `fuentes.css` no se
edita a mano y `--desde-local` dice explícitamente que el CSS no cambia: sin esta
bandera, cambiar una línea obligaba a volver a bajar las familias enteras.

## Las tipografías no hacen esperar ni cambian a mitad de lectura

Desde el 23 de septiembre de 2026 las cuatro caras llevan
**`font-display: optional`**, no `swap`. La diferencia se ve en una frase: con
`swap` el navegador pinta con la fuente de respaldo y **cambia** cuando llega la
de marca; con `optional`, si no llegó a tiempo, esa visita se queda con el
respaldo y la de marca entra desde la caché en la siguiente.

El motivo es medido, no estético. Cada tipografía que aterriza con `swap` obliga
a **rehacer el layout de la página entera**: en la traza de legales, Archivo
termina de bajar en `t+445,7` y 1,7 ms después hay un `Layout` de 10 ms con 278
de 285 objetos sucios; IBM Plex Sans termina en `t+460,6` y 0,8 ms después hay
otro de 43 ms. Al cambiar a `optional`, el estilo y layout de esa pantalla pasó
de 731 a 461 ms, y el de la ficha de 546 a 396 — sin solape entre las bandas de
las dos corridas.

**Lo que cuesta:** en una primera visita lenta el sitio se ve con la familia de
respaldo, no con Archivo. Es una decisión de marca tomada con esa contrapartida
delante. Ver `ADR-0059`.

## Lo que no se toca

- **`tokens.css` y `fuentes.css` son generados.** Se edita `tokens.json` y se
  regenera. Un cambio a mano lo borra el siguiente regenerado.
- **Los `.woff2` también son generados**, y desde el recorte no basta con que se
  regeneren igual: tienen que seguir escribiendo lo que el sitio escribe. Ver
  arriba.
- **La marca corta; la interfaz redondea.** Fue "radio 0 en todo, ninguna
  esquina redondeada en ningún componente" hasta el 24 de septiembre de 2026.
  Hoy el chaflán se reserva a la marca y los controles y superficies del sitio
  redondean con la escala del kit — `rounded-sm` (6 px, el segmento de dentro
  de un grupo), `rounded-md` (8 px, el control suelto: botón, campo,
  alternador), `rounded-lg` (12 px, la superficie que contiene controles) y
  `rounded-completo` (la píldora y el botón circular). **Un elemento lleva una
  cosa o la otra, nunca las dos.** El porqué y lo que cuesta están en
  `ADR-0060`.
  Las cuatro utilidades salen de `--radio-*`, no de la escala de Tailwind, que
  sigue borrada: `rounded-xl` no existe. Lo que sobrevive y la regla sigue
  prohibiendo es el literal, `rounded-[10px]`, porque es un píxel suelto. Se
  audita con `grep -rn "rounded-\[" apps/web/src`. Y `rounded-full` sobrevive
  también —es estática de Tailwind, no sale de la escala— pero se escribe
  `rounded-completo`: hacen lo mismo y solo una es rastreable al token.
  **La migración está a medias y a propósito**: el cambio entró por los dos
  alternadores del encabezado, y `ts-boton`, `ts-tarjeta-producto`,
  `ts-dialogo` y el enlace de salto siguen con `.chaflan`. Se migran cuando se
  toquen, no de una sentada. El hero salió de esta lista el 25 de septiembre de
  2026 sin migrarse: el carrusel que lo sustituyó **no lleva chaflán de ninguna
  clase**, ni el del marco de la foto ni el corte de la banda. Una deuda que ya
  no existe en una lista de pendientes envía a alguien a buscar lo que no está.
- **El chaflán a 45 grados** en la esquina superior izquierda y la inferior
  derecha es la firma de la marca. Se aplica con la clase `.chaflan`, y `--ch`
  controla el tamaño. Va en el logo y en las piezas gráficas — banners,
  portadas de redes, papelería, empaque, recortes de fotografía.
- **El chaflán se desactiva en `:focus-visible`** porque `clip-path` recorta el
  anillo de foco. Está resuelto en `tokens.css` y es deliberado.
- **La regla del ámbar:** `#F5B301` es una sola cosa por pantalla y solo como
  relleno con texto grafito encima. Sobre blanco da 1.85:1. Nunca como texto ni
  como ícono sobre fondo claro.
  **Y no tiene excepciones.** Tuvo una, de un día: la banda de portada desplegó
  el ámbar en cuatro pasos —`--color-acento-2`, `-3` y `-4`, que el generador
  deriva aclarando hacia la superficie— para dar un botón a cada línea de
  negocio. `ADR-0064` la retiró en cuanto se vio en pantalla: el cuarto paso
  parece un botón deshabilitado, y cuatro tonos ordenan cuatro líneas que valen
  lo mismo. Los tres tokens siguen en el kit **sin usarse** — se deja anotado
  aquí para que nadie los encuentre y crea que la regla cede donde no cede.
  Cómo se usa el ámbar cuando de verdad es *la* acción de la pantalla: la
  variante `acento` de `ts-boton`, que además cambia el anillo de foco a
  `--color-sobre-acento` (en tema oscuro `--color-foco` es ese mismo ámbar y el
  anillo de la base sería invisible).
- **El logo es monocromo** y va en versión positiva y negativa; el tema decide
  cuál se ve, con `.logo-pos` y `.logo-neg`. En móvil, isotipo.
- **Área de respeto:** un cuarto del alto del isotipo por los cuatro lados.
- **Tamaño mínimo:** logo horizontal 160 px de ancho. Por debajo, isotipo solo.

## Medidas del sistema

| | Valor | Token |
|---|---|---|
| Ancho máximo de contenido | 1200 px | `--ancho-max` |
| Puntos de quiebre | 640 / 1024 / 1280 | `--bp-*` |
| Header escritorio y móvil | 72 y 60 px, fijo | `--header-alto-*` |
| Logo en el header | 34 y 30 px | `--header-alto-logo*` |
| Menú lateral recogido y desplegado | 72 y 288 px | `--ancho-menu-*` |
| Objetivo táctil mínimo | 44 x 44 px | `--control-tactil` |
| Insignia del contador | 20 x 20 px | `--control-insignia` |
| Radio de la interfaz | 6 / 8 / 12 px y píldora | `--radio-*` |

El menú lateral entró el 24 de septiembre de 2026 (`ADR-0062`). Los 72 px del riel
son el objetivo táctil más 14 a cada lado —es lo único que se ve recogido— y los
288 el ancho donde cabe la etiqueta más larga del árbol sin partirse; 288 y no 290
porque es múltiplo de 4, como el resto de la rejilla. La transición entre los dos
dura `--mov-panel` (300 ms), que es una duración nueva para lo que recorre mucha
distancia: a 160 ms ese salto se lee como un parpadeo y no como un despliegue.

El objetivo táctil fue durante un tiempo el único valor de esta tabla sin token:
el SCSS lo escribía como `min-height: 44px` literal en cada control. Se pidió al
kit y hoy sale de `tokens.json` como cualquier otra medida; en la interfaz se usa
con `min-h-tactil`.

Tipografía: Archivo en titulares, IBM Plex Sans en texto e interfaz, IBM Plex
Mono en precios y referencias, con cifras tabulares para que las columnas alineen
solas. Todas SIL OFL, autoalojadas, sin Google Fonts.

## Cómo se escriben los estilos

Hasta la Fase 5 era SCSS por componente, solo con variables de `tokens.css`.
Desde `ADR-0020` es **Tailwind CSS v4 mapeado a los tokens**, y la diferencia
importa menos de lo que parece: la fuente de verdad sigue siendo `tokens.json`.

- **La configuración es CSS, no TypeScript.** Tailwind v4 no tiene
  `tailwind.config.ts`. Todo está en `apps/web/src/tailwind.css`, un archivo con
  más comentario que código a propósito.
- **Las escalas por omisión están borradas.** `bg-red-500`, `p-7`, `text-8xl` y
  `rounded-xl` no existen. Para saber si una utilidad existe de verdad:
  `npm run clases -- <clase>`. Lo que hay son los tokens: `bg-ts-primario`,
  `text-ts-texto-suave`, `p-16`, `text-2xl`, `shadow-md`, `max-w-formulario`.
  **Y desde el 21 de septiembre de 2026 no hace falta acordarse de preguntar**:
  `npm run clases` sin argumentos barre las plantillas, los enlaces `[class.x]` y
  los literales de clases de los `.ts`, y corre dentro de `npm run verificar`.
- **`p-16` son 16 px**, no 64 como en Tailwind por omisión. La escala de espacio
  se nombró por el píxel para que la utilidad sea rastreable al token
  (`--esp-16`) de un vistazo.
- **Los colores llevan prefijo `ts-`** porque el nombre del token ya ocupa el
  suyo: `--color-primario` es el token, `--color-ts-primario` es lo que Tailwind
  lee para generar `bg-ts-primario`.
- **La escapatoria permitida es `h-[var(--token)]`.** Un valor arbitrario que
  apunta a un token está bien; `h-[72px]` no.
- **El chaflán sigue siendo `.chaflan` de `tokens.css`.** Ninguna utilidad de
  Tailwind reproduce un `clip-path` de polígono, ni el detalle de desactivarse en
  `:focus-visible`.
- **Preflight está deliberadamente fuera** (`ADR-0020`). Consecuencia práctica:
  no hay reset global, así que un componente que necesite `box-sizing: border-box`
  lo pide con `box-border`, como hace `ui/campo`.

**Utilidades propias** (`src/tailwind.css`), para lo que las de Tailwind no
cubren o para lo que no debe olvidarse:

- `anillo-foco`, `anillo-foco-sobre-marca`, `anillo-foco-sobre-acento` — el
  anillo de foco en una sola clase. Tres y no una porque **el color tiene que
  contrastar con la superficie**: en tema claro `--color-foco` y `--color-marca`
  son el mismo grafito, y en oscuro `--color-foco` y `--color-acento` son el
  mismo ámbar. El anillo del pie y el del enlace de salto eran invisibles por
  eso.
- `esqueleto` — el degradado de carga con sus `@keyframes`, apagado bajo
  `prefers-reduced-motion` y bajo `[data-movimiento="reducido"]`.
- `girando` — la vuelta del anillo de carga, con los mismos dos interruptores que
  el esqueleto. Y por la misma razón: `styles.scss` acorta la *duración* de toda
  animación bajo `[data-movimiento]`, y un anillo que da la vuelta en 0,01 ms es
  un parpadeo, peor que uno quieto. Hay que apagarlo, no acelerarlo.
- `superficie-arrastre` — las tres propiedades que hacen arrastrable el visor
  360, incluida `-webkit-user-drag`, que no tiene utilidad en Tailwind.

**El riesgo que trae este cambio, y hay que conocerlo: una clase que no existe no
falla, simplemente no hace nada.** Pasó dos veces en la propia fase con `min-h-0`
y `min-h-auto`. No hay linter que avise. La única forma de comprobarlo es leer el
CSS compilado — ver `docs/06-testing.md`.

### Lo que el kit no define y el sitio necesita

Salieron a la luz al migrar los estilos, y **ya no son huecos**: se pidieron al
kit y hoy los emite `tokens.json`. Quedan aquí como registro de qué era cada uno
y de dónde venía:

| Token | Para qué | Dónde estaba antes |
|---|---|---|
| `--control-tactil` | objetivo táctil mínimo | `min-height: 44px` en cada control |
| `--control-insignia` | contador del carrito | literal en el encabezado |
| `--ancho-min-filtro` | mínimo de columna de los filtros | `minmax(160px, 1fr)` |
| `--ancho-min-eje` | mínimo de columna de los ejes de categoría | `minmax(200px, 1fr)` |
| `--ancho-min-tarjeta` | mínimo de columna de las tarjetas | `minmax(220px, 1fr)` |
| `--ancho-min-vista-previa` | vista previa de imagen en el panel | `width/height: 200px` |
| `--imagen-miniatura` | miniatura de la galería del panel | `size-[var(--imagen-miniatura)]`, 96px |
| `--mov-*` y `--curva-*` | duraciones y curvas | literales en `tailwind.css` |

**`--imagen-miniatura` no es un `--ancho-min-*` pequeño**, y por eso vive en su
propio grupo (`imagenes_px` en el `tokens.json` del kit, 21 de septiembre de
2026). Un ancho mínimo es el valor por debajo del cual algo deja de ser usable,
para rejillas que se auto-ajustan; una miniatura es un tamaño **fijo**, y lo que
decide su valor es cuántas caben en una fila. Confundir las dos cosas fue dejar
ocho imágenes de 200 px apiladas en una columna de formulario: a 96 px entran
tres por fila en `--ancho-formulario`.

Sobre los `--ancho-min-*` se preguntó explícitamente si eran **un solo valor de
sistema**, porque tres valores elegidos a ojo en tres pantallas suelen serlo. La
respuesta fue que no: que `eje` y `vista-previa` coincidan hoy en 200 px es
casualidad, porque se encogen por motivos distintos y van a divergir. Son cuatro
tokens a propósito.

Las curvas se declaran como `cubic-bezier` explícito y no como `ease-out`: el
`ease-out` de CSS no coincide con el de ninguna guía de movimiento, y usar el
nombre esconde esa diferencia.

**Animación: entrada y salida.** El menú móvil usa las animaciones nativas de
Angular 22. La entrada va con la forma de clase (`animate.enter`). La salida
necesita la **forma de evento**: con `animate.leave="..."` la clase se aplica y
la animación corre, pero el elemento se queda en el DOM y visible; con el evento
llega `animationComplete()` y el borrado depende de llamarlo. Con "reducir
movimiento" el camino se salta entero.

Los puntos de quiebre y las tres medidas en `ch` (`min-w-[2ch]`,
`min-w-[12ch]`) no cuentan como huecos: los primeros porque una media query no
puede leer una propiedad personalizada de CSS, y los segundos porque un conteo
de caracteres no es una medida de diseño.

## Componentes compartidos

Cada uno con sus estados (normal, hover, activo, foco, deshabilitado, cargando),
en claro y en oscuro, y operable con teclado.

`shared/ui/` es la capa de primitivas **tontas**: reciben `input()` y emiten
`output()`, y **no dependen de Transloco, de TanStack Query ni de ningún
dominio**. Los textos llegan ya traducidos por quien las usa. `shared/` a secas
guarda los componentes compartidos que sí traducen o conocen un modelo, y que
todavía no se han partido.

**En `shared/ui/` — las primitivas tontas:** `cn` (fusión de clases) ·
`clases-control` (el aspecto compartido de un control de formulario) ·
`ts-icono` · `ts-boton` (primario, secundario, texto, peligro) · `ts-campo` ·
`ts-select` · `ts-checkbox` · `ts-dialogo` · `ts-pagina-formulario`.

`ts-pagina-formulario` es el cascarón de una pantalla de formulario: ancho de
columna, aire vertical y titular. Estaba repetido **doce veces** —ocho en
`cuenta`, donde las pantallas con estado de éxito lo repiten dentro de la misma
plantilla, y cuatro en `admin`—. Las migas se colocan encima del titular por un
`ng-content select="ts-migas"`, así que quien lo usa solo tiene que ponerlas
dentro. `verificar-correo` no lo usa a propósito: es una pantalla de estado con
tres ramas y una de ellas no tiene título.

**El ancla dentro del campo y la etiqueta escondida.** Desde el 24 de septiembre
de 2026 `ts-campo` y `ts-select` aceptan `icono` —un glifo dentro del control, a
la izquierda, que corre el relleno a `ps-48`— y `etiquetaOculta`, que manda la
etiqueta a `sr-only`. La segunda **tiene un precio y no es gratis**: el
placeholder desaparece al primer carácter, así que quien vuelve a un formulario a
medio llenar ya no tiene en pantalla el nombre del campo, solo el ancla. Vale en
un formulario corto de campos evidentes —entrar, crear cuenta, la barra de
búsqueda del catálogo— y **no** en el checkout, en el panel ni en ningún
formulario largo, donde la etiqueta se sigue viendo. El icono nunca es el nombre
accesible: `ts-icono` pinta `aria-hidden`, y quien nombra el control es la
etiqueta, que sigue existiendo aunque no se vea.

Y **todo `<select>` del sitio va con `appearance-none` y su propia punta de
flecha**. La del navegador no se puede estilar, así que un `<input>` y un
`<select>` uno al lado del otro se veían distintos por más que compartieran
`clases-control.ts` — que es justo lo que ese archivo existe para evitar.

`ts-campo` y `ts-select` envuelven controles nativos distintos pero tienen que
verse idénticos, así que su borde, relleno, objetivo táctil, anillo de foco y
estado deshabilitado salen de `clases-control.ts`. Estaban duplicados en dos
SCSS con los mismos valores.

**En `shared/` — compartidos de verdad, pero no tontos:** `ts-precio` (necesita
el idioma activo para formatear la moneda) · `ts-esqueleto` · `ts-migas` ·
`ts-paginador` · `ts-alternador-idioma` · `ts-alternador-tema` · `ts-visor-360`.

### Los dos indicadores de carga, y cuándo va cada uno

Conviven a propósito desde el 25 de septiembre de 2026, cuando entró el segundo:

- **`ts-esqueleto`** dice **qué** va a aparecer. Se usa donde la forma se conoce
  de antemano: una rejilla de tarjetas, una tabla, una ficha. Diecinueve
  pantallas.
- **`ts-cargando`** dice que **algo está pasando ahora**. Se usa donde no hay
  forma que anticipar: un botón que acaba de pulsarse, un párrafo de estado.

Antes solo existía el primero, y lo segundo se resolvía con texto pelado
("Cargando…") o con nada: los botones que cargan **sin cambiar de texto** —la
mayoría de las acciones de fila del panel— solo movían su `aria-busy`, que lo
dice todo para quien usa lector de pantalla y **nada** para quien mira la
pantalla. Un botón ocupado se veía igual que uno en reposo.

`ts-cargando` sale del "Spinner 4" de TailAdmin, el segundo de los dos botones de
esa tarjeta, con **una diferencia deliberada**: el original parte el anillo en dos
colores, la pista en un gris fijo y el arco en el color de marca. Aquí los dos
salen de `currentColor` y la pista va al 25 %. El componente se pinta sobre cinco
fondos —el grafito del botón primario, el ámbar del de acento, el rojo del de
peligro, el transparente del secundario y el fondo de la página— y un arco de
color fijo desaparece sobre el suyo: ámbar sobre ámbar no se ve. Heredando el
color del texto contrasta exactamente igual que la etiqueta que tiene al lado, en
los dos temas y sin una regla por variante. Quien quiera el ámbar lo pide con
`clase="text-ts-acento"`, como con `ts-icono`.

El giro dura `--mov-giro` (1000 ms), que entró al kit para esto. No se reutilizó
`--mov-lenta` —los 1400 ms del brillo de carga— porque son cosas distintas: aquel
es ambiental y este responde a una acción, y a 1400 ms el anillo se arrastra y
parece que la aplicación se colgó.

**No queda una sola línea de SCSS en el frontend**, salvo `src/styles.scss`, que
conserva la regla global de reducción de movimiento disparada por
`[data-movimiento="reducido"]`. Todo lo demás son utilidades mapeadas a tokens.

**Movidos a la funcionalidad que los usa**, porque nunca fueron compartidos:
`ts-tarjeta-producto`, `ts-etiqueta-stock`, `ts-galeria` y
`ts-selector-variante` a `features/catalogo/presentation/`;
`ts-selector-metodo-pago` a `features/checkout/presentation/`.

**Ya no queda ninguna flecha invertida en `shared/`.** Los cuatro casos que
había —componentes de `shared/` importando `features/*/domain`— resultaron ser
exactamente los componentes que usaba una sola funcionalidad. Moverlos, en vez
de partirlos en tonto más envoltorio, deja la flecha `presentation → domain`
que sí es correcta. **Y el build ya lo impide**: `tools/verificar-capas.mjs`
clasifica `shared/` como una capa propia y le prohíbe importar el `domain`, el
`application`, el `infrastructure` y el `presentation` de cualquier
funcionalidad. No lo verifica ESLint —`eslint-plugin-boundaries` no aplica
nada, ver la regla dura #1 de `CLAUDE.md`— y su `boundaries/include` sigue
apuntando solo a `features/**`, pero eso ya da igual: el guardián que cuenta
corre en `npm run verificar`, y es su primer paso, antes del lint.

**Pendientes de construir:** `ts-radio` · `ts-notificacion`. Se construyen
cuando aparezca el primer consumidor real, no antes.

**`ts-checkbox` ya salió de esa lista, y cómo salió importa.** El consumidor
—la casilla de "reducir movimiento" del pie— existía desde la Fase 4, pero
nadie había mirado cuánto medía: era un `<input type="checkbox">` nativo sin
estilar de **13 × 13 px**, con su `<label>` en 19 px de alto. Eso incumple
WCAG 2.2 AA (2.5.8 exige 24 × 24) y está lejos de los 44 px que pide este
documento — o sea, el control de accesibilidad del sitio era el que fallaba
accesibilidad. No lo vio ninguna prueba, porque jsdom no hace layout, ni el
escritorio, porque el ratón perdona un objetivo pequeño. Salió midiendo el
sitio a 380 px en un navegador.

El componente resuelve tres cosas que la casilla nativa no daba: el objetivo
táctil es **la etiqueta entera** (`min-h-tactil` va en el `<label>`, medido en
158 × 44), la caja se dibuja a 24 px con `appearance-none` y un visto de
Lucide, y `sobreMarca` conmuta al par `sobre-marca` / `marca` —validado en
14,64:1— porque en la franja del pie el anillo de foco normal es invisible en
tema claro. Esa casilla **ya no existe**: se quitó del pie el 25 de septiembre de 2026, a
petición, y con ella se fue el único consumidor de `sobreMarca`. El componente
sigue vivo en el resumen del checkout y en el registro, las dos sobre superficie
clara. La consecuencia de quitarla está escrita en `layout/pie/pie.ts` y vale
repetirla: quien **no** tenga la preferencia puesta en su sistema operativo se
queda sin forma de pedir menos movimiento desde el sitio. La regla de
`prefers-reduced-motion` de `tokens.css` sigue intacta y sigue apagando el
carrusel de portada, el brillo de carga y el anillo; lo que desapareció es el
interruptor propio.

No implementa `ControlValueAccessor`, a diferencia de `ts-campo` y
`ts-select`: su único consumidor no usa Angular Forms, y cablear un CVA que
nadie registra sería el código especulativo que la regla de arriba evita.

**`ts-dialogo` es la excepción a esa regla, y conviene decirlo:** está
construido y probado, pero **ninguna pantalla lo usa todavía**. Se revisó
buscándole uno: no hay un `confirm()` nativo que reemplazar en todo el
frontend, y la única acción destructiva de la interfaz —eliminar una línea del
carrito— confirmarla o no es una decisión de producto, no una deuda técnica que
se pueda saldar programando. Queda a la espera del primer modal real. Mientras
tanto es la única primitiva del sistema que se adelantó a su consumidor.

**`ts-dialogo` está construido sobre `@spartan-ng/brain`**, y es lo único que
usa esa dependencia. La primera versión era un `@if` en línea con
`[cdkTrapFocus]`; funcionaba, pero Spartan resuelve tres cosas que un modal de
verdad necesita: se pinta en un **portal del CDK** —así que ningún `z-index` de
un ancestro puede taparlo—, **bloquea el desplazamiento del fondo** (la
limitación que la versión anterior documentaba sin resolver) y cablea
`aria-labelledby` solo, por `brnDialogTitle`. Cerrar con Escape o pulsando
fuera viene de fábrica, así que el componente ya no necesita silenciar dos
reglas de ESLint para tener un fondo pulsable.

**Sigue siendo la excepción a la regla de "primero el consumidor":** se construyó en
la Fase 2 sin consumidor, por petición explícita. `eliminar(setId)` existe en el
puerto y el repositorio de `captura360`, pero ninguna pantalla lo dispara
todavía, así que el diálogo no se alcanza desde ningún sitio del sitio.

`ts-alternador-idioma` y `ts-alternador-tema` **no pasan por `ts-select`**, y
los dos lo hicieron hasta el 24 de septiembre de 2026. `ts-select` envuelve un
control nativo —resuelve el `<label>` real, el anillo de foco, el objetivo
táctil de 44 px y el `min-inline-size: 0` que evita que la opción más larga
ensanche su columna— y eso sigue valiendo para los once formularios que lo usan.
Lo que cambió es que estos dos dejaron de ser un `<select>`: con dos opciones y
nada más, un desplegable cobra dos clics por lo que un control directo resuelve
en uno. El tema es un botón que alterna; el idioma, un grupo segmentado que
muestra los dos códigos con el activo marcado.

Los dos comparten los 44 px de alto porque van uno al lado del otro en el
encabezado, y desde el 24 de septiembre de 2026 los dos redondean: el tema es un
círculo (`rounded-completo` sobre una caja cuadrada de 44 px) y el idioma una
pista de `rounded-md` con la pastilla del activo encima, que es el control
segmentado de la plantilla de referencia. El idioma perdió el borde y el
`border-l` que separaba los segmentos: esa forma no los lleva.

**Lo único de la referencia que no se copió es el relleno de 2 px** que deja la
pastilla flotando dentro de la pista. Allá la pista mide 40 px y los segmentos
36; aquí la pista mide los 44 del objetivo táctil, así que ese relleno saldría
de los segmentos —dejándolos por debajo del mínimo de la tabla de medidas— o
empujaría el grupo a 52 px y lo descuadraría con el botón del tema. Con relleno
0 y el mismo radio en pista y pastilla, las esquinas encajan sin dejar un filo a
la vista.

**Y la pastilla lleva los dos fondos, uno por tema.** Cuál de las dos superficies
"sube" cambia con el tema: en claro la que brilla es `superficie` (blanco) sobre
`superficie-alt` (gris), y en oscuro el orden se invierte —`fondo` < `superficie`
< `superficie-alt`—, así que con un solo par la pastilla quedaba más oscura que
la pista y el activo se leía hundido, con el inactivo pareciendo el
seleccionado. De ahí los dos `oscuro:`. **Ninguna prueba lo atrapa**: es de la
lista de la regla dura #8, se ve en el navegador o no se ve.

Y los dos dicen **a dónde lleva el clic**, no dónde estás: la luna aparece en
tema claro, y el botón "EN" aparece cuando el sitio está en español. El idioma
además muestra el actual, marcado con `aria-current` y sin ser un control, que
es el patrón de `ts-migas` para la página en la que ya estás.

**`ts-menu-acciones` es el menú de tres puntos de una fila**, y entró con la
lista de productos del panel el 25 de septiembre de 2026. Va sobre
`@angular/cdk/menu` y no sobre un `@if` con un `<div absolute>` por dos motivos
que no son de gusto: una tabla ancha vive dentro de un `overflow-x-auto`, y ahí
un panel posicionado en la fila se corta por el borde de la caja; y el teclado
—`role="menu"`, flechas, Escape, foco que vuelve al disparador, cierre al pulsar
fuera— es fácil de hacer a medias y en un menú se nota justo con lector de
pantalla.

Dos cosas que conviene saber antes de tocarlo. **Las opciones llegan como dato,
no proyectadas con `<ng-content>`**: `CdkMenu` encuentra las suyas con una
consulta de contenido, y lo que entra por el `ng-content` de un componente de
envoltura no es contenido suyo — el menú se habría pintado sin una sola opción
navegable, con las flechas muertas y sin que nada fallara. Y **el CDK necesita su
hoja de posicionamiento**, que `src/styles.scss` importa: sin ella
`.cdk-overlay-container` es un `<div>` al final del `<body>` sin `position` ni
`z-index`, y el panel aparece al pie del documento. `ts-dialogo` no la
necesitaba porque se posiciona solo, y por eso nadie la había echado de menos.

**Las secciones del panel son pestañas, y son navegación.** `features/admin/marco/`
tiene el contenedor —barra arriba, `<router-outlet>` debajo— y la barra en sí.
Se ve como el control segmentado de la referencia (riel en `superficie-alt`, la
activa elevada en `superficie` con `shadow-sm`) pero es un `<nav>` con enlaces y
`aria-current="page"`, **no un `role="tablist"`**: cada pestaña lleva a una URL
distinta, y `tablist` prometería paneles que se intercambian en el sitio y un
`tabpanel` que no existe. Cuál está activa la decide una función pura
—`pestanaActiva`— y no un `routerLinkActive` por enlace, porque hay empates que
`routerLinkActive` no sabe resolver: `/admin/productos/existencias` encaja con
"Productos" y con "Existencias" a la vez. Gana la coincidencia más larga.

Si un componente necesita un valor que no está en los tokens, el sistema está
incompleto: se agrega a `tokens.json` con nombre, no se escribe un píxel suelto
ni un valor arbitrario en la plantilla.

## Iconografía

El kit de `packages/marca` **no trae iconos de interfaz**: solo logos, isotipo y
los iconos de aplicación de iOS y Android. Hasta la Fase 4 no hizo falta ninguno
— el primero apareció al reemplazar el texto "Carrito" del encabezado por su
icono.

Ahí se decidió dibujarlos a mano, sin librería, con el umbral para revisarlo en
"más de seis u ocho". **Esa decisión se revirtió en la Fase 2 del stack de UI**
(2026-09-07, `ADR-0020`) por petición explícita, antes de alcanzar el umbral.
Ahora:

- **Los trazos vienen del paquete `lucide`**, no de `lucide-angular`. Ese último
  declara `@angular/core: 13.x - 21.x` y el proyecto va en la 22, así que
  instalarlo exigiría `--legacy-peer-deps` para todo el monorepo. `lucide` no
  declara ningún peer: entrega el icono como datos, `[etiqueta, atributos][]`.
- **`shared/ui/icono/ts-icono`** los pinta. Recorre los trazos y **liga cada
  atributo explícitamente por etiqueta** (`path`, `circle`, `rect`, `line`,
  `ellipse`, `polyline` — las seis que usa el set entero, verificadas contra los
  1.815 iconos). No aplica el registro de atributos a ciegas y **no usa
  `innerHTML` ni `bypassSecurityTrustHtml`**: aplicar atributos de un paquete de
  terceros sobre un elemento del DOM sin lista blanca es una superficie de
  inyección que un ecommerce no necesita.
- **`shared/ui/icono/iconos.ts` es el único archivo que importa de `lucide`.** El
  set que el sitio usa queda auditable en un sitio y se reexporta con nombre,
  para que el empaquetador descarte lo que no se use. **Solo se agrega un icono
  cuando hay una pantalla que lo pide.**
- Las reglas de dibujo no cambian y las impone `ts-icono`, no cada plantilla:
  `viewBox` de 24, trazo `1.5`, extremos redondeados,
  `stroke="currentColor"` —nunca un HEX, así hereda el color del texto y
  funciona en claro y en oscuro sin una regla aparte—, tamaño desde la escala de
  espacio (`size-24` es `var(--esp-24)`) y `aria-hidden="true"` con
  `focusable="false"`, porque **el icono nunca es el nombre accesible**: eso le
  toca al control que lo contiene, con su `aria-label` traducido.

### Logos de marca: otro componente, y una dependencia que no llega a producción

Los logos de Facebook, Instagram y WhatsApp del pie **no** son iconos de
interfaz, y no salen de `lucide`: Lucide retiró los iconos de marca de su set.
La decisión, del 10 de septiembre de 2026, está en `ADR-0026`. En corto:

- **`simple-icons` entra como `devDependency`, no como dependencia de
  producción.** Su único punto de entrada JS es un `index.mjs` de **5,2 MB** con
  las miles de marcas en un archivo, y `simple-icons/icons/*` expone `.svg`, no
  módulos: no hay import por icono. Para tres logos, eso no entra al grafo de
  producción.
- **`npm run iconos-marca`** extrae los tres `path` a
  `shared/ui/icono/marcas.generado.ts` (4,5 kB). Es un archivo **generado**: no
  se edita a mano, se regenera — mismo trato que `packages/marca` con
  `copiar-marca.mjs`. Se corre al subir la versión de `simple-icons` o al
  agregar una marca, que es cuando una marca se rediseña.
- **`shared/ui/icono/ts-icono-marca` los pinta, y es otro componente a
  propósito.** Un logo se distribuye como un `path` **relleno**; pasarlo por
  `ts-icono` —`fill="none"`, trazo de 1,5— lo dejaría invisible o deformado.
  Meterle un modo a `ts-icono` habría convertido un componente con una regla en
  uno con una excepción.
- **El logo va con el nombre, no en su lugar.** Es `aria-hidden` como cualquier
  icono, así que un enlace de solo logo se queda sin nombre accesible; y un
  icono suelto es peor objetivo para quien no reconoce la marca.
- Las marcas registradas siguen siendo de sus titulares. Aquí se usan para
  enlazar los perfiles propios del negocio, no como respaldo de nadie.

## Modo oscuro

Atributo `data-tema="oscuro"` en `<html>`. **Dos opciones para el usuario:
claro y oscuro**, alternadas con un botón. La preferencia se persiste en una
cookie y se resuelve en el servidor durante el SSR.

Hubo una tercera opción, "seguir al sistema", que guardaba esa intención en la
cookie y se resolvía tarde. Se quitó el 24 de septiembre de 2026 al pasar el
control de `<select>` a botón de alternar. **El `prefers-color-scheme` no se fue
con ella**: sigue decidiendo qué ve quien llega sin cookie, en el script inline
del `<head>`, antes del primer pintado y sin destello. La diferencia es que
ahora es un valor inicial y no una preferencia que se persiste — en cuanto
alguien toca el botón, manda la cookie.

La cookie dura un año, así que quien eligió "Sistema" cuando existía la sigue
trayendo. No se migra ni se limpia: `leerTema` la ignora por no ser un tema
válido, el script inline resuelve por `prefers-color-scheme` —que es justo lo
que esa persona pidió— y el primer clic la reemplaza. Hay una prueba en
`tema-ssr.spec.ts` que lo fija.

**El dueño de esa política es `core/tema/ServicioTema`** (Fase 1 del stack de
UI): persiste la cookie y escribe el atributo. Antes vivía dentro de
`shared/ts-selector-tema`, que así cargaba con dibujar un select, escribir una
cookie y tocar el DOM del documento a la vez. Un componente compartido no decide
la política de persistencia del sitio. El servicio se reparte el trabajo con
`tema-ssr.ts` (el servidor, cuando hay cookie) y el script inline de
`index.html` (cuando no la hay); cuando el servicio corre, **ya hay un tema
aplicado**.

**El control no guarda estado, y no es una omisión.** `shared/ts-alternador-tema`
no tiene señal del tema actual ni `afterNextRender` que la corrija: `data-tema`
en `<html>` *es* el estado aplicado, lo escriben tres sitios distintos, y una
señal sería una segunda copia que puede separarse de la primera. Al pulsar, el
servicio lee el atributo y escribe el contrario. Qué icono se ve lo decide el
CSS con el variant `oscuro:` —los dos iconos están siempre en el DOM y uno se
tapa—, el mismo patrón que el logo positivo y negativo del encabezado. El motivo
es concreto: `conTemaAplicado` inyecta `data-tema` por reemplazo de cadena sobre
el HTML **ya construido**, así que el servidor no conoce el tema mientras
renderiza y cualquier cosa atada a una señal parpadearía al hidratar.

Por lo mismo el botón se llama "Cambiar el tema" y no "Activar modo oscuro": el
nombre accesible sí lo pinta Angular, y uno que dependiera del tema saldría
mintiendo del servidor hasta hidratar. El destino lo dice el icono, que se
resuelve sin JavaScript.

**Tailwind no añade un segundo mecanismo.** El variant propio se llama `oscuro:`
y se cuelga del mismo `data-tema`, nunca de una clase `.dark`. Y en la práctica
casi no hace falta: como los tokens se redefinen bajo `[data-tema="oscuro"]` y
`@theme inline` deja la `var()` dentro de la utilidad, `bg-ts-superficie` cambia
de color **sin un solo `oscuro:` en el componente**. Verificado en el navegador
(`ADR-0020`). El variant queda para lo que de verdad difiere entre temas.

Decisión ya tomada en el kit: en oscuro las franjas grandes (hero, menú móvil,
pie) no se vuelven ámbar, se quedan en grafito elevado. El ámbar manda en botones
y enlaces. Si todo se tiñe de ámbar, muere la regla de una sola cosa por pantalla.

## Accesibilidad, mínimos exigibles

- WCAG 2.2 nivel AA. Cualquier color nuevo se verifica antes de entrar, y ahora
  hay con qué: **`npm run contrastes`** calcula los 24 pares que el sitio usa de
  verdad, en claro y en oscuro, contra 4.5:1 (texto) y 3:1 (componentes de
  interfaz). La lista de pares vive en `tools/verificar-contrastes.mjs` y hay
  que ampliarla cuando alguien invente una combinación nueva.

  **Los 24 pares pasan.** Dos no lo hacían y se corrigieron **en el origen**,
  editando `tokens.json` y regenerando con `generador/kit_ui.py` — la vía que
  este mismo documento manda, y que no rompe la regla de no editar `tokens.css`
  a mano:

  1. **`--color-exito` era `#14804A`** y sobre `--color-superficie-alt` daba
     4.20:1, por debajo de 4.5. Es la etiqueta "Disponible", a 12 px. Ahora es
     `#116B3E`, que da **5.54:1** y queda en la misma familia que los otros
     colores señal del kit (error 6.04, aviso 4.80). Se eligió con margen y no
     el mínimo que pasaba (`#137846`, 4.66) para que un retoque futuro de las
     superficies no lo tumbe.
  2. **El tema oscuro no redefinía `--color-sobre-deshabilitado`**, así que el
     texto de un control deshabilitado se quedaba con el grafito del tema claro
     sobre el gris oscuro: 2.31:1. **Era un fallo del generador**, no un valor
     mal elegido: derivaba `o_deshabilitado` pero nunca calculaba ni emitía su
     par de texto. Corregido en `kit_ui.py` con la misma fórmula que usan los
     otros `o_sobre_*`; ahora da 6.80:1.

     Que WCAG exima a los controles deshabilitados no lo hacía aceptable: era
     ilegible.

- Foco visible en el cien por ciento de los elementos enfocables. Nunca
  `outline: none` sin reemplazo.
- Cero desbordamiento horizontal a 380 px de ancho.
- El texto aguanta 200 por ciento de zoom sin romper la maqueta.
- Jerarquía de encabezados sin saltos, un solo `h1` por página.
- Controles del usuario en el pie: tamaño de texto, contraste alto y reducción de
  movimiento, que además respetan `prefers-reduced-motion` y
  `prefers-color-scheme`. **Los tres están hoy sin construir**, y el de movimiento
  vuelve a la lista: estuvo cerrado desde el 2026-09-04 —checkbox persistido en
  `localStorage`, con `styles.scss` repitiendo bajo `[data-movimiento="reducido"]`
  la misma regla que `tokens.css` ya aplica bajo `prefers-reduced-motion`— y **se
  quitó del pie el 2026-09-25**, al rehacerlo, a petición. Quien no tenga la
  preferencia puesta en su sistema operativo se queda sin forma de pedirla aquí;
  la regla automática sigue intacta y los ganchos `[data-movimiento]` siguen
  puestos, así que devolverlo es volver a poner un control que escriba el atributo
  — y el sitio para ponerlo probablemente no sea el pie, sino junto a los otros
  dos. **Esos dos siguen con su propio bloqueo real:**
  - **Contraste alto** necesita una paleta que `tokens.json` no define —
    no es una decisión que le toque tomar a quien programa.
  - **Tamaño de texto**: los tokens `--texto-*` de `tokens.css` están en `px`,
    no en `rem` (verificado leyendo el archivo generado, no de memoria) —
    escalar el `font-size` de la raíz no los mueve. La única vía sin editar
    `tokens.css` a mano (regla dura #3) sería `zoom`/`transform`, que rompe
    el layout y no es soporte real de accesibilidad. El zoom nativo del
    navegador ya cumple el punto de arriba ("el texto aguanta 200% de
    zoom"); un control propio de verdad exige rehacer esos tokens a `rem`
    en el kit — cambio de diseño, no de programación.
- Navegación completa por teclado, con enlace de salto al contenido.
- **El anillo de foco de los componentes de `shared/ui` se verifica en el
  navegador, no en Vitest.** Las utilidades `focus-visible:` solo aplican cuando
  el navegador considera el foco "visible", y eso depende de la modalidad
  (teclado sí, ratón en un `<input>` no siempre): jsdom no lo reproduce.
  Comprobado a mano en la Fase 2 — con Tab real, `outline-color` resuelve a
  `--color-foco`, que en oscuro es ámbar.
- **El CDK está en uso solo dentro de `shared/ui/dialogo`**, y desde que el
  diálogo se construyó sobre `@spartan-ng/brain` no se toca directo:
  `brn-dialog` monta el portal y la trampa de foco del CDK por dentro. La
  versión anterior cableaba `[cdkTrapFocus]` con `cdkTrapFocusAutoCapture` a
  mano, y el efecto visible es el mismo — el foco entra al diálogo al abrir y
  vuelve al elemento anterior al cerrar.
  **Con dos límites que hay que leer juntos, porque esta viñeta llegó a
  afirmar más de lo que se había comprobado:** ese movimiento no lo prueba
  Vitest —el `InteractivityChecker` del CDK mide layout y en jsdom todo mide
  cero— y **tampoco se ha visto en el navegador, porque no hay pantalla que
  abra un diálogo**. O sea: la trampa de foco de este proyecto está sin
  verificar de las dos formas que valen. Se verifica el día que `ts-dialogo`
  tenga su primer consumidor.
- El visor 360 se opera con flechas y con botones visibles, no solo arrastrando.

## El carrusel de portada

Lo primero que ve quien llega, y la única pieza del sitio que ocupa la pantalla
entera de borde a borde. Vive en `features/catalogo/presentation/portada/hero/`,
en `ts-carrusel-hero`.

**Cuatro piezas, una por línea de negocio**, que pasan solas cada cuatro segundos
—fueron cinco hasta el 25 de septiembre de 2026— y **también con el dedo**.
Sustituyó el 25 de septiembre de 2026 a la banda de una sola fotografía con
cuatro botones bajo el mismo titular: el referente es gotrendier.com.co, donde la
imagen sangra hasta el borde de la ventana, y el del carrusel es el bloque "With
indicators" de TailAdmin. El orden —ropa, calzado, bolsos, tecnología— no es el
canónico de `LINEAS`: qué línea encabeza la portada lo decide el negocio, no el
modelo.

**Sin Swiper.** El bloque de referencia está montado sobre esa librería, y no
entra: cada dependencia nueva es deuda, y lo que hace falta de ella —cuatro
diapositivas, unas viñetas y un temporizador— son cincuenta líneas de señales.
Lo que sí se copia es el aspecto: viñetas tipo píldora abajo al centro, la activa
tres veces más ancha.

### Se pasa también con el dedo

Arrastrar de lado pasa a la siguiente pieza o a la anterior, con el umbral en la
sexta parte del ancho visible —una fracción y no unos píxeles: el mismo gesto se
hace sobre una pieza de 390 px y sobre una de 2560—. La tira **sigue el dedo**
mientras dura el gesto; uno que no se mueve hasta que sueltas no parece
arrastrable, parece roto. En los extremos, donde no hay pieza que descubrir, el
arrastre se frena a un tercio: se mueve lo justo para sentirse atendido sin
enseñar una franja vacía.

El patrón sale del visor 360, que ya lo había pagado una vez, y **tres cosas de
las que cuesta acordarse**: `touch-action: pan-y` deja el eje vertical al
navegador —o deslizar aquí secuestra el scroll de la página—, el temporizador se
apaga mientras el dedo está encima y se reprograma al soltar, y un arrastre que
termina sobre el botón de la diapositiva no puede activarlo.

Esa última es una guarda de clic en fase de captura: un `(click)` de plantilla
escucha en burbuja, o sea después de que el enlace haya navegado. Y hay dos
defectos que **solo aparecieron en el navegador**, los dos por cómo trata Chrome
un puntero capturado y un enlace:

- **La captura se toma cuando el gesto se confirma horizontal, no en el
  `pointerdown`.** Capturar el puntero redirige a ese elemento todo lo que queda
  de él, `click` incluido: con la captura al empezar, un clic normal sobre el
  botón de la diapositiva salía `pointerdown` en el `<a>` y `pointerup` y `click`
  en el `<div>`, así que **el botón principal de la portada no navegaba**.
- **Un `pointercancel` aborta el gesto sin decidir nada.** Al arrastrar empezando
  encima del botón —que es un `<a>`— Chrome arranca su arrastre nativo de enlaces
  y manda `pointercancel` con coordenadas que no son las del dedo; tratándolo como
  un final, el carrusel saltaba a la pieza **contraria** a la que pedía la mano.
  Además `superficie-arrastre` pasó a aplicar `-webkit-user-drag: none` también a
  lo de dentro, que es lo que hace que ahí el gesto sí funcione.

Ninguno de los dos lo ve jsdom, que no implementa la captura de puntero. Lo que sí
queda fijado por una prueba es el `pointercancel`, porque el evento sí se puede
disparar a mano.

**El arte es fotografía a sangre desde el 25 de septiembre de 2026**, y antes de
ese día era otra cosa: un lienzo grafito con el producto recortado a la derecha y
la mitad izquierda libre para que el texto cayera sobre color plano. Hoy la
fotografía ocupa la pieza entera y el texto va encima de ella.

Las cuatro fotografías originales no se versionan —12 MB de archivos de 5.000 a
8.000 px— con el mismo criterio que el ZIP del arte anterior: se versiona el
resultado, las doce `.webp` de `apps/web/public/imagenes/portada/hero/`, y el
**encuadre**, que es la decisión y vive en `tools/recortar-hero.py`. Ahí está por
qué el recorte ancho de calzado va pegado arriba —centrado le corta el tenis, que
vive en el tercio superior— y por qué el de bolsos baja un poco, para no cortarle
la base al bolso.

**El texto lo pone el HTML, nunca los píxeles**, que es lo que exige la regla dura
#4: texto dentro de una imagen no se traduce, no lo lee un lector de pantalla, no
escala y, en el caso del botón, parece pulsable sin serlo.

**Un solo juego de arte para los dos temas**, que ya valía para el arte de estudio
y vale más para una fotografía: no hay dos versiones entre las que elegir. Servir
dos costaría caro —un `<img>` con `display:none` se descarga igual en Chrome, o
sea el doble de bytes justo en la imagen del LCP— y elegir en tiempo de ejecución
no se puede, porque el servidor no conoce el tema mientras renderiza. Lo que sí
cambia de tema es el velo, porque sale de `--color-marca`.

### El velo y el halo, que son lo que hace legible el texto

Una fotografía no promete contraste. La de bolsos tiene fondo lila casi blanco y
la de tecnología es un escritorio blanco: texto blanco encima, sin nada de por
medio, da **1,1:1**. Van dos capas, y cada una hace algo distinto.

**El velo** es un degradado de `--color-marca` sobre la fotografía: opaco al 92 %
en el borde, 70 % a la mitad y transparente al 95 %. De abajo arriba en teléfono
—donde el texto se apoya en el borde inferior— y de izquierda a derecha desde el
primer punto de quiebre, donde ocupa la mitad izquierda. Muere antes de llegar a
la otra mitad a propósito: el tenis, el bolso y los audífonos se ven limpios.

**El halo** (`halo-texto` en `src/tailwind.css`) son dos sombras de texto del
mismo grafito, una de 4 px al 85 % que dibuja el borde de la letra y otra de 16 px
al 55 % que la despega del fondo. Es oscuro y no claro, aunque "halo" suene a
claro: con letras casi blancas, un resplandor claro no se distingue de la propia
letra y solo emborrona lo que hay detrás. Lo que separa las letras del grano, las
barandas y el sol de una fotografía de calle es el grafito alrededor.

**Los números están medidos, no elegidos.** El guion compone el velo sobre los
píxeles de cada recorte y calcula el contraste del texto contra el resultado, en
la zona donde el texto cae de verdad y con el par de tema oscuro, que es el peor
—`#EDF0F4` sobre `#191E26`—:

| Pieza | Escritorio | Teléfono |
|---|---|---|
| ropa | 6,42:1 | 6,13:1 |
| calzado | 6,24:1 | 7,17:1 |
| bolsos | **5,69:1** | 6,56:1 |
| tecnología | 6,14:1 | 6,13:1 |

El mínimo es 4,5:1 (WCAG 1.4.3) y la peor esquina de las cuatro es la de bolsos.
**El velo no se puede suavizar sin volver a medir**: se probó a 85 % / 55 % y
bolsos cae a 3,55:1, por debajo del mínimo. `npm run contrastes` no lo vigila
—compara pares de tokens, y aquí uno de los dos lados es una fotografía—, así que
el día que se cambie una foto hay que repetir la medición.

### Una utilidad donde había tres

`corte-hero` y `chaflan-hero` se fueron con la banda anterior. Dibujaban a mano el
corte a 45° de la esquina inferior derecha y el chaflán del marco de la foto, y el
carrusel no tiene marco que chaflanar. **La firma de la marca no se perdió**: la
traen los propios archivos, cuya tarjeta de producto ya viene cortada a 34 px en
las dos esquinas. Un `clip-path` de cinco pares de coordenadas que nadie aplica es
peor que ninguno, porque parece vigente al leerlo.

`alto-hero` sobrevive con otro nombre y con el signo cambiado: es
`alto-carrusel`, y es un **máximo** donde era un mínimo.

| Token | Valor | Qué es hoy |
|---|---|---|
| `--hero-alto-min` | 620 px | El techo del carrusel, usado como `max(min(token, 70vh))` |
| `--hero-corte` | 120 px | Sin consumidor desde el carrusel |
| `--chaflan-hero` | 72 px | Sin consumidor desde el carrusel |

La diferencia entre mínimo y máximo es la que hay entre las dos piezas: la banda
vieja era texto sobre color y necesitaba un alto que llenara la primera pantalla;
el carrusel es una imagen de 1440 × 592 a todo el ancho, y a 1900 px de ventana su
proporción natural pediría 780 px de alto, más que la pantalla entera de un
portátil. Se recorta con `object-cover`, y por eso el encuadre de cada fotografía
tiene que aguantar perder otro tanto por arriba y por abajo: el recorte que
`tools/recortar-hero.py` publica no es el que se ve en una pantalla ancha.

Vale también para la pieza vertical de teléfono, aunque ahí casi nunca manda: 4:5
a 390 px de ventana son 487 px de alto, por debajo del techo. Quien lo nota es un
teléfono bajo —a 667 px de alto, el `70vh` recorta veinte píxeles—.

### Dos formas, no dos tamaños

La pieza ancha es 2,43:1. A 390 px de ventana esa proporción mide 160 px de alto y
no cabe nada encima, así que **en teléfono va un recorte vertical 4:5** de la misma
fotografía, de 1000 × 1250, con el texto apoyado abajo. Fue una tarjeta cuadrada
mientras el texto iba debajo de la imagen; desde que va encima, un cuadrado no da
de sí para la foto y el texto a la vez.

Los encuadres verticales no son los anchos recortados: se eligen aparte y por la
misma razón que los anchos. El de calzado deja el tenis arriba y el piso del
muelle vacío abajo, que es donde cae el texto.

Eso se resuelve con `<picture>` y un `<source media>`, **no con
`NgOptimizedImage`**, que `apps/web/CLAUDE.md` pide "siempre". Es la excepción que
esa regla no contempla: `NgOptimizedImage` no admite dirección de arte, y aquí no
son dos tamaños de la misma foto sino dos composiciones distintas. La alternativa
—dos `<img>` y tapar uno con CSS— descarga los dos. El único literal es el `media`,
y vive con los otros de su clase en `core/imagenes/tamanos-de-imagen.ts`, por el
mismo motivo: lo lee el navegador antes de aplicar una sola hoja de estilos, así
que no puede referirse a `--breakpoint-desde-movil`.

El `sizes` es `100vw` en las dos formas, que es un porcentaje de la ventana y no
un píxel: ahí no hace falta ningún permiso de la regla dura #2. Por eso desapareció
`TAMANOS_HERO`, que existía cuando la foto ocupaba media rejilla de `--ancho-max`.

### Las viñetas y el movimiento

**La tira de viñetas va debajo de la fotografía, no encima**, y eso cambió con el
arte. Flotaba sobre el lienzo de estudio desde el primer punto de quiebre, y podía:
aquel arte era grafito de borde a borde, así que la viñeta apagada —blanco al 50 %—
daba 4,6:1 contra él en cualquier punto donde cayera. Una fotografía no promete
nada de eso, y el velo deja limpia justo la mitad por donde pasa el centro de la
tira: sobre el escritorio blanco de la pieza de tecnología, esa misma viñeta cae a
**1,1:1** y desaparece.

Se bajó a la franja de marca, que ya existe y ya está medida, en vez de ponerle un
fondo oscuro propio: eso último es inventarle una superficie a un control para
tapar un problema que volverá con la siguiente foto clara. Ahí el 3:1 de WCAG
1.4.11 no depende de qué fotografía se publicó.

Las viñetas salen de `--color-sobre-marca` en los dos estados. La inactiva fue
`bg-ts-marca-alt` durante un rato y daba **1,38:1** contra el fondo, por debajo del
3:1; al 50 % del blanco sube a 4,6:1 y se sigue leyendo como "apagada" frente a la
activa.

**El objetivo táctil va en el `<button>` y la píldora en un `<span>` hijo.** La
píldora mide 8 px de alto y el botón era la píldora: 8 × 8 px la inactiva, por
debajo de los 24 × 24 de WCAG 2.5.8 y muy por debajo de los 44 de la tabla de
medidas. La excepción de espaciado de 2.5.8 tampoco salvaba —con `gap-8` los
centros quedaban a 16 px y los círculos de 24 se solapan—. Con `size-tactil` en el
botón y `bg-transparent`, el área pulsable son 44 px y el dibujo no engorda.

**Y hay un botón de pausa**, que es lo que WCAG 2.2.2 (nivel A) exige de todo
movimiento automático que dure más de cinco segundos: un mecanismo para pausarlo,
detenerlo u ocultarlo. **No se fue con el segundo que se le quitó al paso**: lo
que el criterio mide es cuánto dura el movimiento, y el de un carrusel que rota
solo no termina nunca; los cuatro segundos son lo que dura cada paso. Este documento describía "se detiene con el puntero encima
y con el foco dentro" como si bastara, y no basta: en un teléfono no hay puntero
—y tocar una viñeta *reinicia* la cuenta en vez de detenerla— y con teclado no es
descubrible. La pausa de la persona es una señal aparte de la del puntero y
**gana**: con un solo booleano para las dos fuentes, sacar el ratón reanudaba lo
que alguien acababa de pausar a propósito.

**`aria-roledescription` pasa por Transloco.** Decía `"carousel"` y `"slide"` en
inglés, literales en la plantilla. Ese atributo **se pronuncia**: en la versión
española el lector decía "carousel". Es texto visible —para quien escucha— y la
regla dura #4 no admite ninguno, `aria-label` incluidos.

**El `<h1>` de la portada vive fuera del carrusel.** Estuvo en la primera
diapositiva, con `<p>` en las otras tres para no tener cuatro encabezados de nivel
uno. El razonamiento era correcto y el resultado estaba roto: esa diapositiva
queda `inert` y `aria-hidden` en cuanto el carrusel avanza, así que a los pocos
segundos la portada se quedaba **sin ningún encabezado de nivel uno**. Hoy es un
`sr-only` en `portada.page.html` con `portada.titulo`, que además es estable —un
`h1` que cambia de texto solo es peor que uno que no se ve—.

Los cuatro hallazgos anteriores salieron de la auditoría de accesibilidad del 25
de septiembre de 2026, con `npm run clases`, `npm run contrastes` y las pruebas en
verde: ninguna herramienta del proyecto los veía.

### Los cuatro botones miden lo mismo

`min-w-[20ch]` en el `ts-boton` de la diapositiva. Sin él cada uno mide lo que mide
su texto —de 61,6 px "Ver ropa" a 158,8 px "Ver calzado deportivo"— y el botón
baila de ancho cada cuatro segundos en el mismo punto de la pantalla, que es justo
donde está la mano de quien va a pulsarlo.

El número sale de medir los ocho rótulos en el navegador, los cuatro de cada
idioma, y **el más ancho no es el español**: "Browse sports footwear" pide 18,2ch.
Los 20ch dejan margen, y ese margen tiene un destinatario concreto: mientras la
tipografía de marca no ha cargado (`ADR-0059`) el texto se pinta con el respaldo
del sistema, y ahí el mismo rótulo pasa a pedir 19,3ch — no porque crezca, sino
porque encoge el `ch`, que es el ancho del "0" de la fuente que esté puesta.

`ch` y no píxeles: es la escapatoria que la regla dura #2 admite y que ya usan
`min-w-[12ch]` en el visor 360 y `min-w-[2ch]` en la línea del carrito. Y se
comprueba leyendo el ancho pintado, no calculándolo: un `<span>` de prueba con la
misma declaración `font` daba 8,89 px por carácter donde el elemento de verdad
resuelve 9,6.

Son botones con `aria-current`, **no `role="tablist"` con `role="tab"`**: el patrón
de pestañas de la APG obliga además a que cada diapositiva sea un `tabpanel`
etiquetado por su pestaña y a mover el foco con las flechas dentro de la tira, y a
medio implementar anuncia una estructura que no existe.

Del patrón de carrusel de la APG sí se toma todo lo demás:
`aria-roledescription="carousel"` con su nombre, un `group` por diapositiva con su
«N de 4», `inert` en las tres que no tocan —para que su botón salga del orden de
tabulación—, y `aria-live` en `off` mientras rota sola y `polite` cuando alguien la
detuvo. **El movimiento se detiene con el puntero encima y con el foco dentro**, y
con menos movimiento pedido no arranca siquiera: ahí no basta con acortar la
animación, hay que no programar el temporizador.

### La tira de confianza

Los cuatro sellos van **bajo el carrusel y no dentro de cada pieza**: son ciertos
para las cuatro líneas, así que repetirlos cuatro veces sería decir cuatro veces lo
mismo. Son cuatro en rejilla, no tres en un `flex-wrap`: entró "Diversas opciones
de pago" el 25 de septiembre de 2026 y el cuarto caía solo a una segunda línea,
colgando bajo el primero. Dos columnas en teléfono y cuatro desde tableta, con el
icono alineado a la primera línea del texto (`items-start` más `mt-4`) porque a dos
columnas hay sellos que ocupan dos líneas. Los cuatro iconos son de Lucide, con el
mismo trazo y el mismo tamaño: **un emoji no vale** aunque se pida —lo dibuja la
fuente del sistema, cambia de forma y de color en cada plataforma, y la fila deja
de leerse como una familia—.

**El párrafo de apoyo de cada pieza dice qué se vende, no cómo se compra.** Las
condiciones —envíos, contraentrega, medios de pago, garantía legal— las lleva esta
tira, y hasta el 24 de septiembre de 2026 estaban dichas dos veces, palabra por
palabra, en los dos sitios.

### Una cosa que solo se vio en el navegador

**La banda va en `--color-marca`, no en `--color-primario`.** En tema oscuro
`primario` **es el ámbar**: la banda entera se teñía y el botón de acento
desaparecía dentro de ella. Es exactamente lo que este documento ya decía en
"Modo oscuro" —las franjas grandes no se vuelven ámbar— y el pie ya usaba el par
correcto, `bg-ts-marca` con `text-ts-sobre-marca`. Ninguna prueba lo habría dicho.

(La otra que vivía aquí era por qué `chaflan-hero` no podía componerse con
`chaflan` —`.chaflan` vive en `tokens.css`, fuera de toda capa, y le gana a
cualquier utilidad de Tailwind aunque el selector empate—. Se queda escrita
aunque la utilidad ya no exista: el mecanismo sigue ahí para la próxima que lo
intente.)

### Lo que el texto puede decir

Las afirmaciones del hero son publicidad y **obligan** (Ley 1480). Las cuatro que
traía el kit se revisaron una por una contra lo que el sistema puede sostener:

- ~~"Diez años surtiendo a Medellín"~~ y ~~"Precios de distribuidor"~~ no están en
  ningún documento del proyecto. Fuera.
- ~~"Envío a todo Colombia"~~ es falsa hoy: el checkout tiene
  `ENVIO_SIN_COBERTURA` y ofrece la recogida cuando no hay transporte. Se cambió
  por "Envío cotizado a tu ciudad", que es lo que de verdad hace.
- ~~"Desde $189.900"~~ era un precio escrito en la plantilla. Fuera: envejece solo
  y no hay endpoint que dé el mínimo del catálogo.

Lo que queda —envío cotizado, contraentrega donde esté disponible, garantía
legal— es lo mismo que prometen los términos publicados.

El copy de las cuatro piezas sale de la tabla de `hero-tecnosport/LEEME.md` y
pasa el mismo filtro: dice qué hay en cada línea y no promete plazo, precio ni
cobertura.

## Imágenes del catálogo

- Producto, imagen principal: 1:1, 1000 x 1000 px, fondo claro uniforme.
- Set de rotación: 1:1, 1000 x 1000 px, misma escala del producto en todos los
  fotogramas. Lo produce el asistente de captura.
- Hero: 4:3, 1200 x 900 px.
- Ejes de categoría: 16:9, 1200 x 675 px.

Si las proporciones no se respetan, la rejilla queda desigual. El panel valida
proporción, peso y formato al subir, y genera los tamaños derivados.
