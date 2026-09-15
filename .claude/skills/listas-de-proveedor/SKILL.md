---
name: listas-de-proveedor
description: Convierte las listas de productos que mandan los proveedores por WhatsApp —con viñetas de emojis, precios acotados tipo $1.850 y colores marcados con corazones— en productos listos para publicar, filtrando usados y categorías que no se venden, estandarizando títulos, investigando el precio promedio del mercado colombiano, redactando la descripción y traduciendo los emojis de color a colores reales; entrega un ZIP con una carpeta por producto y un Excel comparativo de precios y ganancia. Úsala siempre que llegue una lista o listado de proveedor, mayorista o distribuidor; cuando alguien diga "procesa esta lista", "la lista de hoy", "lista de gama alta", "listado de variedad", "pasa esto a productos", "sube estos equipos a la tienda" o "cuánto me gano con estos productos"; o cuando pegue un texto con equipos, capacidades y precios aunque no lo llame lista. Covers WhatsApp supplier price list parsing, ecommerce catalog preparation, Colombian market price research and margin comparison.
---

# Listas de proveedor → catálogo

Las listas llegan como mensajes de WhatsApp escritos a mano: sin estructura fija,
con abreviaturas del oficio, emojis como viñetas y precios recortados. Publicarlas
a mano cuesta horas y se cometen errores caros (subir un usado como nuevo, poner
un color que no existe, vender por debajo del costo).

Esta skill separa dos trabajos: **lo mecánico lo hace un script** (leer la lista,
clasificar, extraer capacidades, precios y colores) y **lo que exige criterio lo
haces tú** (confirmar nombres comerciales, investigar precios de mercado, redactar
descripciones, conseguir fotos con derechos). El script nunca inventa: lo que no
reconoce lo marca en `revisar` para que quede a la vista.

## Reglas de negocio

Están al inicio de `scripts/parsear_lista.py` y se cambian ahí:

| Regla | Valor |
|---|---|
| Precios | `$1.850` = **1.850.000 COP**; `$1.960.000` se toma tal cual (6 dígitos o más) |
| Categorías que se publican | celulares, tablets, relojes, audífonos, cargadores, cables, power bank, consolas y accesorios, computadores, proyectores |
| Condición publicable | solo `nuevo`, es decir sellado y sin activar |
| Se descartan siempre | usados, "NUEVOS ACTIVOS" y "IPH CON CAJA" |

Estas tres ya están decididas por el negocio y no se vuelven a preguntar en cada
lista:

1. **"NUEVOS ACTIVOS" no se publican.** Están sellados pero con la garantía del
   fabricante ya corriendo, y eso cambia lo que el cliente recibe. Quedan en la
   hoja de descartados con su motivo, por si algún día se decide venderlos aparte.
2. **"IPH CON CAJA" se descarta.** En el argot es un equipo usado completo con su
   caja original.
3. **Los cables entran**, junto con los cargadores.
4. **Las tablets entran** como categoría propia.
5. **Las abreviaturas de la sección Xiaomi se resuelven solas**: `NOTE 15` se lee
   como Redmi Note 15 y `X8 PRO` como POCO X8 Pro. Queda anotado en `supuestos`
   de cada producto, que es distinto de `revisar`: lo asumido no bloquea la
   publicación, solo deja el rastro de por qué el título dice lo que dice.
4. **Las tablets se publican** como categoría propia (iPad y Galaxy Tab).

Si el negocio cambia de opinión, se ajusta `CATEGORIAS_INCLUIDAS` o
`CONDICIONES_PUBLICABLES` al inicio de `scripts/parsear_lista.py`.

## Dónde está corriendo esta skill

Cambia un paso, no el resto:

- **En Claude Code**, los scripts corren en el computador de la persona y tienen
  internet completo. Icecat y la descarga de fotos se ejecutan aquí mismo, de
  corrido, sin pasarle nada al usuario. Las credenciales salen de las variables
  de entorno `ICECAT_USER` e `ICECAT_PASSWORD` de su shell; si faltan, pídeselas
  con `setx` o con un `.env` que no se suba al repositorio, nunca en el chat.
- **En claude.ai**, el entorno solo alcanza unos pocos dominios. Ahí los pasos de
  Icecat y de descarga se preparan y se le entregan a la persona para que los
  corra en su equipo, como describe `referencias/imagenes.md`.

Comprueba cuál es el caso antes del paso 5: si un `curl` o una descarga a un
dominio externo funciona, estás en Claude Code.

