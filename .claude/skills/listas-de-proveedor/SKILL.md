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
| Categorías que se publican | celulares, tablets, relojes, audífonos, consolas, computadores, proyectores, parlantes |
| Condición publicable | solo `nuevo`, es decir sellado y sin activar |
| Se descartan siempre | usados, "NUEVOS ACTIVOS", "IPH CON CAJA", cables, cargadores, power bank, accesorios sueltos (control de consola, pencil táctil, rastreador tipo tag), lo que quede sin precio, los celulares por debajo de 500.000 COP, los computadores sin marca o sin referencia y **todo** lo de Krono, BMAX, itel, ZTE, Infinix y Tecno |

Estas ya están decididas por el negocio y no se vuelven a preguntar en cada
lista:

1. **"NUEVOS ACTIVOS" no se publican.** Están sellados pero con la garantía del
   fabricante ya corriendo, y eso cambia lo que el cliente recibe. Quedan en la
   hoja de descartados con su motivo, por si algún día se decide venderlos aparte.
2. **"IPH CON CAJA" se descarta.** En el argot es un equipo usado completo con su
   caja original.
3. **Lo que venga sin precio queda por fuera del análisis.** Se descarta después
   de fusionar repetidos, porque el aviso de llegada a veces trae el precio que la
   lista de gama alta omite. No se le pregunta al proveedor: si en la próxima lista
   trae precio, entra en esa.
4. **Los celulares por debajo de 500.000 COP de proveedor quedan por fuera.**
   Ahí caen las "flechas" (Nokia, Alcatel, Fly, Corn) y la gama de entrada. El
   mínimo es `PRECIO_MINIMO_CELULAR_COP` y solo aplica a celulares.
5. **Los cables quedan por fuera.** La lista solo trae los extremos, y sin
   longitud, potencia ni marca no se publica un cable. Los cargadores tampoco
   entran, pero por otra razón: ver la regla 17.
6. **"ORIGINAL" en la sección es la palabra del proveedor.** Si el encabezado dice
   `AUDIFONOS ORIGINALES`, los productos se toman como originales de su marca y
   no se vuelve a preguntar. Cuando la línea trae otra marca entre paréntesis
   —`BUDS BECLAD (SAMSUNG)`— la marca es la de afuera (Beclad) y el paréntesis es
   compatibilidad: en la descripción va "compatible con Samsung", nunca en el
   título. Solo se pregunta cuando la sección no dice "original".
   El parser sigue aplicando esto a `CARGADORES ORIGINAL` aunque esa sección ya
   no se publique: la marca y la autenticidad quedan bien leídas en la hoja de
   descartados, que es lo que se le muestra al proveedor.
7. **Si el mismo equipo aparece con dos precios, vale el menor.** Queda en
   `supuestos` con los dos valores, para que se vea de dónde salió.
8. **Las tablets entran** como categoría propia (iPad, Galaxy Tab, Redmi Pad).
9. **Las abreviaturas de la sección Xiaomi se resuelven solas**: `NOTE 15` se lee
   como Redmi Note 15 y `X8 PRO` como POCO X8 Pro. Queda anotado en `supuestos`
   de cada producto, que es distinto de `revisar`: lo asumido no bloquea la
   publicación, solo deja el rastro de por qué el título dice lo que dice.
10. **Los vacíos de la lista se preguntan siempre, y sin respuesta el producto
    queda por fuera.** Un "Infinix Buds" sin modelo, un proyector sin fabricante,
    un PS5 que no dice si es estándar o digital: se le preguntan a la persona en
    el paso 3, en una sola lista para que se la mande al proveedor. Lo que no se
    responda no se investiga ni se publica; queda en la hoja de descartados con
    el dato que faltó.
