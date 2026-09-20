# Estándar de fotos

## Especificación

Lo produce la skill `fotos-estudio-degradado`; aquí queda anotado para saber qué
esperar.

| Qué | Valor |
|---|---|
| Cantidad | hasta 4 por producto, y ninguna de relleno |
| Maestra | 2000 × 2000 px, cuadrada (1:1), sRGB, sin EXIF, JPEG q92 |
| Variantes | 480, 800, 1200, 1600 y 2000 px, en AVIF con JPEG de respaldo |
| Fondo | degradado gris del catálogo, **idéntico en todos los productos** |
| Encuadre | producto centrado ocupando el 85 % del lienzo |
| Nombre | `<id-del-producto>-01` … `-04`, con la extensión del formato |
| Carpetas | `imagenes/<id>/<ancho>/<nombre>.<ext>` |

El cuadrado 1:1 evita que la grilla del catálogo se descuadre y es lo que piden
también Mercado Libre e Instagram Shopping, así que la misma foto sirve en los
tres lados.

El fondo compartido es lo que hace que el catálogo se vea de una sola tienda y
no de cinco proveedores distintos. Por eso no se ajusta foto por foto: cambiarlo
es una decisión para todo el catálogo, y se toma en la otra skill.

## Esta skill escoge las fotos; no las retoca

El reparto de trabajo es deliberado:

| Quién | Qué hace |
|---|---|
| esta skill (`icecat_local.py`, `preparar_fotos.py`) | consigue el material y arma el pedido de lo que falta |
| esta skill (`filtrar_fotos.py`) | decide **qué foto sirve** y descarta el resto |
| `fotos-estudio-degradado` | decide **cómo se ve**, agrupa por producto y separa por ancho |
| esta skill (`construir_entregables.py`) | lee `<producto>/maestra/` y arma el ZIP |

No hay paso de reacomodo en el medio: la otra skill ya entrega la forma que
consume el ZIP.

El estilo del catálogo lo define un solo lugar. Si cada skill recortara y
encuadrara a su manera, el catálogo dejaría de verse parejo, que es justo lo que
un cliente nota sin saber nombrarlo.

### Qué se descarta y por qué

**Lo que no es una foto.** El catálogo de un fabricante mezcla, en la misma
lista de imágenes, pictogramas de característica ("no incluye cargador",
"10–45 W USB PD"), logos y etiquetas de eficiencia energética. Se reconocen por
el **modo del archivo**: una foto llega en color verdadero (RGB o RGBA) y los
pictogramas llegan en escala de grises con alfa (LA) o en paleta (P). En Icecat
además vienen marcados como `FeatureLogo`, así que `icecat_local.py` los
descarta antes de descargarlos; el filtro por píxeles queda para los paquetes
del proveedor, que llegan sin metadatos.

**Las fotos con el producto cortado.** Se mide qué fracción de cada borde toca
el producto. Una foto bien encuadrada da 0,00 en los cuatro lados; una recortada
pasa de 0,20 por el lado donde se sale. Son tomas de detalle o de estilo de
vida: perfectas en la página del fabricante, inservibles en una ficha donde el
cliente quiere ver el equipo completo.

Nada se borra: lo descartado va a `descartadas/<id>/` con un `motivos.json`. Una
foto rechazada por error tiene que poder recuperarse, y el motivo tiene que
poder discutirse.

### Lo que se marca pero no se descarta

La resolución. El estándar de estudio encuadra el producto a 1700 px y muchas
fotos de Icecat no llegan —en una corrida real, 84 de 122; en la del 19/09/2026,
68 de 105—. No se descartan porque en varias marcas no hay nada mejor
disponible, y una foto pequeña es mejor que ninguna.

**Y se retocan igual** (decisión del negocio, 19/09/2026). Que el retoque marque
una foto `REPETIR` por ampliación **no debería sacarla del catálogo**: la marca
es un registro, no una compuerta. Se publica con lo que hay y se pide material
mejor en paralelo; cuando llegue, se reemplaza. Lo que no se hace es dejar el
producto sin foto esperando.

### Lo que `fotos-estudio-degradado` ya hace solo, y lo que no

Medido en la corrida del 19/09/2026, y conviene saberlo antes de inventar
soluciones:

