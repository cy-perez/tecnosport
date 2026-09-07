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

## Lo que no se toca

- **`tokens.css` y `fuentes.css` son generados.** Se edita `tokens.json` y se
  regenera. Un cambio a mano lo borra el siguiente regenerado.
- **Radio 0 en todo.** Ninguna esquina redondeada, en ningún componente. Desde
  `ADR-0020`, la escala de radios de Tailwind está borrada, así que
  `rounded-sm/md/lg/xl` no existen y una plantilla que los use no compila nada.
  **No cubre todos los casos:** `rounded`, `rounded-full`, las variantes por
  esquina y los valores arbitrarios sobreviven porque son estáticos de Tailwind,
  no valores de la escala. En el camino normal lo impide el compilador; en el
  resto, la regla. Se audita con `grep -rn "rounded-" apps/web/src`.
- **El chaflán a 45 grados** en la esquina superior izquierda y la inferior
  derecha es la firma de la marca. Se aplica con la clase `.chaflan`, y `--ch`
  controla el tamaño. Va en botones, tarjetas de producto, etiquetas de precio y
  recortes de fotografía.
- **El chaflán se desactiva en `:focus-visible`** porque `clip-path` recorta el
  anillo de foco. Está resuelto en `tokens.css` y es deliberado.
- **La regla del ámbar:** `#F5B301` es una sola cosa por pantalla y solo como
  relleno con texto grafito encima. Sobre blanco da 1.85:1. Nunca como texto ni
  como ícono sobre fondo claro.
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
| Objetivo táctil mínimo | 44 x 44 px | `--control-tactil` |
| Insignia del contador | 20 x 20 px | `--control-insignia` |

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
  `rounded-lg` no existen. Para saber si una utilidad existe de verdad:
  `npm run clases -- <clase>`. Lo que hay son los tokens: `bg-ts-primario`,
  `text-ts-texto-suave`, `p-16`, `text-2xl`, `shadow-md`, `max-w-formulario`.
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
| `--mov-*` y `--curva-*` | duraciones y curvas | literales en `tailwind.css` |

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
`ts-select` · `ts-dialogo` · `ts-pagina-formulario`.

`ts-pagina-formulario` es el cascarón de una pantalla de formulario: ancho de
columna, aire vertical y titular. Estaba repetido **doce veces** —ocho en
`cuenta`, donde las pantallas con estado de éxito lo repiten dentro de la misma
plantilla, y cuatro en `admin`—. Las migas se colocan encima del titular por un
`ng-content select="ts-migas"`, así que quien lo usa solo tiene que ponerlas
dentro. `verificar-correo` no lo usa a propósito: es una pantalla de estado con
tres ramas y una de ellas no tiene título.

`ts-campo` y `ts-select` envuelven controles nativos distintos pero tienen que
verse idénticos, así que su borde, relleno, objetivo táctil, anillo de foco y
estado deshabilitado salen de `clases-control.ts`. Estaban duplicados en dos
SCSS con los mismos valores.

**En `shared/` — compartidos de verdad, pero no tontos:** `ts-precio` (necesita
el idioma activo para formatear la moneda) · `ts-esqueleto` · `ts-migas` ·
`ts-paginador` · `ts-selector-idioma` · `ts-selector-tema` · `ts-visor-360`.

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
que sí es correcta y que ESLint verifica. Sigue pendiente extender
`boundaries/include` a `src/app/shared/**` para que el build lo impida en vez
de depender de que alguien lo note.

**Pendientes de construir:** `ts-checkbox` · `ts-radio` · `ts-notificacion`. El
único checkbox del sitio sigue siendo el de "reducir movimiento" del pie. Se
construyen cuando aparezca el primer consumidor real, no antes.

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

`ts-selector-idioma` y `ts-selector-tema` no dibujan su propio `<select>`: se
apoyan en `ts-select`, que ya resuelve el `<label>` real, el anillo de foco, el
objetivo táctil de 44 px y el `min-inline-size: 0` que evita que la opción más
larga ensanche su columna. Un componente compartido que envuelve un control
nativo se construye una vez.

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

## Modo oscuro

Atributo `data-tema="oscuro"` en `<html>`. Tres opciones para el usuario: claro,
oscuro y seguir al sistema. La preferencia se persiste en una cookie. Claro y
oscuro explícitos se resuelven en el servidor durante el SSR. "Seguir al
sistema" no se puede resolver en el servidor —no hay forma de saber la
preferencia de `prefers-color-scheme` del visitante ahí—, así que se resuelve
en un script inline antes del primer pintado, en el `<head>`, para que no haya
destello.

**El dueño de esa política es `core/tema/ServicioTema`** (Fase 1 del stack de
UI): lee la cookie, la persiste y escribe el atributo. Antes vivía dentro de
`shared/ts-selector-tema`, que así cargaba con dibujar un select, escribir una
cookie y tocar el DOM del documento a la vez. Un componente compartido no decide
la política de persistencia del sitio. El servicio se reparte el trabajo con
`tema-ssr.ts` (el servidor) y el script inline de `index.html` (el caso
"sistema"); cuando el servicio corre, **ya hay un tema aplicado** y su trabajo al
arrancar es reflejarlo, no volver a decidirlo.

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
  `prefers-color-scheme`. **Reducción de movimiento cerrada** (`layout/pie/`,
  2026-09-04): checkbox persistido en `localStorage`, con `styles.scss`
  repitiendo la misma regla que `tokens.css` ya aplica bajo
  `prefers-reduced-motion`, disparada por `[data-movimiento="reducido"]`.
  **Los otros dos, pendientes, cada uno con su propio bloqueo real:**
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
- **El CDK ya está en uso** (`shared/ui/dialogo`): `[cdkTrapFocus]` con
  `cdkTrapFocusAutoCapture` mete el foco en el diálogo al abrir y lo devuelve al
  elemento anterior al cerrar. **Ese movimiento tampoco lo prueba Vitest**: el
  `InteractivityChecker` del CDK mide layout y en jsdom todo mide cero.
- El visor 360 se opera con flechas y con botones visibles, no solo arrastrando.

## Imágenes del catálogo

- Producto, imagen principal: 1:1, 1000 x 1000 px, fondo claro uniforme.
- Set de rotación: 1:1, 1000 x 1000 px, misma escala del producto en todos los
  fotogramas. Lo produce el asistente de captura.
- Hero: 4:3, 1200 x 900 px.
- Ejes de categoría: 16:9, 1200 x 675 px.

Si las proporciones no se respetan, la rejilla queda desigual. El panel valida
proporción, peso y formato al subir, y genera los tamaños derivados.
