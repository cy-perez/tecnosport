---
name: listas-de-proveedor
description: Convierte las listas de productos que mandan los proveedores por WhatsApp —con viñetas de emojis, precios acotados tipo $1.850 y colores marcados con corazones— en productos listos para publicar, filtrando usados y categorías que no se venden, estandarizando títulos, investigando el precio promedio del mercado colombiano, redactando la descripción y traduciendo los emojis de color a colores reales; organiza la base por modelo —el equipo, con la memoria y el color como variantes—; deja una carpeta por modelo con su ficha y sus fotos, que la persona descarga y fotos-estudio-degradado retoca, y un Excel comparativo de precios y ganancia. Úsala siempre que llegue una lista o listado de proveedor, mayorista o distribuidor; cuando alguien diga "procesa esta lista", "la lista de hoy", "lista de gama alta", "listado de variedad", "pasa esto a productos", "sube estos equipos a la tienda" o "cuánto me gano con estos productos"; o cuando pegue un texto con equipos, capacidades y precios aunque no lo llame lista. Covers WhatsApp supplier price list parsing, ecommerce catalog preparation, Colombian market price research and margin comparison.
---

# Listas de proveedor → catálogo

Las listas llegan como mensajes de WhatsApp escritos a mano: sin estructura fija,
con abreviaturas del oficio, emojis como viñetas y precios recortados. Publicarlas
a mano cuesta horas y se cometen errores caros (subir un usado como nuevo, poner
un color que no existe, vender por debajo del costo).

Esta skill separa dos trabajos: **lo mecánico lo hace un script** (leer la lista,
clasificar, extraer capacidades, precios y colores) y **lo que exige criterio lo
haces tú** (confirmar nombres comerciales, investigar precios de mercado, redactar
descripciones). El script nunca inventa: lo que no
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

16. **Las fotos vuelven a la skill, por carpeta de modelo** (decisión del
    negocio, 08/10/2026; reemplaza la del 25/09/2026, que las había sacado).
    Cada modelo tiene su carpeta en `catalogo/entregables/fichas/<Modelo>/`
    (`scripts/fichas.py`): la persona descarga las fotos en `Fotos originales/`
    —`<id del modelo>_1.jpg`, `_2.png`…— y `fotos-estudio-degradado` deja la
    retocada en `Fotos procesadas/` **con el mismo nombre** (la extensión puede
    cambiar). Las fotos son **de referencia**: no van por color; el color es una
    variante que el cliente elige al comprar.
    Por qué volvieron: la información estaba repartida en `catalogo/` —la ficha
    en `icecat/fichas/<id>.json` con las fotos al lado, la prosa en un JSON, lo
    de mi.com en otro— y la carpeta de las tomas retocadas se perdió sin que nada
    lo dijera. El proveedor de tecnología **no manda fotos**, así que las fuentes
    son la descarga manual y lo que ya bajó Icecat (`referencias/imagenes.md`).
    La regla de antes sigue valiendo en una cosa: **lo que se entrega no finge
    que las fotos existen**. Una foto REPETIR no se devuelve; un modelo sin fotos
    lo dice la salida del paso 5.

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
  internet completo. Icecat se ejecuta aquí mismo, de corrido, sin pasarle nada
  al usuario. Las credenciales salen de las variables de entorno `ICECAT_USER` e
  `ICECAT_PASSWORD` de su shell; si faltan, pídeselas con `setx` o con un `.env`
  que no se suba al repositorio, nunca en el chat.
- **En claude.ai**, el entorno solo alcanza unos pocos dominios. Ahí el paso de
  Icecat se prepara y se le entrega a la persona para que lo corra en su equipo.

Comprueba cuál es el caso antes del paso 4: si un `curl` a un dominio externo
funciona, estás en Claude Code.

En Claude Code conviene trabajar sobre una carpeta del proyecto, por ejemplo
`catalogo/`, y mantener fuera del control de versiones lo pesado y lo generado:
el índice de Icecat pesa 290 MB y los entregables tampoco van al repositorio.