- **El lienzo ya se adapta a la fuente.** No es 2000×2000 siempre: el script
  elige 400, 480, 800, 1200 o 2000 según lo que da el original, y emite solo las
  variantes de ese ancho hacia abajo. Un Honor Choice X7e de 400 px sale en
  `400/` y nada más. **No hay que topar nada a mano**: ya viene topado.
- **La salida ya viene por producto**, con subcarpetas por ancho
  (`<producto>/maestra/`, `1200/`, `800/`, `480/`). Es la misma forma que arma
  `organizar_imagenes.py`, así que **no hay paso de reacomodo**: `construir_entregables.py`
  lee `<producto>/maestra/` directamente.
- **Lo que sí es una compuerta es el `REPETIR` por ampliación**, y sigue
  siéndolo: retira los archivos y `marcar.py --aprobar` no lo levanta —responde
  «está en REPETIR y eso no se aprueba a ojo»—. Lo que se movió es dónde cae:
  `ampliacion_repetir` pasó de 2.0 a **3.0** el 19/09/2026, por decisión del
  negocio y con el porqué escrito en el `SKILL.md` de esa skill.

  Con 2.0 se retenían 11 de 105 fotos y siete productos quedaban con menos de
  cuatro tomas. Con 3.0 entran las cuatro ampliaciones de 2,59× a 2,94× y
  quedan fuera las siete de 3,22× a 4,41×. Esas siete van al pedido de fotos al
  proveedor, que es su sitio: a 4× la foto se ve mal y publicarla no ayuda a
  vender.

El umbral no se toca desde esta skill. Vive en `fotos-estudio-degradado` porque
es la que define el estilo del catálogo desde un solo lugar, y cambiarlo afecta
a **todas** las fotos del sitio, no solo a las de una lista.

### Mejor tres fotos buenas que cuatro con relleno

Si tras filtrar quedan menos de cuatro, el script lo dice y esas fotos entran al
pedido al proveedor. Completar el cupo con un pictograma o con un recorte donde
no se ve el equipo es peor que mostrar menos: la ficha pierde credibilidad justo
donde el cliente está decidiendo.

## Orden de las cuatro fotos

1. Frontal, pantalla o cara principal.
2. Posterior.
3. Ángulo o lateral.
4. Detalle relevante (puertos, corona, estuche) o el producto en uso.

Para relojes y audífonos la cuarta suele rendir más como foto en uso; para
computadores, como detalle de puertos y teclado.

## De dónde salen las fotos

En este orden:

1. **Fotos propias del inventario.** Es la única fuente sin ninguna duda y la que
   además diferencia la tienda: la misma foto de catálogo la tienen todos.
2. **El paquete de imágenes del proveedor o distribuidor.** Casi siempre existe;
   hay que pedirlo. Es el que viene con permiso implícito para revender.
3. **El portal de partners de la marca**, si la tienda está registrada como
   revendedor autorizado y el portal lo autoriza por escrito.

### Catálogos de sindicación de contenido

Existe una cuarta fuente, hecha exactamente para este caso: los catálogos donde
las marcas publican su contenido para que lo usen sus canales de venta. El
gratuito es **Open Icecat**: registro sin costo, imágenes aprobadas por la marca,
fichas técnicas y textos en español, con API para bajarlo por lote.

Antes de contar con él, tres advertencias:

- Solo cubre las marcas que patrocinan el catálogo. Samsung, Xiaomi, Motorola,
  Lenovo, TCL, HP o Asus suelen estar; Infinix, Tecno, Itel, ZTE y las marcas de
  bajo costo tipo Krono, Corn o Fly casi seguro no. Para esas queda el paquete del
  proveedor o foto propia.
- Algunas marcas reservan las imágenes en alta resolución para revendedores
  autorizados, y otras limitan la distribución por país. Que la marca aparezca en
  la lista no garantiza que el modelo esté disponible aquí.
- Su política de uso pide citar "Specs Icecat" con enlace en la ficha del
  producto, publicar un descargo de responsabilidad y **no usar los datos para
  entrenar modelos de IA**. Son obligaciones del sitio, no detalles.
