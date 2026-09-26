---
name: fotos-estudio-degradado
description: Convierte lotes de fotos de producto en tomas de estudio uniformes con el estilo de TecnoSport — aísla el producto sin retocar su interior —ni color, ni forma, ni brillo, ni logos—, lo escala al 85 % de un lienzo cuadrado de hasta 2000 px, menor cuando la foto no da para más, y lo centra sobre un fondo blanco idéntico en todo el catálogo, con una sombra de contacto sutil que le dibuja el resplandor alrededor, y exporta la maestra JPEG y el AVIF web, con un reporte honesto que marca cada foto como LISTA, REVISAR o REPETIR. Úsala siempre que alguien pida fotos «de estudio», «con fondo blanco», «con sombra», «con resplandor», «como las de TecnoSport» o que combinen con el catálogo, aunque no diga «editar»; también si piden el fondo gris degradado que el catálogo usó antes, que sigue disponible con un ajuste. No la uses para piezas publicitarias (diseno-publicitario) ni para fotogramas del visor 360. Covers studio product photos with pure white backgrounds and contact shadow, batch background removal, AVIF export and image QA.
---

# Fotos de estudio (estilo TecnoSport)

Actúa como el retocador del estudio de TecnoSport. Llegan fotos tomadas con el
celular —sobre la cama, una mesa o el piso, a veces reenviadas por WhatsApp— y el
catálogo necesita que todas se vean iguales: mismo fondo blanco, mismo
tamaño de producto, misma sombra. Tu valor no es sólo procesar: es **decir con
honestidad qué foto sirve, cuál hay que mirar de cerca y cuál hay que repetir**.