## Flujo

### 1. Guardar la lista tal cual

Copia el mensaje completo a `lista.txt` **sin corregir nada**: los emojis, los
espacios raros y los saltos de línea son justamente las señales que usa el parser.
Si llegaron dos mensajes (gama alta y variedad), pégalos en el mismo archivo.

### 2. Parsear y comparar con lo conocido

```bash
python3 scripts/parsear_lista.py lista.txt --salida productos.json --reporte revision.md
python3 scripts/comparar_lista.py productos.json
```

El parser deja `productos.json` (productos, repetidos que se fusionaron,
descartados y líneas sin clasificar) y `revision.md`, un resumen legible.

La comparación, que se corre **una vez por lista y antes del paso 4**, cruza cada
producto con `referencias/conocidos.json` y lo clasifica:

| Estado | Qué hereda de la base | Qué queda pendiente para el paso 4 |
|---|---|---|
| nuevo | nada | todo: precio, ficha y descripción |
| sin precio vigente | descripción, metadatos, colores | solo el precio (venció a los 7 días o nunca lo tuvo) |
| costo cambiado | todo, con el margen recalculado al costo de hoy | nada |
| sin cambios | todo | nada |

**Los colores se heredan solo si la lista marca hoy los mismos emojis** que la vez
en que se decidieron. Si marca otros —o ninguno, cuando antes marcaba—, el
producto queda sin colores, sin la sección de colores en la descripción y con la
tarea `colores` pendiente: confírmalos contra la paleta oficial (regla 13) y
escribe `colores_oficiales`; la sección se rehace al consolidar. Ningún script
hace esta tarea.

Y anota:

- los **desaparecidos** —conocidos que vinieron en la última lista de su mensaje
  y hoy no, aunque el mensaje llegó—, que son los que hay que dejar de ofrecer.
  Los que ya faltaban antes quedan como ausentes y no se repiten;
- los **posibles el mismo**: un nuevo y un desaparecido que solo difieren en la
  SIM. Casi siempre es la anotación `*1 SIM*` pegada a otra línea. Si es el
  mismo equipo, agrega la equivalencia que propone el reporte y vuelve a parsear;
- los conocidos que vinieron pero **no entran** (sin precio, bajo el mínimo).

Escribe `cambios.md` junto a `productos.json`. Lee los dos reportes antes de
seguir.

Con la lista del 08/10/2026 fueron 18 nuevos, 7 sin precio vigente, 19 con otro
costo y 46 sin cambios: al paso 4 llegaron 25 de 90 productos.

### 3. Revisar con la persona antes de investigar

Muéstrale en el chat, en pocas líneas: cuántos productos entraron, cuántos se
descartaron y por qué, la lista de alertas `revisar`, y de `cambios.md` cuántos
son nuevos, qué costos cambiaron —en especial los que quedan **por debajo del
costo**— y cuáles desaparecieron, que son los que hay que dejar de ofrecer. Pregunta solo lo que
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

**La ficha técnica sale, en este orden** (decisión del negocio, 08/10/2026):

1. **El sitio oficial de la marca.** Samsung, Apple, Honor, Motorola, OPPO,
   realme, TCL y Lenovo (su PSREF) se leen directamente; JBL publica la ficha en
   PDF, que también se lee. **Xiaomi, JBL (la página), Amazon y Nintendo no se
   leen de forma automática**: Xiaomi responde 403 a los agentes de IA, JBL pone
   un captcha y Amazon y Nintendo excluyen a Claude en su robots.txt. Esas
   barreras no se eluden —ni con otro agente de usuario ni desde el navegador
   de la persona—: la persona guarda la página de especificaciones en la carpeta
   «Fuente de la marca» del modelo y se lee de ahí. Detalle por marca en
   `referencias/fichas-tecnicas.md`.