En Claude Code conviene trabajar sobre una carpeta del proyecto, por ejemplo
`catalogo/`, y mantener fuera del control de versiones lo pesado y lo generado:
el índice de Icecat pesa 290 MB, las fotos crudas y los entregables tampoco van
al repositorio.

## Flujo

### 1. Guardar la lista tal cual

Copia el mensaje completo a `lista.txt` **sin corregir nada**: los emojis, los
espacios raros y los saltos de línea son justamente las señales que usa el parser.
Si llegaron dos mensajes (gama alta y variedad), pégalos en el mismo archivo.

### 2. Parsear

```bash
python3 scripts/parsear_lista.py lista.txt --salida productos.json --reporte revision.md
```

Deja `productos.json` (productos, repetidos que se fusionaron, descartados y
líneas sin clasificar) y `revision.md`, un resumen legible. Lee `revision.md`
antes de seguir.

### 3. Revisar con la persona antes de investigar

Muéstrale en el chat, en pocas líneas: cuántos productos entraron, cuántos se
descartaron y por qué, y la lista de alertas `revisar`. Pregunta solo lo que
realmente bloquea —un modelo irreconocible, una marca ambigua— y no lo que puedes
verificar tú mismo buscando. Si algo quedó en `sin_clasificar`, resuélvelo aquí:
esas líneas son productos que se perderían en silencio.

Detalle de cómo está armada una lista: `referencias/formato-de-listas.md`.

### 4. Investigar cada producto

Para cada producto incluido, en una sola pasada de búsquedas:

- **Nombre comercial oficial** — confirma la referencia real antes de titular.
  Las listas abrevian ("SAMSUNG BAND FIT 3" es la Galaxy Fit3, "WACH 8" es Galaxy
  Watch 8). Normas del título: `referencias/titulos.md`.
- **Precio promedio del mercado colombiano** — método, fuentes válidas y qué hacer
  con los precios atípicos: `referencias/precios.md`.
- **Ficha técnica y descripción** — estructura obligatoria y tono:
  `referencias/descripciones.md`.
- **Colores reales** — traduce los emojis y confírmalos contra la paleta oficial
  del modelo: `referencias/colores.md`.

Escribe los resultados de vuelta en `productos.json` (`precio_mercado_cop`,
`fuentes_precio`, `descripcion`, `meta_titulo`, `meta_descripcion`,
`colores_oficiales`, `titulo` corregido). Guarda cada búsqueda con su fuente: el
Excel lleva una columna de fuentes y sin ellas el precio no es verificable.

### 5. Fotos

Cuatro por producto, estándar y orden en `referencias/imagenes.md`.

Desde el entorno de ejecución no se pueden descargar imágenes de sitios
arbitrarios. La descarga se prepara aquí y se ejecuta en el computador de la
persona:

```bash
python3 scripts/preparar_fotos.py productos.json --salida fotos/
```

Eso genera `urls.csv`, un `descargar.py` sin dependencias y un instructivo. La
persona pega los enlaces, corre el descargador en su equipo y sube la carpeta
`crudas/`, que se normaliza con
`python3 scripts/normalizar_imagenes.py crudas/ --salida imagenes/`.

Si ya tiene fotos del proveedor o propias, se salta directo a la normalización.

Las fotos salen por dos vías y el flujo las separa desde el principio:

1. **Catálogo abierto de Icecat**, con `scripts/icecat_local.py` (`indice`,
   `buscar`, `traer`). Cubre las marcas grandes, obliga a citar la fuente en cada
   ficha y es lo único que se descarga automáticamente.
2. **El proveedor**, para todo lo demás. `scripts/preparar_fotos.py` descuenta lo
   que Icecat resolvió y escribe `pedido-al-proveedor.txt`, listo para enviar.

Un producto que Icecat solo tiene en su catálogo de pago no es un error: pasa al
grupo del proveedor y se sigue. Detalle y cobertura medida en
`referencias/imagenes.md`.

**No propongas las salas de prensa como fuente.** Las condiciones de Apple
Newsroom y Samsung Mobile Press autorizan uso editorial o personal, no publicar el
producto en una tienda. Lo que sirve es el paquete del proveedor, el portal de
partners si la tienda es revendedor autorizado, o fotos propias.

Para quitar fondos, dejar blanco puro y agregar sombra, usa la skill
`fotos-de-producto`, que ya hace ese trabajo por lotes.

### 6. Armar entregables

```bash
python3 scripts/construir_entregables.py productos.json --imagenes imagenes/ --salida entregables/
```

Produce:

- `catalogo-<fecha>.zip` — una carpeta por producto con sus fotos y un `.txt` con
  título, datos de publicación y descripción.
