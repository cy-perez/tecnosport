# Anatomía de una lista de proveedor

## Cómo está construido el mensaje

Un mensaje trae un encabezado (fecha, nombre de la lista, un saludo en cursiva) y
luego bloques de productos separados por secciones. Las secciones van entre
asteriscos y en mayúsculas: `*IPH NUEVOS*`, `*AUDIFONOS ORIGINALES*🎧`.

Cada sección fija tres cosas para las líneas que vienen abajo: la **categoría**,
la **marca** y la **condición**. Un encabezado que solo trae una marca
(`*SAMSUNG*`) cambia únicamente la marca y conserva la categoría vigente.

## La viñeta manda

El emoji con el que abre la línea es la señal más confiable de categoría, más que
la sección:

| Viñeta | Significa |
|---|---|
| ✔️ | celular nuevo sellado |
| ⚠️ | celular nuevo con la garantía ya activada |
| 🚀 | celular nuevo (Samsung) |
| 📲 | celular usado → se descarta |
| ⌚ 🎧 | reloj, audífono |
| 🔌 🪫 🔋 | cargador, power bank → **no se publican**, caen en descartados |
| 🔥 | cable (va en la sección `*CABLE*`, debajo de cargadores) → **no se publica** |

Las viñetas ✔️, ⚠️ y 📲 solo indican la condición: la categoría la pone la sección
(`*TABLET SAMSUNG*`) o el propio texto de la línea. Un encabezado de solo marca
(`*ZTE*`, `*HONOR*`) cambia la marca y deja que las líneas definan la categoría.
| 🎮 👾 | accesorio de consola (**no se publica**), consola |
| 💻 🖥️ | portátil, todo en uno |
| 📽️ | proyector |
| ✔️ 🚀 📲 en sección de tablets | tablet: la sección manda sobre la viñeta |
| 📺 📡 🔊 🖨️ 🫟 🥶 🛴 | TV, router, parlante, impresora, tinta, variedad → fuera |

## Convenciones que hay que conocer

- **Precios acotados**: `$1.850` son 1.850.000 COP y `$85` son 85.000 COP. Siempre
  se multiplica por 1.000, con punto de miles o sin él (`$1050` = 1.050.000).
- **Capacidades**: `1 TERA` = 1TB · `12/512` = 12 GB de RAM y 512 GB · `(8+512)` y
  `°8RAM / °512GB` en computadores = RAM + almacenamiento.
- **Abreviaturas**: `IPH` iPhone · `PM` Pro Max · `16E` iPhone 16e · `ESIM` solo
  eSIM · `ACTIVO` garantía iniciada · `PC` y `BPC` son notas de equipos usados.
- **Errores de digitación frecuentes**: `LAPTO`, `WACH`, `CHOISE`, `11ACTIVE`
  pegado. El script corrige los conocidos; el resto queda marcado.
- **Los porcentajes son salud de batería** de equipos usados (`🖤90%87%`). Nunca
  son productos ni colores.
- **Una línea puede continuar en la siguiente**: si una línea con viñeta no trae
  precio y la de abajo no tiene viñeta, son el mismo producto
  (`👾 NINTENDO SWITCH 2` + `MARIO KART $2.150`).
- **Hay productos sin viñeta**: `15 PM 256 *ESIM* 🔵⚫` bajo su sección. Se toman
  con la categoría de la sección y quedan marcados para verificar.

## Listas de gama media

Las listas de equipos económicos usan otras convenciones:

- **Memoria entre paréntesis**: `(8+256)` son 8 GB de RAM y 256 GB. `(4+4+128)` y
  `(4+4/256)` agregan en el medio la **RAM virtual**, que el fabricante toma del
  almacenamiento. Se publica la RAM física; la extendida se menciona aparte.
- **Red en el nombre**: `A17 4G (8+256)` y `A17 5G (8+256)` son dos productos con
  precios distintos. La red entra al título.
- **Anotaciones en la línea siguiente**: `*1 SIM*`, `*DUAL SIM*`, `*SIM / ESIM*`,
  `*INCLUYE MOUSE*` van debajo del producto al que pertenecen y se pegan a él.
- **Precios completos**: en los anuncios sueltos escriben `$1.960.000` en vez de
  `$1.960`. Se distingue por la cantidad de dígitos: seis o más ya son pesos.
- **Anuncios de llegada**: `*LLEGANDO INFINIX GT50 PRO*` con el precio dos líneas
  más abajo. El nombre está en el encabezado, no en una línea de producto.
- **Líneas sin viñeta ni sección**: bajo `*LLEGANDO MERCANCÍA*` escriben
  `Poco x8 pro Max 256 $1.830` en minúscula y sin emoji. Se reconocen por la marca.
- **Marcas abreviadas**: bajo `*XIAOMI*` escriben `NOTE 15` por Redmi Note 15 y
  `X8 PRO` por POCO X8 Pro. Las dos se completan solas y quedan anotadas en
  `supuestos`, para que se pueda rastrear de dónde salió el nombre del título.
- **Flecha** es el nombre del oficio para los celulares básicos de teclado.
- **Chat exportado**: si en vez de copiar el mensaje exportan la conversación,
  cada línea llega con `[12/9, 9:22 a. m.] +57 300 0000000:` adelante. Se ignora.
- **Los números de contacto del final no son productos**, aunque lleven 📲.

## Qué hace el parser con lo dudoso

Nada se descarta en silencio. `productos.json` trae cuatro listas: `productos`,
`duplicados` (los que se fusionaron con otro), `descartados` (con el motivo) y
`sin_clasificar` (líneas que no se pudieron leer).
Cada producto lleva `texto_origen` y el número de línea para poder rastrearlo, y
un arreglo `revisar` con lo que necesita confirmación humana.

Si una lista nueva trae una sección o una viñeta que no está en los mapas, no la
adivines en el momento: agrégala a `ENCABEZADOS` o `VINETAS` en el script para que
la próxima lista ya la reconozca.