2. **Open Icecat**, solo si la marca no da la ficha, y **solo por código**: la
   tabla la arma `redactar_fichas.py`, y la prosa nunca se escribe a partir de
   una ficha de Icecat. Su licencia anula el permiso si los datos se usan para
   «automated synthetic content creation» (cláusula 10).
3. **La caja del producto**, cuando no hay ninguna de las dos.

Para cada producto con algo `pendiente` —los nuevos, entero; los de precio
vencido, solo el precio—, en una sola pasada de búsquedas. Lo que se heredó de la
base no se vuelve a investigar. **El aviso de retiro (regla 15) sí se revisa para
todas las marcas de la lista**, conocidas o no: un retiro puede publicarse después
de la primera vez que se investigó el producto.

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
- **Ficha técnica y descripción, una por modelo** — estructura y tono en
  `referencias/descripciones.md`; **de dónde salen los datos** arriba y en
  `referencias/fichas-tecnicas.md`. La descripción vale para todas las memorias
  del modelo: no nombra la capacidad ni los colores, que son variantes.
- **Paleta oficial completa** — en `colores_oficiales` van **todos** los colores
  que el fabricante publica para el modelo, no solo los que marca la lista: la
  base la guarda como la paleta del modelo. Los que marca la lista los sugiere
  `comparar_lista.py` en `colores_sugeridos` (`referencias/colores.md`), y se
  confirman en el panel al revisar el borrador.
- **¿Esa configuración existe?** La ficha oficial dice qué combinaciones de RAM y
  almacenamiento vende el fabricante, y el proveedor a veces ofrece otras. En la
  lista del 12/09/2026 aparecieron dos: un Redmi Note 15 Pro 5G de 8+512 cuando
  Xiaomi solo publica 8+256, y un Note 15 Pro+ de 8+256 cuando solo publica
  12+512. Pueden ser versiones de otro mercado o un error de la lista, pero
  publicarlas sin preguntar es venderle al cliente una configuración que el
  fabricante no reconoce. Se marcan en `revisar` y se preguntan.

#### Cómo se corre el paso 4

Cuatro scripts, en este orden. Ninguno decide por su cuenta lo que exige
criterio, y los cuatro procesan solo lo que el producto tiene `pendiente`
(`scripts/pendientes.py`): un precio, una ficha o una descripción heredados no se
tocan. Sin la comparación procesan todo, como antes.

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

# 4. Ficha técnica oficial de Open Icecat, que es de donde salen las
#    especificaciones. Detalle del emparejamiento en fichas-tecnicas.md.
python3 scripts/icecat_local.py diagnostico --icecat-id 12345678
python3 scripts/icecat_local.py indice --productos catalogo/productos.json
python3 scripts/icecat_local.py buscar --productos catalogo/productos.json
python3 scripts/icecat_local.py traer  --solo-confirmados \
    --salida-contenido catalogo/icecat/fichas

# 5. Descripciones y metadatos, una por modelo: la estructura la arma el script,
#    la prosa la escribes tú en catalogo/prosa.json, por id de modelo, y la ficha
#    de la marca en catalogo/fichas-marca.json. Ver descripciones.md.
python3 scripts/redactar_fichas.py catalogo/productos.json \
    --prosa catalogo/prosa.json \
    --marca catalogo/fichas-marca.json --icecat catalogo/icecat/fichas
