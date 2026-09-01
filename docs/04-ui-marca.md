# Interfaz y marca

El sistema visual ya existe y está cerrado. Vive en `packages/marca`. Este
documento explica cómo se instala en Angular y qué no se puede tocar.

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
  "src/styles.scss"
]
```

Los tokens son CSS puro, así que atraviesan el encapsulamiento de estilos: dentro
de cualquier componente `var(--color-primario)` funciona sin `::ng-deep`.

Las licencias OFL se distribuyen con las fuentes. Es condición de la licencia, no
un detalle.

## Lo que no se toca

- **`tokens.css` y `fuentes.css` son generados.** Se edita `tokens.json` y se
  regenera. Un cambio a mano lo borra el siguiente regenerado.
- **Radio 0 en todo.** Ninguna esquina redondeada, en ningún componente.
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
| Objetivo táctil mínimo | 44 x 44 px | |

Tipografía: Archivo en titulares, IBM Plex Sans en texto e interfaz, IBM Plex
Mono en precios y referencias, con cifras tabulares para que las columnas alineen
solas. Todas SIL OFL, autoalojadas, sin Google Fonts.

## Componentes de `shared/`

Cada uno con sus estados (normal, hover, activo, foco, deshabilitado, cargando),
en claro y en oscuro, y operable con teclado:

`ts-boton` (primario, secundario, texto, peligro) · `ts-campo` · `ts-select` ·
`ts-checkbox` · `ts-radio` · `ts-tarjeta-producto` · `ts-precio` ·
`ts-etiqueta-stock` · `ts-galeria` · `ts-visor-360` · `ts-selector-variante` ·
`ts-paginador` · `ts-migas` · `ts-dialogo` (CDK) · `ts-notificacion` ·
`ts-esqueleto` · `ts-selector-idioma` · `ts-selector-tema` · `ts-selector-metodo-pago`.

Si un componente necesita un valor que no está en los tokens, el sistema está
incompleto: se agrega a `tokens.json` con nombre, no se escribe un píxel suelto
en el SCSS.

## Modo oscuro

Atributo `data-tema="oscuro"` en `<html>`. Tres opciones para el usuario: claro,
oscuro y seguir al sistema. Se persiste y se resuelve en el servidor durante el
SSR para que no haya destello blanco al hidratar.

Decisión ya tomada en el kit: en oscuro las franjas grandes (hero, menú móvil,
pie) no se vuelven ámbar, se quedan en grafito elevado. El ámbar manda en botones
y enlaces. Si todo se tiñe de ámbar, muere la regla de una sola cosa por pantalla.

## Accesibilidad, mínimos exigibles

- WCAG 2.2 nivel AA. Cualquier color nuevo se verifica antes de entrar.
- Foco visible en el cien por ciento de los elementos enfocables. Nunca
  `outline: none` sin reemplazo.
- Cero desbordamiento horizontal a 380 px de ancho.
- El texto aguanta 200 por ciento de zoom sin romper la maqueta.
- Jerarquía de encabezados sin saltos, un solo `h1` por página.
- Controles del usuario en el pie: tamaño de texto, contraste alto y reducción de
  movimiento, que además respetan `prefers-reduced-motion` y
  `prefers-color-scheme`.
- Navegación completa por teclado, con enlace de salto al contenido.
- El visor 360 se opera con flechas y con botones visibles, no solo arrastrando.

## Imágenes del catálogo

- Producto, imagen principal: 1:1, 1000 x 1000 px, fondo claro uniforme.
- Set de rotación: 1:1, 1000 x 1000 px, misma escala del producto en todos los
  fotogramas. Lo produce el asistente de captura.
- Hero: 4:3, 1200 x 900 px.
- Ejes de categoría: 16:9, 1200 x 675 px.

Si las proporciones no se respetan, la rejilla queda desigual. El panel valida
proporción, peso y formato al subir, y genera los tamaños derivados.