11. **Los computadores no se preguntan: sin marca o sin referencia en la lista,
    quedan por fuera.** "LAPTOP ASUS RYZEN 5 7520U (8+512) 15.6"" describe
    decenas de equipos distintos, y el parser no puede saber cuál es. Entra solo
    la línea que trae marca y referencia ("ASUS VIVOBOOK 15 X1504", "HP
    14-em0001la"); el resto va a descartados con el motivo "computador sin
    referencia en la lista", y si el proveedor la manda en la próxima lista,
    entra en esa.

12. **Krono, BMAX, itel, ZTE, Infinix y Tecno no se analizan, en ninguna
    categoría.** Ninguna tienda colombiana de primera mano —Alkosto, Ktronix,
    Éxito, Olímpica, Panamericana— trabaja esas marcas, así que no hay precio
    de mercado admisible contra el cual calcular margen, y un margen sin fuente
    no se puede defender ante el negocio. Están en `MARCAS_EXCLUIDAS` y la
    regla se aplica por marca, sin mirar la categoría: si la lista trae unos
    audífonos Infinix o un parlante Tecno, también quedan fuera.
    La regla nació acotada a celulares, se amplió a tablets y terminó cubriendo
    todo el surtido el mismo día, al confirmarse que el problema no era la
    categoría sino que el retail no vende la marca. Es una decisión de dónde
    poner el esfuerzo, no un juicio sobre el producto: si algún día una de
    estas marcas entra a Alkosto o a Éxito, se saca de la lista y vuelve a
    entrar al análisis.
13. **Los colores se publican asumiendo que el proveedor tiene todos los de la
    ficha.** Las listas casi nunca marcan color —en una lista real de 121
    productos solo 2 líneas traían emojis de color—, y esperar a que el
    proveedor confirme bloquea la publicación entera. La premisa es que están
    disponibles todos los colores que trae la ficha oficial del producto, y así
    queda anotado en cada ficha para que quien carga el inventario sepa de
    dónde salieron. Los emojis de la lista, cuando los hay, siguen mandando
    sobre la premisa: si la línea dice `🖤💙`, esos dos son los que hay.
    Cuando llegue una devolución por un color que no era, se revisa esta regla.

14. **Los accesorios sueltos no se publican.** El control de consola, el pencil
    táctil y el rastreador tipo tag quedan por fuera: se venden solos, con margen
    bajo y rotación lenta, y obligan a investigar un precio de mercado por cada
    uno para muy poca venta. La consola y la tablet sí entran; lo que se vende
    aparte, no. El pencil y el tag ya caían en `variedad`; el control tenía
    categoría propia y por eso se sacó `accesorios_consola` de
    `CATEGORIAS_INCLUIDAS`. La categoría se deja viva en el parser a propósito,
    para que el control aparezca en la hoja de descartados con su motivo y no se
    pierda en `sin_clasificar`. Si algún día se quieren vender, se vuelve a meter
    `accesorios_consola` en `CATEGORIAS_INCLUIDAS` y se saca `variedad` de la
    viñeta del pencil y la del tag.

15. **Ningún producto se publica sin revisar si el fabricante tiene un retiro
    del mercado vigente sobre esa referencia.** Se comprueba en el aviso de
    seguridad del fabricante —Xiaomi lo publica en
    `mi.com/co/support/safety-notice/`, y las demás marcas tienen su
    equivalente— antes de redactar la ficha.
    No es una precaución teórica: la lista del 12/09/2026 traía
    `20.000mAh 33w $100`, que es la **Xiaomi 33W Power Bank 20000mAh (Integrated
    Cable), modelo PB2030MI**, con retiro vigente por riesgo de
    sobrecalentamiento de la batería e incendio en el lote fabricado entre
    agosto y septiembre de 2024. Xiaomi identifica las unidades afectadas por
    número de serie.
    Un mayorista es justo donde aparece el inventario viejo, así que el riesgo
    es real. Cuando haya un aviso, el producto **no entra** hasta que el
    proveedor confirme modelo, lote y números de serie, y se verifiquen uno por
    uno. Queda en la hoja de descartados con el motivo y el enlace al aviso.
    Esta regla vale aunque el producto tenga buen margen: no se negocia un
    riesgo de incendio contra un punto de rentabilidad.

16. **Una foto por debajo del estándar se retoca igual, al máximo que dé la
    fuente.** El estándar de estudio encuadra el producto a 1700 px y buena
    parte del material de Icecat no llega: en la lista del 12/09/2026 fueron 68
    de 105 fotos, con casos de 342×431. Ninguna se deja sin retocar por eso. La
    El `REPETIR` por ampliación deja de ser una compuerta y pasa a ser un
    registro: queda en las hojas de revisión y alimenta el pedido al proveedor,
    pero el producto se publica con lo que hay y la foto se reemplaza cuando
    llegue una mejor. Un producto sin foto no vende; uno con una foto regular,
    sí.
    **No hay que topar resoluciones a mano**: `fotos-estudio-degradado` ya elige
    el lienzo según la fuente (400 a 2000 px) y emite solo las variantes de ese
    ancho hacia abajo.
    Para que la regla se cumpliera de verdad hubo que mover el umbral de esa otra
    skill: `ampliacion_repetir` pasó de 2.0 a **3.0** el 19/09/2026, con el
    porqué escrito en su propio `SKILL.md`. Con 2.0 se retenían once fotos de 105
    y siete productos quedaban con menos de cuatro; con 3.0 entran las de hasta
    3× y siguen fuera las de 3,22× a 4,41×, que se ven mal de verdad.
    El corte sigue existiendo, y eso es a propósito: `marcar.py --aprobar` no
    levanta un `REPETIR`, así que lo que queda retenido va al pedido de fotos al
    proveedor y no al catálogo. Ver `referencias/imagenes.md`.

17. **Los cargadores y las power bank no se publican** (decisión del negocio,
    24/09/2026). Es la misma regla que ya había sacado a los cables y a los
    accesorios sueltos, aplicada hasta el final: **lo que se vende junto al
    equipo entra; lo que alimenta al equipo, no.** Un cubo de 25 W y una batería
    portátil se venden solos, con margen bajo y rotación lenta, y cada
    referencia obliga a investigar un precio de mercado propio —los vatios y los
    miliamperios identifican el producto, así que no hay atajo— para muy poca
    venta. Con esto la lista autorizada queda en **ocho**: celulares, tablets,
    relojes, audífonos, consolas, computadores, proyectores y parlantes.
    Las dos categorías siguen vivas en el parser, igual que
    `accesorios_consola`: así el cubo y la power bank caen en la hoja de
    descartados con su motivo a la vista y no en `sin_clasificar`. Si algún día
    se quieren vender, se vuelven a meter `cargadores` y `power_bank` en
    `CATEGORIAS_INCLUIDAS` y hay que devolver sus categorías al catálogo del
    sitio, que las perdió en la misma decisión —`V62__categorias_sin_suministro.sql`
    borró `cargadores`, `power-banks` y `cables-de-cargador`, y una prueba de
    infraestructura afirma que la línea `TECNOLOGIA` tiene exactamente esas
    ocho—. Publicar una categoría que esta skill no puede llenar es lo que había
    pasado con los cables: nacieron en el catálogo el mismo día en que la regla 5
    decidió que nunca entrarían.

Si el negocio cambia de opinión, se ajustan `CATEGORIAS_INCLUIDAS`,
`CONDICIONES_PUBLICABLES`, `PRECIO_MINIMO_CELULAR_COP`, `DESCARTAR_SIN_PRECIO`,
`DESCARTAR_COMPUTADOR_SIN_REFERENCIA` o `MARCAS_EXCLUIDAS` al inicio
de `scripts/parsear_lista.py`.

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

Los vacíos de la lista van en una sola lista para el proveedor: modelo del
audífono, marca del proyector, edición de la consola. Se pregunta siempre; el
producto que se queda sin respuesta se saca del análisis con el motivo "falta
<dato>" en la hoja de descartados, no se publica a medias. La excepción son los
computadores, que el parser ya descartó si no traen marca y referencia: se
mencionan entre los descartados y no se preguntan.

Detalle de cómo está armada una lista: `referencias/formato-de-listas.md`.

### 4. Investigar cada producto

Antes de empezar, mira qué marcas trae la lista y **pide los permisos de sitio
que vas a necesitar en la extensión del navegador**. Un dominio sin autorizar
corta el lote entero a mitad de camino. La lista de los que hicieron falta la
última vez está en `referencias/fichas-tecnicas.md`.

Para cada producto incluido, en una sola pasada de búsquedas:

- **Nombre comercial oficial** — confirma la referencia real antes de titular.
  Las listas abrevian ("SAMSUNG BAND FIT 3" es la Galaxy Fit3, "WACH 8" es Galaxy
  Watch 8) y a veces cambian de línea ("XIAOMI BUDS 6 PLAY" son **Redmi**).
  Normas del título y tabla de correcciones ya confirmadas:
  `referencias/titulos.md`.
- **Precio promedio del mercado colombiano** — método, fuentes válidas y qué hacer
  con los precios atípicos: `referencias/precios.md`. Dos cosas que deciden la
  calidad del número: el retail de vitrina manda sobre el marketplace, y Alkosto
  solo se consulta por navegador.
- **Aviso de retiro del fabricante** — regla 15. Es una consulta por marca, no
  por producto, y se hace antes de redactar.
- **Ficha técnica y descripción** — estructura y tono en
  `referencias/descripciones.md`; **de dónde salen los datos** en
  `referencias/fichas-tecnicas.md`. Para Xiaomi, que suele ser la marca más
  grande de estas listas, el sitio oficial `mi.com/co` resolvió por sí solo 39
  de 98 productos: trae ficha completa en español, paleta oficial de colores y
  las configuraciones de memoria que el fabricante vende de verdad.
- **Colores reales** — traduce los emojis y confírmalos contra la paleta oficial
  del modelo: `referencias/colores.md`. Si la línea no trae emojis, se asumen
  disponibles todos los colores de la ficha oficial (regla 13) y se deja dicho
  en el producto de dónde salió la lista de colores.
- **¿Esa configuración existe?** La ficha oficial dice qué combinaciones de RAM y
  almacenamiento vende el fabricante, y el proveedor a veces ofrece otras. En la
  lista del 12/09/2026 aparecieron dos: un Redmi Note 15 Pro 5G de 8+512 cuando
  Xiaomi solo publica 8+256, y un Note 15 Pro+ de 8+256 cuando solo publica
  12+512. Pueden ser versiones de otro mercado o un error de la lista, pero
  publicarlas sin preguntar es venderle al cliente una configuración que el
  fabricante no reconoce. Se marcan en `revisar` y se preguntan.

#### Cómo se corre el paso 4

Tres scripts, en este orden. Ninguno decide por su cuenta lo que exige criterio.

```bash
# 1. Cosecha de precios: Éxito, Olímpica y Jumbo por su catálogo VTEX.
python3 scripts/precios.py catalogo/productos.json --salida catalogo/precios-vtex.json

# 2. Alkosto va por navegador y el emparejamiento final lo hace una persona,
#    producto por producto, en catalogo/alkosto-vitrina.json. Ver precios.md.

# 3. Decide el precio de mercado y el margen con las reglas de precios.md.
python3 scripts/asignar_precios.py catalogo/productos.json \
    --vtex catalogo/precios-vtex.json \
    --alkosto catalogo/alkosto-vitrina.json \
    --descartar catalogo/precios-descartados.json

# 4. Descripciones y metadatos: la estructura la arma el script, la prosa la
#    escribes tú en catalogo/prosa.json. Ver descripciones.md.
python3 scripts/redactar_fichas.py catalogo/productos.json \
    --prosa catalogo/prosa.json \
    --icecat catalogo/icecat/fichas --mi catalogo/mi-fichas.json
```

`asignar_precios.py` **reevalúa el corpus cada vez que corre**, así que apretar
una regla de emparejamiento en `precios.py` limpia lo que ya está en disco sin
volver a consultar las tiendas. Y los dos archivos que escribe una persona
—`alkosto-vitrina.json` y `precios-descartados.json`— llevan el motivo de cada
decisión: un precio sin fuente, o un descarte sin razón, no se puede defender
cuando el negocio pregunte.

Escribe los resultados de vuelta en `productos.json` (`precio_mercado_cop`,
`fuentes_precio`, `descripcion`, `meta_titulo`, `meta_descripcion`,
`colores_oficiales`, `titulo` corregido). Guarda cada búsqueda con su fuente: el
Excel lleva una columna de fuentes y sin ellas el precio no es verificable.

**Un producto sin ficha oficial no se queda sin descripción, pero tampoco se la
inventa.** Se escribe una corta con lo que el nombre comercial y la línea del
proveedor establecen, y una nota que diga qué falta y que se le pidió al
proveedor. En la corrida del 19/09/2026 fueron 16 de 96, sobre todo Apple, JBL y
marcas que no publican ficha para Colombia.

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
`crudas/`.

**Lo descargado se filtra antes de retocar.** El catálogo de un fabricante no
trae solo fotos, y las que trae no siempre sirven para una ficha:

```bash
python3 scripts/filtrar_fotos.py crudas/               # aplica
python3 scripts/filtrar_fotos.py crudas/ --diagnostico # solo mide y explica
```

Descarta dos cosas y deja lo descartado en `descartadas/<id>/` con un
`motivos.json`, para poder recuperar una foto rechazada por error:

1. **Lo que no es una foto**: pictogramas de característica, logos y etiquetas
   de eficiencia energética. Se reconocen por el modo del archivo —una foto
   llega en color verdadero, un pictograma en escala de grises o en paleta—.
   En una corrida real eran 18 de 146.
2. **Las fotos donde el producto sale cortado**, midiendo cuánto producto toca
   cada borde. Son tomas de detalle o de estilo de vida: legítimas en la página
   del fabricante, inservibles en una ficha donde el cliente quiere ver el
   equipo completo.

Si un producto queda con menos de cuatro, el script lo dice y esas fotos entran
al pedido al proveedor. **Quedarse con dos fotos buenas es mejor que completar
cuatro con un pictograma.**

Si la persona ya tiene fotos del proveedor o propias, se salta directo al filtro.

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

### 6. Retocar con `fotos-estudio-degradado`

**Esta skill no retoca imágenes.** Todas las fotos del ecommerce pasan por
`fotos-estudio-degradado`, que es la que define el estilo del catálogo: fondo
blanco, producto al 85 % del lienzo, sombra de contacto —que es la que le pone el
resplandor alrededor—, maestra JPEG y AVIF web. Que el estilo lo decida un solo lugar es justamente el punto: si cada
skill recortara a su manera, el catálogo dejaría de verse parejo.

```bash
python3 "${CLAUDE_SKILL_DIR}/../fotos-estudio-degradado/scripts/procesar.py" \
    catalogo/fotos/crudas -o catalogo/fotos/estudio --variantes --segundo-plano
```

**No hace falta pasarle `--por-producto`.** Esa skill agrupa sola en cuanto las
fotos le llegan en subcarpetas, y `descargar.py` siempre las deja así
(`crudas/<producto>/`). La salida queda:

```
estudio/
└── <producto>/
    ├── maestra/<producto>-01.jpg    la mejor versión de cada toma
    ├── 1200/<producto>-01.avif      un subdirectorio por ancho real
    └── 800/…  480/…
```

Esa forma es exactamente la que consume el paso 7, así que **no hay que
reacomodar nada en el medio**. Tampoco hay que topar resoluciones: el lienzo se
elige según lo que da cada original —400, 480, 800, 1200 o 2000 px— y solo se
emiten las variantes de ese ancho hacia abajo. Una foto de 400 px sale en `400/`
y nada más.

Tarda entre 5 y 20 segundos por foto, así que con un lote de catálogo va en
segundo plano. Lee su `SKILL.md` antes: tiene su propio flujo de revisión y hay
que mirar las hojas `revision-*.jpg` antes de dar el lote por bueno.

#### Lo que va a pasar con fotos de Icecat, y qué hacer

- **La mitad larga no llega a los 1700 px** que pide el encuadre: en la corrida
  del 19/09/2026 fueron 68 de 105, con casos de 342×431. Se retocan igual
  (regla 16) y salen marcadas `REVISAR` por ampliación.
- **Lo que pase de 3× de ampliación se retiene y no escribe archivo.** Es una
  compuerta dura: `marcar.py --aprobar` responde «está en REPETIR y eso no se
  aprueba a ojo». No la rodees. Esas fotos van al pedido al proveedor, y si el
  negocio quiere moverla otra vez, el umbral es `ampliacion_repetir` en el
  `config.json` de esa skill —con el porqué del valor actual escrito ahí mismo—.
- **Producto cortado.** `filtrar_fotos.py` ya lo quitó antes, así que si vuelve a
  aparecer aquí es que el encuadre quedó justo al límite.

### 7. Armar entregables

```bash
python3 scripts/construir_entregables.py catalogo/productos.json \
    --imagenes catalogo/fotos/estudio --salida catalogo/entregables
```

`--imagenes` apunta **directo a la salida del retoque**: el script entiende la
forma `<producto>/maestra/` y toma de ahí las fotos para el ZIP. Produce:

- `catalogo-<fecha>.zip` — una carpeta por producto con sus fotos y un `.txt` con
  título, metadatos y descripción. Los que tienen menos de cuatro fotos llevan
  además un `FOTOS-PENDIENTES.md`.
- `comparativo-<fecha>.xlsx` — hoja **Comparativo** con las cuatro columnas
  pedidas (título, precio de lista, promedio del mercado, ganancia), más hojas de
  **Detalle** (margen %, colores, fuentes, pendientes) y **Descartados**.

### 8. Entregar

Preséntale los dos archivos y, en dos o tres líneas, lo que necesita saber:
productos listos, productos que quedaron con pendientes y cualquier caso donde el
promedio del mercado esté por debajo del precio de lista. Ese caso significa que a
ese precio se pierde plata: márcalo, no lo publiques callado.

## Lo que el parser ya resuelve solo

No hay que volver a hacerlo a mano en cada lista:

- **Prefijos de exportación de WhatsApp** (`[10:05, 12/09/2026] +57 300 123 4567:`)
  se quitan antes de leer la línea.
- **Encabezados sin la negrita de WhatsApp.** El parser reconocía la sección
  solo si la línea venía envuelta en `*asteriscos*`, y un mensaje copiado desde
  una vista que ya los renderizó llega sin ellos. Pasó con la lista del
  12/09/2026: no se reconoció **ninguna** de las 30 secciones, así que 29
  productos quedaron sin marca, 3 tablets se publicaron como celulares, una
  Galaxy Tab se descartó por el mínimo que solo aplica a celulares, los siete
  cargadores pidieron confirmar autenticidad que el encabezado `CARGADORES
  ORIGINAL` ya daba, y 7 equipos de marca excluida —6 Infinix y 1 ZTE— se
  colaron al análisis. Ahora la línea sin marcadores también cuenta como
  encabezado, con dos condiciones que un encabezado siempre cumple y un
  producto casi nunca: no trae precio y no pasa de seis palabras. El tope de
  palabras no es adorno —la frase con que el proveedor cierra la lista, “TE
  BRINDAMOS UNA AMPLIA VARIEDAD DE TECNOLOGÍA…”, contiene VARIEDAD y sin él
  abría esa sección—. Y sin negrita el encabezado de solo marca tiene que ser
  **exactamente** la marca: `SAMSUNG` es la sección, `SAMSUNG BAND FIT 3` es un
  producto de esa sección. Con la negrita todo sigue igual que antes: las dos
  listas de ejemplo dan un JSON idéntico byte a byte.
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
- **Submarcas de Xiaomi**: las referencias que el proveedor escribe bajo
  "XIAOMI" pero el fabricante publica como Redmi (`WATCH 5 ACTIVE`,
  `BUDS 6 PLAY`, `PAD 2`…) se corrigen solas con `SUBMARCA_XIAOMI` y dejan la
  nota en `supuestos`. La tabla completa está en `referencias/titulos.md`.

## Cosas que se rompen si no se cuidan

**Un precio de marketplace no es el precio de mercado.** Éxito, Olímpica y
Jumbo venden en el mismo sitio su inventario propio y el de terceros, y los
terceros tiran el precio por debajo de la vitrina. Tomar ese número hunde
categorías enteras: en la lista del 12/09/2026 los parlantes JBL daban mediana
**−1 %** con precios de marketplace y **+14 %** contra la vitrina de Alkosto; el
Charge 6 solo pasó de −20 % a +14 %. La vitrina manda, el marketplace es el
último recurso y los dos niveles nunca se promedian juntos. Detalle y receta de
Alkosto en `referencias/precios.md`.

**Si el retail ya vende la generación siguiente, el precio va a quedar bajo
costo.** No es mala suerte: el proveedor está saliendo de inventario viejo.
Pasó con el JBL Grip, el Xtreme 4, el PartyBox 320 y el Motorola Edge 50 Fusion
en la misma lista. Cuando la vitrina no tenga la referencia pero sí la
siguiente, anótalo: cambia la decisión del negocio, no solo el número.

**La sección "XIAOMI" no es toda Xiaomi.** Varias referencias son de la línea
Redmi y el fabricante las publica con otro nombre y otra ficha. El parser
corrige las conocidas con `SUBMARCA_XIAOMI`, pero la tabla es explícita a
propósito —las Smart Band, el Watch S4 y las power bank sí son Xiaomi—, así que
una referencia nueva se verifica contra el catálogo oficial antes de titular.

**El precio del sitio no tiene por qué ser el promedio.** El promedio es el
referente para saber cuánto margen hay. Si se publica exactamente en el promedio
no hay ventaja frente a Alkosto o Mercado Libre, y si el proveedor subió, el
margen puede ser negativo sin que nadie lo note. Muestra el número y deja la
decisión de precio en manos del negocio.

**"Original" y "compatible" no son lo mismo.** Publicar uno como el otro es
publicidad engañosa frente a la Ley 1480 y termina en devoluciones. La regla ya
está tomada: la sección que dice "ORIGINAL" manda, y la marca entre paréntesis
es compatibilidad y va en la descripción. Solo cuando la sección no lo diga se
pregunta antes de publicar.

**En las tablets, el mismo número significa dos cosas.** `IPAD AIR 11` puede ser
la pantalla de 11 pulgadas o la generación. Confirma cuál antes de titular: es el
error más caro de esta categoría porque cambia el producto entero.

**A los cables les faltan tres datos, y por eso quedaron por fuera.** La lista
solo dice los extremos (`TIPO C - LIGHTNING`). Longitud, potencia soportada y
marca son exactamente las tres razones por las que un cliente devuelve un cable.
Si algún día se quieren vender, se vuelve a meter `cables` en
`CATEGORIAS_INCLUIDAS` y se le piden esos tres datos al proveedor.

**La RAM virtual no es RAM.** En la gama media las listas escriben `(8+8+256)`:
8 GB físicos, 8 GB "extendidos" tomados del almacenamiento, 256 GB de disco.
Publicar 16 GB es falso y el cliente lo comprueba en la configuración del equipo.
Va la RAM física en el título y la extendida como característica aparte.

**Un mismo equipo llega varias veces.** El anuncio de "LLEGANDO", el mensaje de
mercancía nueva y la lista larga del día traen los mismos modelos con distinta
escritura. El script fusiona los que no se contradicen en red, RAM, marca ni tipo
de SIM (un aviso que no menciona la SIM no contradice a la lista que sí), y une
el anuncio sin capacidad con la lista cuando esta trae una sola variante al
mismo precio. Si el precio no coincide, se queda el menor y lo anota.

**4G y 5G son productos distintos.** `A17 4G (8+256)` a $630 y `A17 5G (8+256)` a
$700 son dos referencias. La red va en el título, no en la descripción.

**Los colores son variantes, no parte del título.** Un mismo modelo con cuatro
colores es un producto con cuatro variantes.

## Archivos

```
scripts/parsear_lista.py          paso 2  lista.txt → productos.json + revision.md
scripts/precios.py                paso 4  cosecha precios VTEX de Éxito, Olímpica y Jumbo
scripts/asignar_precios.py        paso 4  decide el precio de mercado y el margen
scripts/redactar_fichas.py        paso 4  prosa + ficha oficial → descripción y metadatos
scripts/icecat_local.py           paso 5  trae fichas e imágenes de Open Icecat
scripts/preparar_fotos.py         paso 5  arma urls.csv, el descargador y el pedido al proveedor
scripts/filtrar_fotos.py          paso 5  quita pictogramas y fotos con el producto cortado
scripts/construir_entregables.py  paso 7  productos.json + fotos de estudio → ZIP + Excel
scripts/organizar_imagenes.py     FUERA DEL FLUJO desde el 19/09/2026: ver la nota de abajo
referencias/formato-de-listas.md  anatomía de los mensajes de proveedor
referencias/titulos.md            fórmula de títulos y nombres ya confirmados
referencias/descripciones.md      estructura de la descripción y metadatos
referencias/fichas-tecnicas.md    de dónde sale la ficha oficial de cada marca
referencias/precios.md            método de investigación de precios
referencias/colores.md            emojis → colores publicables
referencias/imagenes.md           estándar de fotos y origen de las imágenes
plantillas/producto.txt           plantilla del archivo de cada producto
plantillas/env.ejemplo            plantilla de credenciales de Icecat
```

`organizar_imagenes.py` reordenaba la salida plana del retoque en una carpeta por
producto. Ya no hace falta: `fotos-estudio-degradado` agrupa sola cuando las
fotos le llegan en subcarpetas —que es siempre en este flujo— y entrega
justamente esa forma. El script espera `maestras/` y `escritorio/`, que solo
aparecen si se fuerza `--plano`, así que **contra la salida normal no hace
nada**. Se deja en el repositorio por si alguna vez se procesa un lote plano a
mano; no lo metas de vuelta en el flujo sin comprobar antes qué forma tiene la
salida.