```

`fichas-marca.json` lleva, por id de modelo, la fuente, la URL, la fecha de
consulta y las filas: `{"samsung-galaxy-a57-5g": {"fuente": "samsung.com/co",
"url": "https://…", "fecha": "2026-10-08", "f": {"Pantalla": "…", "Batería":
"…"}}}`. Las filas de memoria se descartan solas: son de la configuración.

`traer` escribe además un `fotos/urls-icecat.csv` con enlaces de fotos: es una
lista de dónde descargar a mano lo que falte en `Fotos originales/`.

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

**Cada título que corrijas va también a `referencias/equivalencias.json`**, con el
`id_lista` del producto como clave. Si no, la lista siguiente lo vuelve a traer
crudo y se investiga otra vez como si fuera nuevo. Formato y reglas en
`referencias/titulos.md`.

**Un producto sin ficha oficial no se queda sin descripción, pero tampoco se la
inventa.** Se escribe una corta con lo que el nombre comercial y la línea del
proveedor establecen, y una nota que diga qué falta y que se le pidió al
proveedor. En la corrida del 19/09/2026 fueron 16 de 96, sobre todo Apple, JBL y
marcas que no publican ficha para Colombia.

### 5. Armar entregables

```bash
python3 scripts/construir_entregables.py catalogo/productos.json \
    --salida catalogo/entregables
```

Produce:

- `fichas/<Modelo>/` — una carpeta por modelo, que se queda de una lista a la
  siguiente: `<id del modelo>-ficha.txt` (el modelo, sus configuraciones de esta
  lista con costo, precio de mercado y colores sugeridos, la descripción, los
  supuestos, los pendientes y las fuentes) y `Fotos originales/`, vacía la
  primera vez. En las marcas que no se leen de forma automática trae además
  `Fuente de la marca/`. La carpeta se reconoce por el archivo de ficha, no por
  el nombre; las pulgadas van con `″` porque Windows no admite `"`.
- `comparativo-<fecha>.xlsx` — hoja **Comparativo** con las cuatro columnas
  pedidas (título, precio de lista, promedio del mercado, ganancia), más hojas de
  **Detalle** (margen %, colores, fuentes, pendientes) y **Descartados**.

La salida dice qué modelos no tienen fotos todavía. Nada de esto borra: las
fotos que dejó la persona no se tocan.

### 5b. Fotos

Con las fotos en `Fotos originales/` de cada modelo:

```bash
python3 "${CLAUDE_SKILL_DIR}/../fotos-estudio-degradado/scripts/procesar_fichas.py" catalogo/entregables/fichas
python3 scripts/conocidos.py fotos catalogo/entregables/fichas --escribir
```

El primero deja cada foto retocada en `Fotos procesadas/` con el mismo nombre,
procesando solo lo nuevo; las hojas de revisión quedan en `fichas/_estudio/salida/`
(ver la skill de fotos). El segundo anota cada foto procesada en la base, con su
huella: las carpetas viven fuera del repositorio, y el registro es lo que nota
que una se perdió.

### 6. Guardar lo investigado

```bash
python3 scripts/conocidos.py consolidar catalogo/productos.json            # simula
python3 scripts/conocidos.py consolidar catalogo/productos.json --escribir
```

Lleva a `referencias/conocidos.json` cada producto terminado (con descripción):
el precio de mercado con sus fuentes y su fecha, la descripción, los metadatos,
los colores y los supuestos que escribiste al investigar; y el costo y la fecha
de la lista. Es lo que evita investigar otra vez, en la lista siguiente, lo que
ya se investigó: contra la del 08/10/2026 la base reconoce 72 de 90 productos.

La base va en el repositorio y no se edita a mano. Un precio de mercado vale
**7 días** desde la fecha de su consulta (decisión del negocio, 08/10/2026), que
escribe `asignar_precios.py`; un precio que se copió de la base conserva la fecha
que traía, y consolidar días después no lo rejuvenece. Lo que salga «sin
terminar» no se guarda: dilo en la entrega.

Consolidar se niega a correr sobre una corrida que no pasó por la comparación,
y no guarda los supuestos que hablan del precio o citan una línea —«precio
tomado del bloque PRECIOS DE VENTA (línea 530…)»—: son de esa lista y serían
falsos en la siguiente. Si escribes un supuesto que sí vale para el producto,
no lo ates a una línea ni a un precio.

### 6b. Llevarla al catálogo

La lista mueve el catálogo por la API, no a mano (`docs/adr/0075`):