**El fondo es blanco plano desde el 23/09/2026** (antes era un degradado radial
#FFFFFF → #A5A5A5, y el nombre de la carpeta se quedó de esa época). Lo que
rodea al producto no lo pone el fondo: lo pone la **sombra de contacto**, que es
la que dibuja el resplandor pegado a la silueta. Por eso la sombra dejó de ser un
adorno y pasó a ser lo único que separa un producto claro de su fondo: ver
«El fondo blanco y el resplandor».

**Regla que no se negocia: se cambia el entorno, nunca el producto.** Fondo,
encuadre y sombra son del catálogo; color, forma, logos y textos son del
producto y no se tocan. La corrección tonal se limita a exposición y contraste de
la luminancia dentro del producto; no hay balance de blancos, tono, saturación ni
relleno generativo. El Estatuto del Consumidor (Ley 1480 de 2011) exige que lo que
se muestra corresponda a lo que se entrega: un color «mejorado» es una devolución
en potencia. Por la misma razón no se borran marcas de agua ni stickers que estén
encima del producto (ver «Límites»).

**Rutas y entorno.** `${CLAUDE_SKILL_DIR}` es la carpeta de esta skill: Claude
Code la sustituye sola. Si aparece literal (claude.ai), cámbiala por la carpeta
donde está este archivo, p. ej. `/mnt/skills/user/fotos-estudio-degradado`. Déjala
siempre entre comillas, porque en Windows la ruta puede llevar espacios y barras
invertidas. `SALIDA` es la carpeta de resultados. Usa `python3` en Linux y macOS y
`python` en Windows. Para ver imágenes usa `view` en claude.ai y `Read` en Claude Code.

## Cuándo usarla y cuándo no

| Pedido | Skill |
|---|---|
| Fotos «de estudio», «fondo blanco», «con sombra», «con resplandor», «como las de la tienda» | esta |
| El fondo gris degradado de antes | esta, con `--ajuste 'fondo_esquinas=#A5A5A5' --ajuste dither_niveles=1` |
| Mercado Libre, Amazon, Instagram Shopping | `fotos-de-producto`, que aplica los requisitos de cada canal (márgenes, sombra permitida o no, tamaños); el fondo blanco ya es el mismo, la diferencia es el encuadre y lo que cada uno exige |
| Volantes, banners, historias o anuncios hechos con las fotos | `diseno-publicitario` |
| Fotogramas del visor 360 | ninguna: aquí cada foto se recorta y se escala por su cuenta, así que en una secuencia el producto cambiaría de tamaño y el borde temblaría entre fotogramas |

## Qué se entrega

```
SALIDA/
├── maestras/<nombre>.jpg            2000×2000 en salida plana · JPEG q92 progresivo 4:4:4 · sRGB · sin EXIF/XMP/IPTC · ≤ 800 KB
├── escritorio/<nombre>-2000.avif    AVIF 10 bits · calidad 60, que sube si aparecen escalones · ≤ 300 KB
├── revision-1.jpg, revision-2.jpg…  antes/después, 8 fotos por hoja, con estado, motivos y datos
├── reporte.json                     parámetros, mediciones, estado y motivos de cada foto
└── .trabajo/                        máscaras, miniaturas, vistas previas y registro (no se entrega)
SALIDA.zip                           maestras + escritorio + hojas + reporte.json
```

Con `--variantes`, `escritorio/` lleva además `<nombre>-<ancho>.avif` y
`<nombre>-<ancho>.jpg` para 480, 800, 1200, 1600 y 2000 px.

**Por producto** (`--por-producto`, y sola cuando las fotos vienen en subcarpetas,
como `crudas/<producto>/`) la salida se agrupa y la carpeta dice el ancho, así que
el nombre ya no lo repite:

```
SALIDA/
└── <producto>/
    ├── maestra/<nombre>.jpg      la maestra, al lienzo del producto
    ├── 2000/<nombre>.avif        un subdirectorio por ancho
    └── 1200/<nombre>.avif
```

`--plano` fuerza la salida de siempre aunque las fotos vengan en subcarpetas. Las fotos REPETIR no
escriben nada en `maestras/` ni en `escritorio/`: su vista previa queda en
`.trabajo/vistas/` para poder mostrarla y explicar el problema.

## El fondo blanco y el resplandor

Decisión del negocio del 23/09/2026. El catálogo se publicaba sobre un degradado
radial #FFFFFF → #A5A5A5; desde esa fecha el fondo es **blanco plano**, y las dos
razones son de edición, no de estética:

- **Para no alterar el interior del producto.** El recorte devuelve alfa parcial
  dentro del producto cuando su superficie se parece al fondo, y al componer, el
  gris del estudio se veía a través de esas zonas: manchas grises y oliva que no
  estaban en la foto original (ver «El interior se compone opaco», que existe por
  eso). Sobre blanco, lo que se filtra por un alfa parcial es blanco, así que la
  diferencia con el original es mucho menor y más fácil de juzgar a ojo.
- **Para que un producto blanco no pelee con el fondo.** Sobre el degradado, el
  borde de un producto claro caía unas veces en la zona blanca del centro y otras
  en la gris: el mismo producto con dos contornos distintos según dónde quedara.

**El resplandor alrededor del producto se conserva, y ahora lo dibuja entero la
sombra de contacto** (#000000 al 63 %, σ 58 px, 14 px hacia abajo). Sobre blanco
esa sombra deja de ser un adorno: es lo único que separa al producto del fondo.
De ahí dos consecuencias que conviene no deshacer:

- **No le bajes la opacidad ni el sigma** «porque se ve fuerte» en una foto: es el
  contorno de todo el catálogo.
- **Los productos claros dan más REVISAR por separación**, y es correcto. Medido
  al cambiar el fondo, el Honor X8B plateado pasó de fallar el 30,6 % del contorno
  a fallar el 52,8 %, mientras que el Lenovo Tab mejoró de 36,1 % a 16,7 %. El
  umbral no se movió: lo que marca es justo la foto donde el contorno depende solo
  de la sombra, que es la que hay que mirar al 100 %.

El fondo plano también apagó el tramado (`dither_niveles` = 0): sobre un color
liso no hay degradado que suavizar, y medido en el JBL Go 5 los escalones salen
iguales o mejores sin él (AVIF 480 px 0,56 contra 0,57; 1200 px 0,36 contra 0,42),
con el blanco en 255 exacto en vez de salpicado de 254.

Para volver al degradado de antes, en una corrida o en el `config.json`:
`--ajuste 'fondo_esquinas=#A5A5A5' --ajuste dither_niveles=1`, y regenerar la
plantilla con `fondo.py --generar`. **El catálogo procesado antes del 23/09/2026
sigue en gris**: mientras no se reprocese, conviven los dos fondos.

## El producto sale tal cual la foto original

Decisión del negocio del 19/09/2026, y es la regla que manda sobre los parámetros
de esta skill: **dentro de la silueta se publican los píxeles del original**. Lo
que esta skill aporta es el fondo, la sombra de contacto, el resplandor alrededor
y el encuadre; el interior no se retoca.

De ahí salen tres ajustes que conviene no deshacer sin leer esto.

### 1. El interior se compone opaco (`alfa_solida_banda_px`)

Es el que motivó la regla. El modelo de recorte devuelve **alfa parcial dentro
del producto** cuando su superficie se parece al fondo: en una foto del Galaxy
A56, el **35,6 % de los píxeles interiores** tenían alfa < 1, con mínimos de
0,533. Al componer, el gris del estudio se veía a través de esas zonas y la
pantalla salía con manchas grises y oliva que **no están en la foto original**.
Apareció en 21 fotos de un lote de 105, sobre todo en celulares con el fondo de
pantalla claro. Con el fondo blanco lo que se filtra es blanco y se nota mucho
menos, pero el arreglo se queda: un alfa parcial sigue aclarando el interior, y
la regla es que dentro de la silueta van los píxeles del original.

`solidificar_interior()` lo corrige en dos pasos:

- **Cierra los agujeros pequeños** del recorte —menos de
  `alfa_agujero_max_frac` del área del producto, hoy 0,5 %—, que son manchas del
  modelo en mitad de una superficie. Los grandes no: **un asa calada o el hueco
  de un aro siguen abiertos**, y así está probado.
- **Sube el alfa a 1 según la distancia al borde**, con una rampa de
  `alfa_solida_banda_px` (3 px). En el borde manda el alfa original, para que el
  antialias y el resplandor no cambien; a 3 píxeles hacia adentro el alfa es 1.
  Nunca baja el alfa.

La distancia se mide con `distanceTransform`, no con una erosión, y esa
diferencia es la que preserva los agujeros: la transformada mide también la
distancia al hueco, así que sus bordes no se rellenan.

Cuando sube más de `alfa_interior_aviso` (25 %) del interior, el reporte lo
avisa. **Ese aviso importa en un producto transparente o espejado de verdad**
—un vaso, una vitrina, una malla—: ahí la opacidad sí cambia lo que se ve y hay
que mirarlo a tamaño real. Para un catálogo de celulares, tablets y parlantes es
lo correcto.

### 1.b El corte de la máscara está en 0,2, no en 0,5 (`alfa_umbral_binario`)

Segundo hallazgo del mismo día, y va de la mano del anterior. **Una superficie de
malla o tejido sale del modelo con alfa entre 0,2 y 0,5.** Con el corte en 0,5,
`limpiar_islas` la parte en fragmentos, los descarta por pequeños, y el producto
se publica sin cuerpo: el JBL Flip 7 salía como el logo, la tapa y unos jirones
de rejilla, con el 80 % del parlante ausente.

Medido bajando el corte desde 0,5, la máscara cruda crece así:

| Corte | JBL Flip 7 (malla) | Galaxy A56 (limpio) |
|---|--:|--:|
| 0,4 | +36,9 % | +0,4 % |
| 0,3 | +69,8 % | +0,7 % |
| **0,2** | **+81,4 %** | **+1,2 %** |
| 0,1 | +84,7 % | +1,6 % |

La cobertura **se estanca por debajo de 0,2**: ahí acaba la malla y empieza el
fondo. Por eso el corte es 0,2 y no menos.

Bajarlo trae dos efectos que hay que compensar, y los dos parámetros que siguen
existen por eso:

- **`alfa_solida_banda_px` = 14, no 3.** Al entrar el halo del fondo como
  producto, una rampa corta lo vuelve opaco y aparece un fleco claro en el
  contorno. Con 14 px el halo se queda dentro de la banda donde manda el alfa
  original, así que sigue translucido y el antialias se conserva.
- **`alfa_cierre_px` = 31.** El modelo parte un producto en dos piezas cuando un
  reflejo o una costura le bajan el alfa en una línea estrecha; la rampa trataba
  esa grieta como borde y dejaba un fleco blanco **en mitad** del producto. Un
  cierre morfológico une los dos lados antes de medir la distancia.

Comprobado que el cierre **no puentea piezas separadas**: los Galaxy Buds Core
salen con los dos audífonos bien sueltos y en LISTA. Si alguna vez dos piezas
quedan a menos de 31 px, el motivo `varias_piezas` lo avisa.

### 2. `tono.activo` está en `false`

La corrección tonal automática subía hasta 0,3 EV y añadía microcontraste 0,12.
Solo tocaba L*, nunca a* ni b*, así que el color no cambiaba —pero el brillo y el
contraste local sí, y eso es una variación respecto al original.

### 3. `enfoque.cantidad` está en `0`

La máscara de enfoque añadía nitidez dentro del producto. Misma razón.

Las dos siguen implementadas y se reactivan con un valor en el config o con
`--ajuste tono.activo=true`. Si alguna vez el catálogo se surte de fotografía
propia bien expuesta, volver a encenderlas tiene sentido; con material de
catálogo ajeno, no: lo que hace falta es fidelidad, no interpretación.

## Quién consume esta salida: `listas-de-proveedor`

El catálogo se surte sobre todo de esa skill. **Desde el 25/09/2026 el traspaso
es a mano**: esa skill dejó de buscar, descargar y retocar fotos —su regla 16—
y entrega solo las fichas y el comparativo. Cuando llega un lote de fotos, del
proveedor o propias, se procesa aquí y allá se vuelve a armar el ZIP con
`--imagenes`. La interfaz entre las dos no cambió, y conviene conocerla porque
ya se rompió una vez sin que nadie lo notara.

**Lo que le llega a esta skill.** Una carpeta `crudas/<producto>/<producto>-NN.jpg`.
Si el lote pasó por `filtrar_fotos.py` —que sigue en esa skill, fuera de su
flujo— ya viene sin pictogramas, sin logos y sin las tomas donde el producto
sale cortado; si no pasó, ese descarte hay que hacerlo a ojo antes de procesar.
Como vienen en subcarpetas, **el modo por producto se activa solo** y no hace
falta pasar `--por-producto`.

**Lo que se lleva de vuelta.** La forma agrupada, tal cual:

```
<producto>/maestra/<producto>-01.jpg     lo que va al ZIP del catálogo
<producto>/<ancho>/<producto>-01.avif    lo que va al sitio
```

`construir_entregables.py` lee `<producto>/maestra/` directamente. **Si esta
skill cambia esa forma, ese script deja de encontrar las fotos y el ZIP sale
vacío sin fallar.** Hubo un `organizar_imagenes.py` que reacomodaba la salida
plana; quedó fuera del flujo el 19/09/2026 justamente porque esta skill ya
entrega la forma buena, y estuvo un tiempo sin hacer nada sin que se notara.

**Lo que esa skill decidió sobre el material pobre.** Su regla 16 decía, hasta
el 24/09/2026, que una foto por debajo del estándar se publica igual, al máximo
que dé la fuente, porque un producto sin foto no vende. De ahí salió el valor
de `ampliacion_repetir`, que está en 3.0 y no en 2.0; el porqué está unas
secciones más abajo. Esa regla hoy dice otra cosa —que las fotos no son asunto
de esa skill—, pero el umbral se queda donde está: lo sostiene la calidad del
material de catálogo, no quién lo baje. Dos cosas que conviene no conceder
igual:

- No hay que topar anchos a mano: esta skill ya elige el lienzo según la fuente
  y solo emite las variantes de ese ancho hacia abajo.
- El `REPETIR` sigue reteniendo los archivos, y `marcar.py --aprobar` sigue
  negándose a levantarlo. Es la compuerta que evita que una foto ampliada 4×
  entre al catálogo. Si alguien pide subirla, se mueve el umbral con su razón
  escrita, no se rodea la compuerta.

## Especificación

| Aspecto | Valor por defecto (`config.json`) |
|---|---|
| Lienzo | 2000×2000; con `--por-producto`, el mayor escalón de `lienzos_escala` que el material del producto alcance |
| Encuadre | lado mayor de la caja del producto (alfa ≥ 10 %) al 85 % del lienzo: 1700 ± 2 px, centrado ± 2 px |
| Fondo | plantilla `assets/fondo-2000x2000.png`: blanco #FFFFFF plano, sin tramado, idéntico en todo el catálogo. Con `fondo_esquinas` distinto de `fondo_centro` vuelve a ser un degradado radial (el del catálogo anterior: #FFFFFF → #A5A5A5, blanco hasta el 26 % del radio, rampa lineal hasta la esquina y tramado ±1 de semilla fija) |
| Sombra | #000000 al 63 %, dilatación 6 px, desenfoque σ 58 px, desplazada 14 px hacia abajo, modo normal, siempre debajo del producto. Sobre el fondo blanco es también el resplandor: lo único que separa al producto del fondo |
| Recorte | rembg `isnet-general-use` en dos pasadas; islas < 0,5 % descartadas con aviso; borde descontaminado y contraído 1 px |
| Adornos | se quitan solos los elementos ajenos separados del cuerpo, pequeños (< 15 %) y de un color que no aparece en él (≥ 20 en a*b*): los destellos de «Galaxy AI» y adornos de render parecidos. Las piezas legítimas comparten el color del cuerpo y se conservan |
| Tono | sólo L*: niveles con recorte ≤ 0,5 %, ganancia ≤ 0,3 EV, microcontraste leve; a* y b* intactos (Δcroma ≤ 2) |
| Enfoque | después de escalar, sobre L*: radio 1 px, 70 %, umbral 2 |
| Escala | ≥ 1,5× → REVISAR · ≥ 3× → REPETIR (el producto debe medir ≥ 1700 px en la foto) |
| Máscara | corte en 0,2 para que la malla y el tejido entren enteros |
| Interior | se compone opaco: alfa 1 a partir de 14 px del borde, cerrando grietas de 31 px y agujeros de menos del 0,5 % del área |
| Tono y enfoque | apagados: el interior del producto no se retoca |
| Lienzo por producto | escalones 2000 · 1600 · 1200 · 1000 · 800 · 600 · 480 · 400 · 320; se toma el mayor que la mejor foto alcance sin pasar de 1,25× de ampliación, y si no llega a ninguno, el menor |
| Web | AVIF a 10 bits con `avifenc` (a 8 bits con Pillow si no lo hay); con `--variantes`, JPEG q88 de respaldo |

Los valores en píxeles están pensados para 2000 px y se escalan si cambia el lienzo.
Para una corrida puntual: `--ajuste clave=valor` (p. ej. `--ajuste tono.activo=false`;
claves anidadas con punto). Cambiar fondo, ocupación o sombra rompe la
uniformidad del catálogo: hazlo sólo si el usuario lo decide para todo el catálogo,
y entonces regenera la plantilla con `fondo.py --generar`.

## Flujo

### 1. Mirar antes de procesar

Mira dos o tres fotos del lote (todas, si son pocas) y busca:

- **Nombres**: si son de cámara (`IMG_4521.HEIC`), pide las referencias en un solo
  mensaje o propón nombres descriptivos y pásalos con `--mapa` (CSV `archivo,nombre`).
- **Problemas evidentes**: producto cortado, foto diminuta o borrosa, varios
  productos distintos, marca de agua o sticker encima del producto. Dilo antes de
  procesar, sin frenar el resto del lote.
- **Producto parecido a su fondo** (blanco sobre blanco, negro sobre negro): se
  procesa igual, pero anticipa que puede salir REVISAR.

No hagas un interrogatorio: si nada es ambiguo, procesa con los valores por defecto
y di qué asumiste.

### 2. Preparar el entorno (una vez por sesión)

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/entorno.py"         # qué falta y el comando exacto para instalarlo
python3 "${CLAUDE_SKILL_DIR}/scripts/avif10.py" estado   # ¿hay avifenc para AVIF a 10 bits?
python3 "${CLAUDE_SKILL_DIR}/scripts/avif10.py" instalar # si no: binarios oficiales de libavif, con SHA-256
```

Si `entorno.py` marca faltantes, ejecuta el comando que imprime: instala
`requirements.txt` con `python -m pip` del mismo Python que correrá los scripts (en
Claude Code, el del entorno virtual con que se abrió `claude`) y, en el contenedor
de claude.ai, añade `--break-system-packages`. No uses `pip install rembg` a secas:
`rembg` ya no trae OpenCV. `avif10.py` sólo usa la biblioteca estándar, así que
puede instalarse antes que lo demás.

La primera corrida descarga el modelo de recorte (unos 180 MB, desde GitHub). Sin
`avifenc` todo funciona, pero el AVIF sale a 8 bits y necesita más calidad para no
mostrar escalones. En Windows, `avifenc.exe` necesita el Visual C++ Redistributable;
si `estado` no reporta 10 bits tras instalar, falta ese paquete.

### 3. Procesar

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar.py" ENTRADA -o SALIDA --zip
```

`ENTRADA` son fotos o carpetas. En claude.ai, las subidas están en
`/mnt/user-data/uploads` y la salida va en `/home/claude/`. En Claude Code, usa la
carpeta que indique el usuario y una salida fuera del repositorio, porque las fotos
pesan y no deben versionarse.

Tarda entre 5 y 20 s por foto con un núcleo (lote de prueba de 15 fotos: 129 s; con
`--variantes`, 214 s); en un entorno recién instalado, la primera foto tarda cerca de
un minuto porque `pymatting` compila su código. Con más de 10 fotos, lánzalo en segundo plano y consulta el
avance para no chocar con el límite de tiempo de la terminal:

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar.py" ENTRADA -o SALIDA --zip --segundo-plano
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar.py" --avance SALIDA      # repetir hasta ver «Terminado»
```

Otras opciones: `--mapa nombres.csv`, `--variantes`, `--ajuste clave=valor`,
`--config otro.json`, `--solo nombre1,nombre2`, `--por-producto`, `--plano`,
`--nuevas` (todas en `--help`).

Para un árbol que se vuelve a llenar —`catalogo/fotos/crudas/`, que crece con cada
lote de inventario— el par útil es `--por-producto --nuevas`: agrupa la salida por
producto y salta lo ya procesado cuyo archivo de origen no ha cambiado. En Windows las
rutas van entre comillas (`"C:\fotos\lote 1"`) y el segundo plano funciona igual.

Una carpeta de salida acumula rondas: al reprocesar, el reporte se fusiona y las
hojas se regeneran. Por eso las opciones de salida (lienzo, anchos, formatos y
carpetas) deben ser las mismas en toda la carpeta; si cambian habiendo otras fotos
procesadas, el script se detiene y pide reprocesar todo o usar otra carpeta.

### 4. Revisar como un cliente exigente

Mira cada `revision-*.jpg` (primero van las REPETIR, luego REVISAR y LISTA). Para
cada REVISAR, y para cualquier LISTA que se vea rara en la miniatura, amplía y mira
el mosaico que se genera:

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/ampliar.py" SALIDA nombre                 # hasta 4 zonas al 100 %
python3 "${CLAUDE_SKILL_DIR}/scripts/ampliar.py" SALIDA nombre --zona X,Y,W,H  # zona del resultado, en px
python3 "${CLAUDE_SKILL_DIR}/scripts/ampliar.py" SALIDA nombre --original      # foto original con cuadrícula
```

Las zonas automáticas son, por prioridad: bordes inciertos, recorte dudoso (el
producto se parecía a su fondo original), poca separación con el fondo nuevo, base
y centro del producto. Busca:

- **Restos del fondo original** en el contorno, entre las piezas de un par o dentro
  de asas y correas.
- **Partes perdidas**: cordones, correas, asas, antenas, suelas claras sobre mesas claras.
- **Elementos ajenos que el recorte conservó**: manos, ganchos, maniquíes, soportes,
  cables, etiquetas colgantes. No se borran a mano (ver el paso 5). Los adornos de
  color separados del producto —los destellos de «Galaxy AI»— ya se quitaron solos
  y el reporte lo anota: confirma que no eran parte del producto.
- **Silueta legible**: un producto blanco o plateado sólo se distingue del fondo por
  la sombra, y por arriba la sombra es más débil que por abajo. Mira el contorno
  superior a tamaño real antes de aprobarlo.
- **Nitidez y compresión**, sobre todo en fotos ampliadas o reenviadas por WhatsApp.

Lo que el script no detecta se marca a ojo, con un motivo claro que quedará en el
reporte y en las hojas:

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/marcar.py" SALIDA nombre --revisar "se ve la mano que sostiene el tenis"
python3 "${CLAUDE_SKILL_DIR}/scripts/marcar.py" SALIDA nombre --repetir "marca de agua de otra tienda sobre el producto"
python3 "${CLAUDE_SKILL_DIR}/scripts/marcar.py" SALIDA nombre --aprobar "revisado al 100 %: el borde está completo"
python3 "${CLAUDE_SKILL_DIR}/scripts/marcar.py" SALIDA --lista
```

`--aprobar` sólo levanta los motivos REVISAR automáticos que ya miraste a tamaño
### Por qué el corte de ampliación está en 3× y no en 2×

`ampliacion_repetir` estuvo en 2.0 hasta el 19/09/2026. Se subió a 3.0 por una
decisión del negocio, y conviene saber de dónde salió para no volverla a bajar
sin pensarlo.

El catálogo se surte de listas de proveedor, y buena parte de ese material viene
de Open Icecat en resoluciones bajas: en una corrida de 105 fotos, 68 no llegaban
a los 1700 px que pide el encuadre. Con el corte en 2×, once fotos se retenían y
siete productos quedaban con menos de cuatro tomas —algunos con una sola—, o sin
ninguna. Un producto sin foto no se vende; uno con una foto regular, sí.

El corte en 3× deja pasar lo que se ve aceptable a tamaño de tarjeta y sigue
reteniendo lo que se ve mal de verdad: de esas once pasaron cuatro (ampliaciones
de 2,59× a 2,94×) y quedaron fuera siete (de 3,22× a 4,41×). Las retenidas van
al pedido de fotos al proveedor.

Lo que **no** cambió: `REPETIR` sigue retirando los archivos y `marcar.py
--aprobar` sigue negándose a levantarlo. La compuerta existe; solo se movió
dónde cae. Si alguna vez el material del catálogo mejora —fotos propias o un
paquete decente del proveedor—, esto vuelve a 2.0.

real; nunca sirve para saltarse un REPETIR. Una marca REPETIR retira los archivos
de las carpetas de entrega y `--limpiar` los restaura.

### 5. Corregir lo corregible

- **Algo ajeno separado del producto** (etiqueta colgante, gancho, soporte): ubícalo
  con `ampliar.py --original`, exclúyelo con coordenadas de la foto original y
  reprocesa sólo esa foto:

  ```bash
  python3 "${CLAUDE_SKILL_DIR}/scripts/marcar.py" SALIDA nombre --excluir 1200,300,1500,650
  python3 "${CLAUDE_SKILL_DIR}/scripts/procesar.py" -o SALIDA --solo nombre
  ```

  Si lo ajeno está **encima** del producto (una mano que lo tapa, un sticker, una
  marca de agua), excluirlo abriría un hueco en el producto: es REPETIR.
- **Un parámetro que no conviene a una foto** (p. ej. la corrección tonal):
  reprocésala con `--solo` y `--ajuste`, y di qué cambiaste. El encuadre, el fondo
  y la sombra no se cambian foto por foto.
- **Lo que no se arregla con parámetros** —ampliación ≥ 2×, producto cortado,
  recorte fallido por falta de contraste, foto borrosa, marca de agua encima— se
  reporta como REPETIR con la indicación concreta de cómo repetir la toma. Lee
  `references/captura.md` antes de dar esas indicaciones.

### 6. Verificar y entregar

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/verificar.py" SALIDA
```

Relee cada archivo contra los criterios de aceptación: nombres, tamaños, perfil y
metadatos, esquinas del fondo, caja y centro medidos sobre la máscara guardada,
Δcroma, pesos y estados coherentes. Si algo falla, corrígelo; nunca entregues con
fallos sin decirlos. El detalle de cada criterio, los campos del reporte y las
mediciones que respaldan los valores están en `references/criterios.md`.

Si marcaste fotos después de procesar, regenera el ZIP con
`marcar.py SALIDA --lista --zip`. En claude.ai, copia `SALIDA.zip` a
`/mnt/user-data/outputs/` y preséntalo con `present_files` junto con la primera hoja
de revisión. En Claude Code no hace falta copiar nada: da las rutas del ZIP y de la
primera hoja. En el mensaje, en pocas líneas: cuántas quedaron listas, cuáles hay
que revisar y por qué, y cuáles hay que repetir y cómo. No copies el reporte.

## Cómo se decide el estado

| Estado | Motivos automáticos |
|---|---|
| REPETIR | archivo dañado o no soportado · producto cortado por el encuadre · ampliación ≥ 2× · recorte vacío o que desaparece al escalar |
| REVISAR | ampliación ≥ 1,5× · bordes inciertos en más del 8 % del producto · producto parecido a su fondo original en más del 20 % del contorno · producto confundido con el fondo nuevo en más del 25 % del contorno · fragmento descartado apreciable · 3 piezas o más · PNG recortado que llega al borde |
| LISTA | ninguno de los anteriores |

Manda el motivo más grave y las marcas a ojo se suman a las automáticas. Las
notas (dominante de color, luces quemadas, JPEG recomprimido, par de piezas,
bordes poco separados en pocos tramos) son informativas y no cambian el estado.

## Límites que debes decir sin rodeos

- **Resolución**: si el producto mide menos de 1700 px en la foto, se amplía y
  ningún ajuste inventa detalle. WhatsApp recomprime las fotos: pide que las envíen
  como documento.
- **Blanco sobre blanco y negro sobre negro**: el recorte puede comerse parte del
  producto o sumar parte de la superficie. El script lo detecta midiendo el
  contraste con el fondo original, pero la solución es repetir la foto sobre un
  fondo que contraste.
- **Superficies transparentes o espejadas, pantallas encendidas y tejidos calados**
  (mallas, encajes, tiras): bordes dudosos o huecos rellenos. Revísalos al 100 %.
- **Filete oscuro**: donde la foto tenía sombra junto al producto puede quedar un
  borde oscuro de pocos píxeles, visible sólo al 100 %. Se evita con luz más
  difusa; recortar más se comería bordes reales del producto.
- **Producto blanco o plateado sobre el fondo blanco**: la silueta la sostiene sólo
  la sombra, y por el borde de arriba puede quedar muy suave. El reporte lo marca
  midiendo la separación con el fondo, y suele salir REVISAR. Ni el fondo se
  oscurece ni la sombra se refuerza foto por foto: rompería la uniformidad del
  catálogo. Si el producto no se lee, la salida es otra toma, con el producto
  girado o con más luz de recorte.
- **Color**: no se corrige a propósito. Si la foto tenía una dominante (luz
  amarilla), el producto la conserva; dilo y sugiere repetir con luz de día.
- **Varios productos distintos en una foto** se procesan como uno solo: márcala REPETIR.
- **Marcas de agua y stickers**: sobre el fondo desaparecen con el recorte y una
  etiqueta colgante separada se quita con `--excluir`, pero encima del producto no
  se borran (sería alterar el producto y suele delatar una foto ajena): REPETIR, con
  la foto oficial del fabricante (Open Icecat) o una propia como alternativa.
- **Adornos y texto de los renders de fabricante**: los destellos de «Galaxy AI» se
  quitan solos porque están separados del producto, pero el **texto incrustado en la
  pantalla** —«Galaxy S25 Ultra» sobre el propio equipo— no se toca: está encima del
  producto y borrarlo sería alterarlo. Si ese texto no se puede publicar, la salida
  es otra foto, no un retoque. El criterio distingue por color, así que un producto
  con una pieza pequeña de un color que no aparece en su cuerpo podría perderla:
  el reporte anota siempre qué se quitó, y `--ajuste adornos_quitar=false` lo apaga.
- **WebP**: con pérdida dejaba anillos en el degradado de antes (1,1–1,25 niveles
  medidos, aun a calidad 100); por eso el respaldo de AVIF es JPEG. Sobre el fondo
  blanco hay mucho menos degradado que arruinar —queda el de la sombra—, pero no
  se ha vuelto a medir. Si un sistema exige WebP,
  `--ajuste 'formatos_web=["avif","webp"]'` lo genera y el reporte lo advierte.
- **Modelo de recorte**: `isnet-general-use` por defecto, porque BiRefNet se queda
  sin memoria en un contenedor de 3 GB. En un equipo con RAM holgada puede probarse
  `--ajuste modelo_recorte=birefnet-general-lite`.

## Scripts

| Script | Para qué |
|---|---|
| `procesar.py` | Lote completo, reprocesado por nombre, segundo plano y avance |
| `ampliar.py` | Zonas al 100 % y foto original con cuadrícula |
| `marcar.py` | Marcas a ojo, aprobaciones, exclusiones y ZIP |
| `verificar.py` | Criterios de aceptación releyendo los archivos |
| `hojas.py` | Regenera las hojas de revisión desde el reporte |
| `fondo.py` | Genera o verifica la plantilla del fondo |
| `entorno.py` | Diagnóstico del entorno y comando exacto de instalación |
| `avif10.py` | Estado e instalación de `avifenc` (AVIF a 10 bits) |
| `imagen.py`, `reporte.py` | Módulos internos: píxeles y reporte |

## Integración con la web

Los archivos web siguen el patrón `<nombre>-<ancho>.<formato>`, así que un cargador
de imágenes (por ejemplo, un loader de `NgOptimizedImage` en Angular) traduce el
ancho que pide el navegador directamente a la URL del archivo. Con `--variantes`,
sirve AVIF con JPEG de respaldo:

```html
<picture>
  <source type="image/avif" srcset="tenis-blanco-480.avif 480w, tenis-blanco-800.avif 800w, tenis-blanco-2000.avif 2000w">
  <img src="tenis-blanco-800.jpg" srcset="tenis-blanco-480.jpg 480w, tenis-blanco-800.jpg 800w, tenis-blanco-2000.jpg 2000w" alt="Tenis blanco">
</picture>
```

Si el almacenamiento de la tienda genera los tamaños al subir, basta con las
maestras. Con el fondo blanco la advertencia de antes pesa mucho menos: el único
degradado que queda es el de la sombra, y medido en el JBL Go 5 los escalones de
las variantes se quedan entre 0,24 y 0,56 —por debajo del umbral de 0,6— aun sin
tramar. `procesar.py` sigue tramando cada variante (`cuantizar_tramado`), pero con
`dither_niveles` en 0 eso no hace nada; si alguna vez vuelve el degradado, el
tramado vuelve con él y entonces sí: un redimensionador genérico lo promedia y los
escalones reaparecen, así que el proceso de la nube tendría que tramar igual o
codificar AVIF a 10 bits.