- Las URL de las imágenes de Icecat cambian con el tiempo. Hay que descargarlas y
  servirlas desde el sitio, no enlazarlas en caliente.

**Cómo se registra la tienda** (una sola vez, gratis):

1. Entrar a `https://icecat.us/en/registration` y elegir **Channel Partner
   Registration**, que es el lado del comercio. La otra opción, Brand Partner, es
   para fabricantes. Hay formulario en español en `https://icecat.es`.
2. Llenar correo, usuario, contraseña, organización, país, teléfono, el vertical
   del negocio y **el sitio web**, que es obligatorio y es con lo que verifican
   que hay un comercio real detrás.
3. Confirmar el correo. El acceso al catálogo abierto queda habilitado de una vez.
4. Elegir cómo bajar el contenido: hay API en XML y JSON, exportaciones en CSV y
   conectores para plataformas de tienda. Los manuales están en iceclog.com.
5. Dejar en la plantilla de la ficha la mención a "Specs Icecat" con enlace y el
   descargo de responsabilidad que exige su política de uso.

El usuario de Icecat es el mismo que autentica la API. La contraseña no se pega
en el chat ni queda escrita en los scripts. El script la busca, en este orden:

1. Las variables `ICECAT_USER` e `ICECAT_PASSWORD` de la sesión.
2. Un archivo `.env` en la carpeta actual o hasta cuatro niveles más arriba, que
   es lo que hace que funcione desde cualquier subcarpeta del proyecto.
3. `~/.icecat.env`, para dejarla configurada de una vez en el equipo.

Lo que ya está en el entorno manda sobre el archivo, así una sesión puede usar
otras credenciales sin editar nada. El `.env` va en la raíz del proyecto y en el
`.gitignore`, nunca dentro de `.claude/skills/`, que sí se versiona.

Las alternativas equivalentes de pago son 1WorldSync y CNET Content Solutions.

### Lo que no sirve

**Los bancos de imágenes gratuitos no tienen estos productos.** Unsplash, Pexels o
Pixabay viven de fotos genéricas y de estilo de vida. No hay una foto de un Redmi
Note 15 Pro 5G verde sobre fondo blanco, y publicar "un celular cualquiera" en la
ficha de un modelo concreto es una descripción engañosa del producto.

**Wikimedia Commons** sí tiene fotos de equipos con licencia libre, pero la
cobertura es despareja, casi nunca son fotos de catálogo sobre fondo blanco y la
licencia exige atribuir al autor junto a la imagen. Sirve para un artículo del
blog, no para la ficha de venta.

**Las salas de prensa no sirven para una tienda.** Apple Newsroom, Samsung Mobile
Press y los centros de medios de las marcas publican fotos excelentes, pero sus
condiciones las limitan a uso editorial de prensa y analistas, o a uso personal e
informativo no comercial. Publicar el producto a la venta no cabe en ninguna de
las dos. Tampoco sirven las fotos tomadas de otras tiendas ni los bancos de
imágenes sin licencia comprada.

Registra el origen y la licencia de cada foto junto al producto. Cuando toque
responder por una imagen, esa nota es la diferencia entre cambiarla y tener un
problema.

### Las fotos salen por dos vías, nunca por una sola

Ninguna lista se cubre entera con un catálogo. El flujo asume desde el principio
que hay dos grupos y trata cada uno por su lado:

1. **Lo que está en el catálogo abierto de Icecat** se baja automáticamente.
2. **Todo lo demás se le pide al proveedor**, con un pedido escrito que el script
   genera solo. No se busca en ningún otro lado ni se descarga nada más.

Un producto que Icecat tiene pero en su catálogo de pago **no es un error**: pasa
al grupo del proveedor igual que los demás. El script lo clasifica así y sigue.

Medido en septiembre de 2026 con dos listas reales de 171 productos, el catálogo
abierto cubría las marcas Samsung, Apple, Honor, Motorola, Asus, JBL, Lenovo,
TCL, Nintendo y HP: 67 productos. Xiaomi, Infinix, Tecno, ZTE, Itel, Realme,
Alcatel, Nokia, Sony y las marcas locales como Krono, Corn, Fly o BMAX no
aparecían: 104 productos. Conviene volver a medirlo cada tanto, porque las marcas
entran y salen del catálogo, pero el orden de magnitud sirve para planear: la
mayoría del surtido depende del proveedor, no de Icecat.