```bash
python3 scripts/exportar_lista.py catalogo/productos.json --salida catalogo/lista-api.json
node tools/importar-lista-tecnologia.mjs catalogo/lista-api.json --proveedor <id>             # simula
node tools/importar-lista-tecnologia.mjs catalogo/lista-api.json --proveedor <id> --escribir
```

Solo se exportan los modelos con descripción; los demás se dicen. Lo que ya se
vende se renueva —costo del día y 2 unidades libres por color—, lo desaparecido
deja de ofrecerse, y lo nuevo queda como borrador en `/admin/tecnologia`, donde
la persona marca los colores que hay de cada configuración y fija el precio de
venta. **La lista no toca el precio de venta**: si la salida avisa que el costo
alcanzó el precio, dilo en la entrega.

Cuando la persona haya aprobado en el panel:

```bash
node tools/importar-lista-tecnologia.mjs --fotos --publicar             # simula
node tools/importar-lista-tecnologia.mjs --fotos --publicar --escribir
```

Sube la principal y la galería de cada modelo aprobado que todavía no tiene
imagen, desde `Fotos procesadas/` de su carpeta, y lo publica. Un modelo sin
fotos procesadas se queda en borrador y se dice. La sesión es `--token` o
`TS_TOKEN_ADMIN`; no la pidas por terminal.

### 7. Entregar

Preséntale los dos archivos y, en dos o tres líneas, lo que necesita saber:
productos listos, productos que quedaron con pendientes, los desaparecidos —la
hoja del mismo nombre en el Excel— y cualquier caso donde el promedio del
mercado esté por debajo del precio de lista. Ese caso significa que a
ese precio se pierde plata: márcalo, no lo publiques callado.

## De dónde salen las fotos

El proveedor de tecnología no manda fotos (08/10/2026). Las fuentes, y lo que
no sirve, están en `referencias/imagenes.md`: **no se usan las salas de prensa
del fabricante**, cuyas condiciones autorizan uso editorial, no vender. Las de
Open Icecat se pueden usar con su licencia —aviso, descargo y nota de la
modificación (cláusulas 1 y 2)—, pero casi siempre llegan en miniatura: de las
134 que se bajaron para la lista del 02/10/2026, 66 no pasaban de 800 px.

`preparar_fotos.py` (enlaces y pedido al proveedor) y `filtrar_fotos.py` (quita
pictogramas, logos y tomas cortadas) siguen en `scripts/`.

## Lo que el parser ya resuelve solo

No hay que volver a hacerlo a mano en cada lista:

- **Viñetas de temporada.** Cualquier emoji al inicio de una línea con precio es
  una viñeta, esté o no en las tablas. La lista del 08/10/2026 trajo 🎃 y, antes
  de esto, perdió 52 líneas —23 equipos publicables— en `sin_clasificar`. Con
  ella llegaron otros tres defectos que ya no se repiten: la SIM escrita en la
  misma línea (`*1 SIM*`) se ignoraba y fusionaba dos referencias, un precio sin
  `$` descartaba el equipo, y la serie F de POCO salía como Xiaomi.
- **Títulos ya confirmados.** Lo que se corrigió en una lista anterior está en
  `referencias/equivalencias.json` y se aplica solo: el producto sale con su id y
  su título definitivos, el id de la lista queda en `id_lista` y la alerta de
  confirmar el nombre no vuelve a aparecer. En la lista del 08/10/2026 fueron 29
  de 90 productos.
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
  si coinciden el modelo, la capacidad y lo que se sabe de marca, red, RAM y SIM;
  ante la duda quedan separados, porque juntar un Pro con un Pro Max es peor que
  tener dos fichas. El precio **no** separa: si difiere, vale el menor (regla 7).
  Una línea que no menciona la SIM no contradice a la que sí, así que el
  `X5D 4G (4+128)` sin SIM a $380 se fusiona con el de `*1 SIM*` a $370 (lista
  del 08/10/2026, confirmado por el negocio ese día).
- **SIM y eSIM**: `SIM/ESIM`, `DUAL SIM`, `1 SIM` y `ESIM` salen como atributo, no
  como parte del nombre.