- `comparativo-<fecha>.xlsx` — hoja **Comparativo** con las cuatro columnas
  pedidas (título, precio de lista, promedio del mercado, ganancia), más hojas de
  **Detalle** (margen %, colores, fuentes, pendientes) y **Descartados**.

### 7. Entregar

Preséntale los dos archivos y, en dos o tres líneas, lo que necesita saber:
productos listos, productos que quedaron con pendientes y cualquier caso donde el
promedio del mercado esté por debajo del precio de lista. Ese caso significa que a
ese precio se pierde plata: márcalo, no lo publiques callado.

## Lo que el parser ya resuelve solo

No hay que volver a hacerlo a mano en cada lista:

- **Prefijos de exportación de WhatsApp** (`[10:05, 12/09/2026] +57 300 123 4567:`)
  se quitan antes de leer la línea.
- **Avisos de mercancía por llegar** (`LLEGANDO INFINIX GT 50 PRO`) entran como
  producto marcado `por_llegar`, para no publicar como disponible algo que no está.
- **El mismo equipo repetido** entre el aviso del día y la lista larga se fusiona
  en un solo producto, quedándose con el registro más completo. Solo se fusionan
  si coinciden marca, capacidad, RAM y precio; ante la duda quedan separados,
  porque juntar un Pro con un Pro Max es peor que tener dos fichas.
- **SIM y eSIM**: `SIM/ESIM`, `DUAL SIM`, `1 SIM` y `ESIM` salen como atributo, no
  como parte del nombre.
- **RAM virtual** (`8+8`): se publica solo la física y queda la nota de que el
  proveedor sumaba la virtual.

## Cosas que se rompen si no se cuidan

**El precio del sitio no tiene por qué ser el promedio.** El promedio es el
referente para saber cuánto margen hay. Si se publica exactamente en el promedio
no hay ventaja frente a Alkosto o Mercado Libre, y si el proveedor subió, el
margen puede ser negativo sin que nadie lo note. Muestra el número y deja la
decisión de precio en manos del negocio.

**"Original" y "compatible" no son lo mismo.** Varios cargadores y cables de las
listas son compatibles, no originales de la marca. Publicar uno como el otro es
publicidad engañosa frente a la Ley 1480 y termina en devoluciones. Cuando la
lista no lo aclare, pregunta antes de publicar.

**En las tablets, el mismo número significa dos cosas.** `IPAD AIR 11` puede ser
la pantalla de 11 pulgadas o la generación. Confirma cuál antes de titular: es el
error más caro de esta categoría porque cambia el producto entero.

**A los cables les faltan tres datos.** La lista solo dice los extremos
(`TIPO C - LIGHTNING`). Longitud, potencia soportada y marca hay que conseguirlos
con el proveedor: son exactamente las tres razones por las que un cliente devuelve
un cable.

**La RAM virtual no es RAM.** En la gama media las listas escriben `(8+8+256)`:
8 GB físicos, 8 GB "extendidos" tomados del almacenamiento, 256 GB de disco.
Publicar 16 GB es falso y el cliente lo comprueba en la configuración del equipo.
Va la RAM física en el título y la extendida como característica aparte.

**Un mismo equipo llega varias veces.** El anuncio de "LLEGANDO", el mensaje de
mercancía nueva y la lista larga del día traen los mismos modelos con distinta
escritura. El script fusiona los que no se contradicen en red, RAM ni marca, y
avisa cuando el precio no coincide entre mensajes.

**4G y 5G son productos distintos.** `A17 4G (8+256)` a $630 y `A17 5G (8+256)` a
$700 son dos referencias. La red va en el título, no en la descripción.

**Los colores son variantes, no parte del título.** Un mismo modelo con cuatro
colores es un producto con cuatro variantes.

## Archivos

```
scripts/parsear_lista.py          lista.txt → productos.json + revision.md
scripts/preparar_fotos.py         arma urls.csv + descargador para correr local
scripts/icecat_local.py           trae fichas e imágenes de Open Icecat
scripts/normalizar_imagenes.py    fotos crudas → 2000×2000 + variantes webp
scripts/construir_entregables.py  productos.json → ZIP + Excel
referencias/formato-de-listas.md  anatomía de los mensajes de proveedor
referencias/titulos.md            fórmula de títulos por categoría
referencias/descripciones.md      estructura de la descripción y metadatos
referencias/precios.md            método de investigación de precios
referencias/colores.md            emojis → colores publicables
referencias/imagenes.md           estándar de fotos y origen de las imágenes
plantillas/producto.txt           plantilla del archivo de cada producto
plantillas/env.ejemplo            plantilla de credenciales de Icecat
```