### Bajar el contenido de Icecat

`scripts/icecat_local.py` hace el trabajo en tres pasos, todos en el computador
del usuario, con las credenciales en variables de entorno:

```bash
python icecat_local.py diagnostico --icecat-id 12345678       # antes de nada
python icecat_local.py indice   --productos productos.json   # una vez cada tanto
python icecat_local.py buscar   --productos productos.json   # empareja y puntúa
python icecat_local.py traer    --solo-confirmados           # baja fichas y fotos
```

`diagnostico` comprueba credenciales, acceso al repositorio abierto e idiomas
activos, e imprime un bloque para pegar cuando algo falle. Conviene correrlo
primero: la mayoría de los problemas de Icecat son de cuenta, no de producto.

El paso `buscar` es el delicado: la lista del proveedor no trae ni GTIN ni código
de fabricante, así que el emparejamiento es por nombre. El script compara modelo,
capacidad, RAM y red, penaliza fuerte cuando la capacidad no coincide y cuando
uno dice "Pro Max" y el otro solo "Pro". Marca solo como confirmado lo que pasa
de 0,90 y deja el resto para revisión humana en `coincidencias.csv`.

Pedirle los GTIN al proveedor vuelve este paso exacto en lugar de aproximado: con
el código de barras la búsqueda es directa.

Para los productos que el emparejamiento no resuelva, se busca el equipo en
icecat.biz y se pega **la URL completa** de su ficha en la columna `icecat_id` de
`coincidencias.csv`: el script extrae solo el id, el código de fabricante y el
GTIN. `python icecat_local.py id <url>` hace lo mismo desde la terminal.

`traer` deja las fichas en `icecat/fichas/<producto>.json` (descripción, viñetas,
especificaciones, garantía y metadatos SEO) y los enlaces de fotos en
`fotos/urls-icecat.csv`, listos para el descargador. Lo que responda que es de
Full Icecat se cuenta aparte y se reporta al final, sin ensuciar los errores.

### Pedirle el resto al proveedor

```bash
python3 scripts/preparar_fotos.py productos.json --cubiertos fotos/urls-icecat.csv
```

Descuenta lo que Icecat ya resolvió y con el resto escribe
`fotos/pedido-al-proveedor.txt`: los productos agrupados por marca, con sus
colores, y arriba las condiciones (4 tomas, fondo blanco, 1500 px mínimo, una
frontal por color). Está pensado para enviarse tal cual por WhatsApp o correo.

Los productos que la lista no permitió identificar salen con el texto original
del mensaje al lado, para que el proveedor sepa de cuál se está hablando.

Cuando lleguen las fotos, van a `crudas/<id-del-producto>/` y pasan por el mismo
filtro que las de Icecat. Si el proveedor manda enlaces en vez de archivos, se pegan en
`fotos/urls.csv` y los baja el mismo `descargar.py`.

## Cómo se descargan

El entorno donde corre la skill solo alcanza una lista corta de dominios: no puede
bajar imágenes de sitios de marca ni de un Drive. La descarga se prepara aquí y se
ejecuta en el computador del usuario:

```bash
python3 scripts/preparar_fotos.py productos.json --salida fotos/
```

Deja `fotos/urls.csv` con una fila por producto y encuadre, `fotos/descargar.py`
(sin dependencias, corre en Windows con el Python que ya esté instalado) y un
`LEEME.txt`. El usuario pega los enlaces, ejecuta `python descargar.py`, y las
imágenes caen en `crudas/<id>/` junto con un `origen.json` que guarda de dónde
salió cada una.

De vuelta en la skill, `scripts/filtrar_fotos.py` descarta lo que no sirve y el
retoque lo hace `fotos-estudio-degradado`.

Si un producto queda sin fotos, su carpeta en el ZIP incluye un
`FOTOS-PENDIENTES.md` que dice cuántas faltan.

El retoque —fondo degradado, encuadre al 85 %, resplandor, maestra y variantes—
lo hace la skill `fotos-estudio-degradado`, que es la que define el estilo de
todas las imágenes del ecommerce.