- **RAM virtual** (`8+8`): se publica solo la física y queda la nota de que el
  proveedor sumaba la virtual.
- **Submarcas de Xiaomi**: las referencias que el proveedor escribe bajo
  "XIAOMI" pero el fabricante publica como Redmi (`WATCH 5 ACTIVE`,
  `BUDS 6 PLAY`, `PAD 2`…) se corrigen solas con `SUBMARCA_XIAOMI` y dejan la
  nota en `supuestos`. La tabla completa está en `referencias/titulos.md`.
- **Referencias sin marca ya identificadas**: `PROYECTOR L1` es el **Xiaomi
  Smart Projector L1** (decisión del negocio, 02/10/2026) y no se vuelve a
  preguntar. Está en `REFERENCIAS_SIN_MARCA`, solo actúa si la línea no trae
  marca —`PROYECTOR EPSON L1` y `PROYECTOR L1 PRO` no se tocan— y deja la nota
  en `supuestos`. Una referencia nueva entra a esa tabla solo cuando el negocio
  la confirma; mientras tanto se aplica la regla 10.

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
scripts/comparar_lista.py         paso 2  productos.json contra conocidos.json → pendientes + cambios.md
scripts/pendientes.py             paso 4  qué le toca a cada script según lo pendiente
scripts/precios.py                paso 4  cosecha precios VTEX de Éxito, Olímpica y Jumbo
scripts/asignar_precios.py        paso 4  decide el precio de mercado y el margen
scripts/icecat_local.py           paso 4  trae la ficha técnica oficial de Open Icecat
scripts/redactar_fichas.py        paso 4  prosa + ficha oficial → descripción y metadatos
scripts/construir_entregables.py  paso 5  productos.json → carpetas de modelo + Excel
scripts/fichas.py                 paso 5  la carpeta de cada modelo en catalogo/entregables/fichas
scripts/conocidos.py              paso 6  guarda lo investigado en referencias/conocidos.json;
                                  `fotos` registra las fotos procesadas de cada modelo
scripts/preparar_fotos.py         enlaces de descarga y pedido de lo que falte (regla 16)
scripts/filtrar_fotos.py          quita pictogramas, logos y tomas cortadas (regla 16)
scripts/organizar_imagenes.py     FUERA DEL FLUJO desde el 19/09/2026: ver la nota de abajo
referencias/formato-de-listas.md  anatomía de los mensajes de proveedor
referencias/titulos.md            fórmula de títulos y nombres ya confirmados
referencias/equivalencias.json    id de la lista → id y título definitivos; lo aplica el parser
referencias/conocidos.json        productos ya investigados; lo escribe conocidos.py, no a mano
referencias/descripciones.md      estructura de la descripción y metadatos
referencias/fichas-tecnicas.md    de dónde sale la ficha oficial de cada marca
referencias/precios.md            método de investigación de precios
referencias/colores.md            emojis → colores publicables
referencias/imagenes.md           estándar y fuentes de las fotos (regla 16)
pruebas/test_parsear_lista.py     las listas de ejemplo contra su revisión, y un caso por
                                  defecto corregido. `npm run listas`, y dentro de verificar
plantillas/producto.txt           plantilla del archivo de cada producto
plantillas/env.ejemplo            plantilla de credenciales de Icecat
```

`organizar_imagenes.py` reordenaba la salida plana del retoque en una carpeta por
producto. Ya no hace falta: `fotos-estudio-degradado` agrupa sola cuando las
fotos le llegan en subcarpetas —que es como se le pasan— y entrega justamente
esa forma. El script espera `maestras/` y `escritorio/`, que solo aparecen si
se fuerza `--plano`, así que **contra la salida normal no hace nada**. Se deja en el repositorio por si alguna vez se procesa un lote plano a
mano; no lo metas de vuelta en el flujo sin comprobar antes qué forma tiene la
salida.
