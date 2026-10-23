---
name: fotos-estudio-degradado
description: Convierte lotes de fotos de producto en tomas de estudio uniformes con el estilo de TecnoSport — aísla el producto sin retocar su interior —ni color, ni forma, ni brillo, ni logos—, lo escala al 85 % de un lienzo cuadrado de hasta 2000 px, menor cuando la foto no da para más, y lo centra sobre un fondo blanco idéntico en todo el catálogo, con una sombra de contacto sutil que le dibuja el resplandor alrededor, y exporta la maestra JPEG y el AVIF web, con un reporte honesto que marca cada foto como LISTA, REVISAR o REPETIR. Es también el bloque 2 de la ingesta de tecnología: con la tanda de por-proponer.json, ningún modelo pasa sin 4 fotos LISTA y la tanda espera fotos nuevas hasta que todos pasen; úsala cuando alguien diga «procesa las fotos de la tanda», «ya dejé las fotos», «sigue con las fotos de los productos nuevos» o termine /listas-de-proveedor con modelos esperando fotos. Úsala siempre que alguien pida fotos «de estudio», «con fondo blanco», «con sombra», «con resplandor», «como las de TecnoSport» o que combinen con el catálogo, aunque no diga «editar»; también si piden el fondo gris degradado que el catálogo usó antes, que sigue disponible con un ajuste. No la uses para piezas publicitarias (diseno-publicitario) ni para fotogramas del visor 360. Covers studio product photos with pure white backgrounds and contact shadow, batch background removal, AVIF export and image QA.
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

Con `--variantes`, `escritorio/` lleva en su lugar **lo que se sube al sitio, y nada
más**: `<nombre>-1200.avif` y `<nombre>-1200.jpg` —el carrusel de la ficha, y su JPEG
para la vista previa de WhatsApp y Facebook, que no muestran AVIF— y
`<nombre>-480.avif` —la miniatura de la galería y la tarjeta de la rejilla—. Hasta el
16/10/2026 eran cinco anchos (480 a 2000) en dos formatos: once archivos por foto,
de los que el sitio usaba cuatro. Los formatos de cada ancho los dice
`formatos_por_ancho` en el `config.json`.

**Por producto** (`--por-producto`, y sola cuando las fotos vienen en subcarpetas,
como `crudas/<producto>/`) la salida se agrupa y la carpeta dice el ancho, así que
el nombre ya no lo repite:

```
SALIDA/
└── <producto>/
    ├── maestra/<nombre>.jpg      la maestra, al lienzo del producto (el archivo: hasta 2000 px)
    ├── 1200/<nombre>.avif        el carrusel de la ficha
    ├── 1200/<nombre>.jpg         la vista previa de WhatsApp
    └── 480/<nombre>.avif         la miniatura y la tarjeta
```

Si el lienzo del producto no llega a 1200, **el lienzo ocupa el lugar del carrusel**
(`1000/<nombre>.avif` y `.jpg`): la ficha se sirve con lo mejor que da el material,
no con la miniatura. Si lo pasa, la web se queda en 1200 —el sitio no sirve más
(ADR-0056)— y la resolución de más vive en la maestra.

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

De ahí salen los ajustes que siguen, y conviene no deshacer ninguno sin leer esto.

### 0. Lo que el recorte deja fuera del producto vuelve a él (20/10/2026)

Es el que más fotos arreglaba. El modelo de recorte toma por fondo **lo claro de un fondo de
pantalla** —el blanco del iPhone 15, el lavanda del Redmi Pad 2, el gris perla de los Galaxy— y
deja dentro del equipo huecos encerrados por el bisel. La regla del punto 1 solo cierra los
menores del 0,5 % del producto, para no tapar un asa, así que por los grandes se veía la sombra
de contacto: **manchas grises en la pantalla** de unas 40 fotos de la tanda del 15/10/2026, y
varias salieron LISTA. Con la tapa blanca sobre fondo blanco pasaba lo mismo por el contorno: un
mordisco abierto en la parte de abajo.

`imagen.completar_producto()` corre antes de medir la caja, sobre la foto original:

- **Huecos encerrados.** En un **celular o una tablet** —la categoría de la ficha, que
  `procesar_fichas.py` pasa con `--sin-huecos`— se rellenan siempre: no tienen huecos
  pasantes, y lo encerrado por el bisel es la pantalla. En lo demás se rellenan si en la foto
  **no son del color del fondo**: el hueco de una correa cerrada sí lo es, y sigue abierto.
- **Muescas del contorno**, solo en celulares y tablets, cuyas piezas son convexas: lo que falta
  hasta la envolvente convexa de cada pieza se rellena si casi nada en ello es fondo (≤ 15 %) y
  si **continúa el color de lo que tiene al lado** (salto ≤ 6 ΔE). La segunda condición existe
  por la sombra del piso de los renders: bajo una fila de teléfonos escalonados cae dentro de la
  envolvente, y sin ella se pintaba como equipo (Galaxy S26 Ultra _1, A17 _1). Si la pieza es
  casi un rectángulo (su envolvente cubre el 95 % de su rectángulo mínimo: un equipo de frente), la
  referencia es el rectángulo, para que una esquina mordida entera vuelva completa; lo que de la
  muesca es fondo se queda fuera píxel a píxel, así que una esquina redonda sigue redonda.

El fondo de la foto se lee como la mediana Lab de lo que está lejos del recorte, y un píxel es
fondo a menos de 2,5 ΔE de ella: los renders traen el fondo plano y una tapa blanca queda a 3 o
más. Si la foto no tiene un fondo de estudio —oscuro o disparejo, una mesa— no se juzga nada por
color y solo cuenta la categoría. Un PNG que ya viene recortado no pasa por aquí: su alfa es de
quien lo recortó. Las constantes viven en `imagen.py` y no en `config.json`, porque no cambian el
estilo del catálogo y allá entrarían en la huella de la configuración.

**Y dentro de la silueta va el color de la foto original, no el estimado.** `descontaminar`
(pymatting) calcula un color de primer plano en todo píxel con alfa parcial, y el modelo deja
alfa parcial en el 30 al 73 % del interior de algunas fotos. Subir el alfa a 1 (punto 1) no
devolvía el color: el TCL Pad salía borroso, los Redmi Buds 6 Active con el estuche lavado y el
«Redmi Pad 2» de la pantalla, ilegible. `interior_con_el_original()` usa la misma rampa del
punto 1: en el borde manda lo descontaminado y a 14 px hacia adentro ya va el original tal cual.

Dos controles nuevos lo vigilan, porque `delta_croma` no veía nada de esto —una mancha gris
sobre una pantalla blanca no tiene croma—: **`interior_alterado`** marca REVISAR si más del 1 %
del interior se aparta del original más de 10 ΔE (luminancia incluida), y **`hueco_interior`**
marca REVISAR un celular o una tablet que aún tenga un hueco encerrado. `interior_alterado` se
mide sobre la silueta con sus huecos —salvo los que en la foto son fondo—, que es donde aparece
la mancha; en un PNG recortado el interior se queda con el color de su recorte.

**La versión del retoque** (`salidas.VERSION_RETOQUE`, 20/10/2026) entra en la huella de la
configuración y va en cada foto y en cada marca a ojo. Se sube cuando el código cambia cómo se ve
una foto. Con ella:

- `--nuevas` rehace lo que se procesó con un retoque anterior.
- **Una marca a ojo caduca** cuando la foto se rehace con otra versión: no se aplica sola, pasa a
  REVISAR (`marca_caducada`) con su texto, para que la persona mire si sigue aplicando. Si no,
  `marcar.py --limpiar`; si sí, se vuelve a marcar. Las tres Tab A11 seguían en REPETIR por «el
  recorte se comió la pantalla» cuando el retoque nuevo ya no lo hacía.
- La tanda **avisa** cuántas de sus fotos se hicieron con un retoque anterior, pero no las rehace
  sola: el lienzo y las LISTA de un modelo aprobado cambiarían sin que nadie lo pidiera. Para rehacer fotos concretas sin moverle
el lienzo a las demás del modelo: `procesar.py ... --solo <nombres> --conservar-lienzo
--sin-huecos <modelos>`, y después `procesar_fichas.py ... --tanda ... --devolver`.

### 0.b El color del fondo es la segunda opinión del recorte (20/10/2026)

Casi todo el material es render de fabricante sobre un fondo plano, y ahí el color dice más que el
modelo. `imagen.segunda_opinion()` corre antes de completar el producto, y solo si el fondo de la
foto se pudo leer:

- **Suma** las regiones de color (croma C* ≥ 5 en el 60 % de sus píxeles, al menos el 0,5 % del
  producto) que tocan el producto, no tocan el borde de la foto y el modelo no vio: el panel
  trasero lavanda del Redmi Pad 2 _5, que se publicaba sin él. La sombra del piso es gris y una
  tapa blanca no tiene croma: por aquí no entran. Tampoco lo que **se desvanece** hacia el fondo
  —más del 5 % de la región a menos de 10 ΔE de él—: el reflejo de piso de un celular de color o el
  halo de un fondo pálido con viñeta (medido: 22 % en un reflejo; 0,5 % en el panel del Redmi Pad
  2). Y nada que toque una zona excluida a mano.
- **Quita** lo que el modelo marcó con duda (alfa < 0,9), es del color del fondo y se alcanza desde
  el borde de la foto sin cruzar producto: el arco blanco sobre el TCL Pad _3. Un teléfono blanco no
  se pierde, porque lo encierra un marco que no es del color del fondo. Se entra por la franja del
  borde y no por su primer píxel, porque el filete oscuro del material de Apple la cerraba.

El fondo se lee primero en un anillo junto al borde de la foto —saltando el 1 %, por el filete de
Apple— y solo si ese anillo no es un fondo de estudio, en lo que está lejos del recorte: «lejos del
recorte» incluía lo que el modelo no vio, y con el panel del Redmi Pad 2 adentro el fondo salía
disparejo y sin leer.

### 0.c Un fragmento descartado solo avisa si queda fuera del producto (20/10/2026)

`limpiar_islas` descarta las islas pequeñas antes de completar el producto, y medido sobre la tanda
del 15/10/2026 casi todas eran **del producto**: los lentes de un OPPO A6k, la manzana de un iPhone
16e, el «REDMI» de una tablet. El modelo los vio sueltos porque no vio la superficie de alrededor, y
el relleno de huecos los vuelve a encerrar. El motivo `pieza_descartada` se decide ahora contra el
producto ya completo (`islas_perdidas`): una isla que quedó adentro es una nota; solo cuenta la que
sigue fuera en más de la mitad. En la muestra, seis de ocho fotos dejaron de salir REVISAR por eso.

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

El cierre **sí puenteaba piezas separadas** cuando quedan a menos de 31 px: el Galaxy A17 de
frente salía pegado al de perfil por una franja opaca de fondo. Desde el 20/10/2026 lo que el
cierre añade y en la foto original es del color del fondo se queda fuera (`fondo_px` de
`solidificar_interior`); una grieta dentro del producto, que no es fondo, se sigue cerrando.

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

## El bloque 2 de la ingesta de tecnología: la tanda

La ingesta de tecnología va en tres bloques (`docs/adr/0079`): `/listas-de-proveedor`
deja la carpeta de cada modelo y `catalogo/5-api/por-proponer.json` —los modelos que el
sitio no vende—; **esta skill retoca sus fotos y no deja pasar ninguno a medias**;
`/fichas-de-tecnologia` lleva al panel el borrador con sus fotos.

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar_fichas.py" catalogo/4-entregables/fichas \
    --tanda catalogo/5-api/por-proponer.json
```

**Una tanda grande va por partes** (22/10/2026): `--lote 20` reparte los modelos que hay
que procesar en partes de modelos enteros de hasta 20 fotos y corre `procesar.py` una vez
por parte, en serie; devuelve y decide una sola vez, al final. Un modelo más grande que el
lote va solo. La tanda del 22/10 tenía 297 fotos: en una sola llamada no cabe en el
límite de una terminal en primer plano, y un `procesar.py` de 83 fotos en segundo plano ya
había muerto por memoria. Con `--lote` cada parte es un proceso aparte, así que la tanda
entera puede correr en segundo plano —en Claude Code, con `run_in_background` del Bash—
sin acumular memoria; `--lote` no se combina con `--segundo-plano`.

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar_fichas.py" catalogo/4-entregables/fichas \
    --tanda catalogo/5-api/por-proponer.json --lote 20
```

**La compuerta** (decisión del negocio del 10/10/2026): cada modelo necesita
**4 fotos LISTA** en un lienzo de por lo menos **1200 px** —la mayor variante que
sirve el sitio—. Una LISTA en un lienzo menor no cuenta: si el modelo no reúne
cuatro ni en 1200, lo que falta son fotos de más resolución.

**Una REVISAR aprobada a ojo sí cuenta** (decisión del 22/10/2026). Hasta esa fecha
no contaba, y cuatro modelos de la lista del 15/10 esperaban fotos nuevas con tomas
que, miradas a tamaño real, estaban bien: los contornos completos de unos audífonos
blancos, el canto claro de un celular. Se aprueba con `marcar.py --aprobar
"motivo"` **después de mirarla al 100 %** en la zona que dice su motivo, y la
siguiente corrida de la tanda la cuenta. El lienzo mínimo sigue valiendo, y una
REPETIR no se aprueba. `estado-fotos.json` anota en `aprobadas_a_ojo` cuáles
pasaron la compuerta por una persona y no por el script. No apruebes por tu
cuenta: muéstrale a la persona la foto con su motivo y que decida.

**En la tanda, el lienzo lo elige la compuerta, no la mejor foto** (16/10/2026).
`procesar_fichas.py` le pasa a `procesar.py` `--lienzo-minimo 1200 --listas-minimas 4`,
y el lienzo es el **mayor** escalón de 1200 o más donde caben cuatro fotos por debajo
de `ampliacion_revisar` (`salidas.elegir_lienzo_tanda`). Hasta esa fecha lo fijaba la
mejor foto, como en un lote suelto, y fallaba por los dos lados:

- **Por arriba**: una sola foto grande subía el lienzo y dejaba a las demás en
  REVISAR. El Galaxy A57 tenía una de 1194 px y cinco de 797: lienzo 1600 y una sola
  LISTA; en 1200, las seis.
- **Por abajo**: la mejor foto tenía que llegar con `ampliacion_tolerada` (1,25×),
  más estricta que la compuerta. El Galaxy A27, seis fotos de 800 px, se quedaba en
  1000 y fuera de la tanda, cuando en 1200 salen a 1,28×: LISTA.

**Los celulares y las tablets van con `--sin-huecos`** (20/10/2026): `procesar_fichas.py` lee la
línea `CATEGORÍA:` de la ficha, y esas dos categorías no tienen huecos pasantes (ver «Lo que el
recorte deja fuera del producto vuelve a él»). **Una carpeta con fotos que no está en
`por-proponer.json` se avisa** al final de la tanda: el Redmi Note 17 Pro y el Pro Max se
quedaron sin procesar sin que nada lo dijera.

**Un modelo `sinFicha` no bloquea la tanda** (22/10/2026). `por-proponer.json` anota en `sinFicha`
lo que la API espera pero la lista no trae: el bloque 1 no lo exportó, casi siempre por falta de
descripción, así que el bloque 3 no puede hacerle borrador. Sus fotos se procesan igual —el
trabajo no se pierde— y el informe lo lista aparte (`SIN FICHA`), pero la tanda se
entrega sin esperarlo: el Pro Max retuvo la tanda entera del 15/10/2026 esperando la cuarta foto
de un borrador que no podía existir. La salida es completar la ficha en el bloque 1 —que la lista
siguiente lo exporte— o sacarlo con `--sacar`.

Es el mayor escalón que cumple, y no el que más LISTA deja, para no bajar a 1200 un
modelo que da para 2000. Si ni en 1200 reúne cuatro, se elige el de más LISTA; si
ni ahí sale una, la regla de siempre. El lienzo se decide con una estimación del
tamaño del producto antes del recorte: un modelo justo en el borde puede quedarse
con tres. La compuerta lo detiene igual, sin que nada pase de más.

**La estimación mide contra el fondo de cada foto, no contra el blanco**
(`estimador.py`, 18/10/2026). Hasta esa fecha el contenido era «lo más oscuro que
247», y el material de Apple llega sobre gris #F5F5F7: el iPhone 18 Pro, de 712 px
en un cuadro de 1000, «medía» 1000, la tanda elegía 1600 y salía a 1,91×, en
REVISAR; en 1200 sale a 1,43×, LISTA. Ahora el fondo se lee en un anillo junto al
borde —saltando el filete oscuro que trae ese material— y un PNG o WebP recortado
se mide por su alfa, porque lo transparente puede guardar negro y la foto entera
pasaba por producto. Si el anillo no es un fondo de estudio (oscuro, o menos del
60 % parejo: una mesa, una cama), se toma la foto entera, como antes.

**Antes de gastar un retoque**, el script mide las originales: una foto cuyo lado
mayor no llega a 638 px (1200 × 0,85 / 1,6) no puede salir LISTA, porque el
producto nunca mide más que la foto. Un modelo con menos de cuatro originales que
puedan, espera sin procesarse.

**Y antes de lanzar una tanda grande, `--validar`** (22/10/2026). No retoca nada; por
modelo dice:
- los nombres que no siguen `<id>_N`;
- los archivos de un formato que no se lee, que la tanda ignora sin avisar;
- las fotos bajo el lado mínimo;
- cuántas LISTA se estiman en 1200, con el mismo estimador y la misma regla de la tanda.

Y en toda la tanda, las fotos idénticas en dos modelos (la Redmi Pad 2 bundle y la 4G
compartían sus seis). Sale con 3 si algún modelo no reúne cuatro. Con `--renombrar`
deja las originales como `<id>_N` en orden natural y anota el cambio en
`_estudio/renombradas-<fecha>.json`, pero solo en los modelos que todavía no tienen
procesadas: el nombre entra en la huella de la carpeta, así que renombrar un modelo
procesado fuerza su reproceso y caduca sus aprobaciones. El 22/10 se validaron 297
fotos a mano antes de procesarlas; esto es eso mismo.

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar_fichas.py" catalogo/4-entregables/fichas \
    --tanda catalogo/5-api/por-proponer.json --validar [--renombrar]
```

**Un modelo que cambió se reprocesa entero**, no solo la foto nueva: el lienzo
depende de todas las fotos del modelo, y una foto que se procesara sola elegiría el
suyo. **La principal y las aprobaciones a ojo sobreviven al reproceso** (22/10/2026,
`salidas.heredado_del_previo`): la principal mientras la foto de origen sea la misma, y
la aprobación solo si además la configuración —lienzo y versión del retoque— es la
misma, porque si no el resultado que se aprobó ya es otro. Hasta esa fecha un
reproceso las borraba en silencio.

**Una carpeta de salida, un proceso** (22/10/2026, `candado.py`). `procesar.py`,
`marcar.py` y `hojas.py` toman un candado en `.trabajo/candado.json` con su pid, y se
niegan si otro proceso vivo lo tiene; uno de un proceso que murió no bloquea. Dos a la
vez se pisaban el reporte: la parte 12 de la tanda del 22/10 corrió mientras la 11
seguía en segundo plano y borró del reporte sus 18 fotos. Y si aun así al reporte le
faltan fotos que están en «Fotos originales», la tanda rehace ese modelo **en la misma
corrida** en vez de darlo por «sin fotos nuevas» con 0 LISTA.

Cada modelo queda con su `estado-fotos.json`: `apto`, `en_espera` —con cuántas
LISTA le faltan y la razón de cada foto que falló— o `fuera_de_tanda`. El
veredicto se recalcula contra el reporte en cada corrida, aunque la carpeta no
haya cambiado: lo que se marque a ojo cuenta. El script sale con **0 si la tanda
está completa y con 3 si algún modelo espera** —o si el retoque quedó en segundo
plano: hasta que termine y se corra `--devolver`, la tanda no está completa—.

### Cuando la tanda espera

**No sigas con el bloque 3 con un modelo en espera, ni lo saques por tu cuenta.**
El objetivo es que al final todos los modelos queden con fotos publicables y que
no haga falta otra corrida. Con salida 3:

1. Muéstrale a la persona, por modelo, qué foto falló y por qué —el motivo que
   escribe el script, en sus palabras: «el producto mide 900 px y necesita 1700»,
   «el borde se confunde con el fondo»— y cuántas LISTA le faltan. Dile dónde
   está la carpeta, y de dónde salen las fotos (`listas-de-proveedor`, sección
   «De dónde salen las fotos»). Muéstrale las hojas de revisión de las que fallaron.
   **La espera ya trae la revisión preparada** (22/10/2026): el informe deja los
   comandos de `ampliar.py` y `marcar.py --aprobar` listos para cada REVISAR que
   detiene la tanda, y los mosaicos al 100 % de los modelos a los que les faltan
   hasta 2 LISTA se generan de una vez. Muéstraselos: si al 100 % la foto está
   bien, aprobarla cuenta como LISTA y evita esperar una foto nueva. Aprobar
   sigue siendo de la persona, nunca tuyo.
2. **Espera su confirmación** con `AskUserQuestion`: «ya dejé fotos nuevas»,
   «sacar un modelo de la tanda» o «lo retomo después». No sigas sin respuesta.
3. Con «ya dejé fotos nuevas», vuelve a correr la tanda. El script reprocesa los
   modelos en espera cuya carpeta cambió; si la de alguno no cambió, lo dice
   («sin fotos nuevas desde el último intento») y no lo reprocesa: díselo a la
   persona tal cual, no lo des por intentado. Una foto `SIN PROCESAR` —el retoque
   no la tocó, casi siempre una HEIC sin `pillow-heif`— se reintenta sola.
4. Repite hasta que la salida sea 0.

**Sacar un modelo lo decide la persona**, con su motivo, y nunca tú:

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar_fichas.py" catalogo/4-entregables/fichas \
    --tanda catalogo/5-api/por-proponer.json --sacar <id del modelo> --motivo "sin material oficial"
```

Queda `fuera_de_tanda` y no bloquea la entrega. Vale para la tanda de esa lista:
si después aparecen fotos nuevas en su carpeta, o llega la tanda de otra lista,
vuelve a intentarse. Un modelo que ni siquiera tiene carpeta también se puede
sacar; queda anotado en `_estudio/sacados.json`.

Si el motivo es que el modelo **no se va a vender** —no solo en esta tanda—, la
salida imprime el comando para registrarlo en `referencias/excluidos.json` de
`listas-de-proveedor` (`conocidos.py excluir`): sin ese registro, la lista
siguiente lo vuelve a proponer, a investigar y a pedirle fotos. También lo
decide la persona.

**En audífonos, el par con su estuche cuenta solo** (decisión del 22/10/2026). Dos
auriculares y el estuche —a veces con la tapa aparte— son 3 o 4 piezas, y casi toda
foto buena de unos TWS disparaba `varias_piezas`: 11 fotos de la tanda del 15/10.
En la categoría `audifonos`, una foto de hasta 4 piezas cuyo único reparo son las
piezas sueltas cuenta como LISTA; con 5 o más, o con cualquier otro reparo, sigue
decidiendo la persona. Las demás categorías no cambian.

**Un kit también lo declara la persona** (decisión del 18/10/2026). Hay productos que
se venden con piezas sueltas —la consola con sus Joy-Con, la tablet con teclado y
mouse— y su foto de catálogo las muestra separadas: el recorte encuentra tres piezas o
más y la marca REVISAR (`varias_piezas`), que para la tanda no cuenta. Declarado el
modelo como kit, **esa foto cuenta como LISTA si las piezas sueltas son su único
reparo**: una ampliación de más, un borde dudoso o una marca a ojo la siguen dejando
fuera. En los demás modelos no cambia nada, porque tres piezas también pueden ser dos
productos distintos o la gama de colores, y eso lo decide una persona, no el script.
Si en la hoja ves un modelo esperando solo por `varias_piezas`, pregúntale a la persona
si es un kit; no lo declares por tu cuenta. Desde el 20/10/2026 el informe de la tanda lo sugiere
con la orden lista para copiar («¿Es un kit? … --kit <id>»), y la hoja de revisión lo anota en esa
foto: la sugerencia es para la persona, no para que la apliques.

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar_fichas.py" catalogo/4-entregables/fichas \
    --tanda catalogo/5-api/por-proponer.json --kit <id del modelo> --motivo "consola con los Joy-Con sueltos"
```

**Un modelo sin huecos lo declara también la persona** (20/10/2026). La categoría decide casi siempre
—celulares y tablets van con `--sin-huecos`; relojes y audífonos no, porque una correa cerrada o
una diadema sí tienen huecos—, pero hay modelos de esas categorías que no los tienen y cuyo
interior claro pasa por fondo: el estuche transparente de los Redmi Buds 6 Active salía mutilado.
Si en la hoja ves eso, pregúntale a la persona; no lo declares por tu cuenta:

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar_fichas.py" catalogo/4-entregables/fichas \
    --sin-huecos <id del modelo> --motivo "estuche cerrado, sin huecos pasantes"
```

Queda en `_estudio/sin-huecos.json`, vale para todas las tandas, y el modelo queda «por procesar»:
la siguiente tanda rehace todas sus fotos. `--quitar-sin-huecos <id>` lo deshace.

Un kit queda en `_estudio/kits.json` y vale para todas las tandas; `--quitar-kit <id>` lo
deshace. No pide otro retoque: la siguiente corrida de la tanda recalcula el veredicto
contra el reporte. La hoja de revisión sigue mostrando la foto como REVISAR —el retoque
no sabe de kits—, así que en la revisión a ojo confirma que las piezas son las del kit.

**Con «lo retomo después»**, la espera queda guardada en el `estado-fotos.json`
de cada modelo: la sesión siguiente corre la misma orden y sigue desde ahí, sin
reprocesar lo que ya salió LISTA.

### Cuando la tanda está completa

**Primero, la revisión a ojo** (paso 4 de esta skill), sobre las hojas de las
fotos LISTA: hay defectos que solo ve una persona —una mano, una marca de agua,
dos productos en la toma—. Si una no sirve, márcala con `marcar.py --repetir
"motivo"` y vuelve a correr la tanda: deja de contar, y si el modelo baja de
cuatro, vuelve a esperar. Una REVISAR aprobada a ojo, en cambio, cuenta como LISTA
para la tanda desde el 22/10/2026.

**Y la foto principal se elige aquí** (22/10/2026). La primera del
`estado-fotos.json` es la que el borrador sube de principal, y sin decir nada es
la `_1` que la persona descargó primero, no necesariamente la mejor toma
frontal. Si en la revisión otra se ve mejor de portada, pregúntale a la persona
y márcala: `marcar.py SALIDA <nombre> --principal`, y la siguiente corrida de la
tanda la pone de primera. Una por producto; marcar otra le quita la marca a la
anterior, y una REPETIR no puede ser principal. **Varias en una sola llamada**, una
por producto (`marcar.py SALIDA a-3 b-2 c-1 --principal`): cada llamada regenera todas
las hojas —unos 4 minutos con una tanda de 300 fotos—, y elegir las portadas de 56
modelos de a una eran casi cuatro horas. Para varias decisiones seguidas, `--sin-hojas`
en cada llamada y `hojas.py SALIDA` al final. Y la principal sobrevive al reproceso de
la misma foto (ver «Un modelo que cambió se reprocesa entero»).

Con la revisión hecha:

```bash
python3 "${CLAUDE_SKILL_DIR}/../listas-de-proveedor/scripts/conocidos.py" fotos catalogo/4-entregables/fichas --escribir
```

Anota cada foto procesada en la base de conocidos, con su huella: las carpetas
viven fuera del repositorio, y el registro es lo que nota que una se perdió.
Después dile a la persona que siga con `/fichas-de-tecnologia`.

## Quién consume esta salida: `listas-de-proveedor`

El catálogo de tecnología se surte de esa skill, y **desde el 08/10/2026 el
traspaso es por carpetas de modelo**. Cada modelo tiene su carpeta en
`catalogo/4-entregables/fichas/` —la arma esa skill, con su archivo
`<id>-ficha.txt`—; la persona deja las fotos en `Fotos originales/` y esta skill
escribe la retocada en `Fotos procesadas/`, **con el mismo nombre** (la extensión
puede cambiar: la maestra siempre es JPEG):

```bash
python3 "${CLAUDE_SKILL_DIR}/scripts/procesar_fichas.py" catalogo/4-entregables/fichas
```

```
fichas/Samsung Galaxy S25 Ultra/
├── Fotos originales/samsung-galaxy-s25-ultra_1.png
└── Fotos procesadas/samsung-galaxy-s25-ultra_1.jpg              la maestra
                     web/1200/samsung-galaxy-s25-ultra_1.avif     el carrusel de la ficha
                     web/1200/samsung-galaxy-s25-ultra_1.jpg      la vista previa de WhatsApp
                     web/480/samsung-galaxy-s25-ultra_1.avif      la miniatura y la tarjeta
```

`procesar_fichas.py` no tiene estilo propio: copia las originales a
`fichas/_estudio/crudas/<id del modelo>/`, corre `procesar.py` con `--por-producto
--variantes --nuevas` y devuelve cada resultado a su modelo. Por `--nuevas`,
correrlo otra vez después de soltar fotos nuevas procesa solo esas; con `--tanda`
va en su lugar `--solo` con cada modelo entero y la compuerta del lienzo. Las hojas de
revisión y el reporte quedan en `fichas/_estudio/salida/`, en un sitio para todos
los modelos: ahí se revisa como siempre (paso 4) y ahí se aprueba con `marcar.py`;
`--devolver` vuelve a copiar lo aprobado. Las REPETIR no se devuelven, y **la versión anterior de
una foto que pasó a REPETIR se mueve a `Fotos procesadas/_retiradas/`** (20/10/2026):
`tools/material-catalogo.mjs` sube todo `.jpg` de «Fotos procesadas», y el Redmi Pad 2 _5
malformado seguía ahí, listo para subirse. Se mueve y no se borra: borrarlo lo decide la persona. Con
`--segundo-plano` lanza el proceso y, al terminar, se corre `--devolver` —con la
misma `--tanda`, que es la que decide el estado de cada modelo—. Sin `--tanda`
procesa todas las carpetas y no decide nada: es el modo de antes, para un lote
suelto.

**Si esta skill cambia la forma de su salida** —`<producto>/maestra/` y
`<producto>/<ancho>/` en modo por producto—, `procesar_fichas.py` deja de
encontrar los archivos, y `tools/material-catalogo.mjs`, que sube las tomas, lee los
anchos de las carpetas de `web/` (los que existan hasta 1200) y la vista previa en
el JPEG del mayor. Lo vigilan sus pruebas en `pruebas/`, que corren en
`npm run verificar`. Hubo un `organizar_imagenes.py` que reacomodaba la salida
plana, y estuvo un tiempo sin hacer nada sin que se notara.

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
| Escala | ≥ 1,6× → REVISAR · ≥ 3× → REPETIR (el producto debe medir ≥ 1700 px en la foto) |
| Máscara | corte en 0,2 para que la malla y el tejido entren enteros |
| Interior | se compone opaco: alfa 1 a partir de 14 px del borde, cerrando grietas de 31 px (salvo las que en la foto son fondo) y agujeros de menos del 0,5 % del área; adentro, los píxeles de la foto original |
| Huecos y muescas | lo encerrado por un celular o una tablet vuelve al producto siempre; en lo demás, si no es del color del fondo de la foto. En celulares y tablets también las muescas del contorno que continúan el color vecino |
| Tono y enfoque | apagados: el interior del producto no se retoca |
| Lienzo por producto | escalones 2000 · 1600 · 1200 · 1000 · 800 · 600 · 480 · 400 · 320; se toma el mayor que la mejor foto alcance sin pasar de 1,25× de ampliación, y si no llega a ninguno, el menor. En la tanda (`--lienzo-minimo`), el mayor de 1200 o más donde caben cuatro LISTA |
| Web | AVIF a 10 bits con `avifenc` (a 8 bits con Pillow si no lo hay). Con `--variantes`, dos anchos: 1200 en AVIF y JPEG q88 —el carrusel y la vista previa de WhatsApp— y 480 en AVIF —la miniatura y la tarjeta—; por producto, un lienzo menor que 1200 ocupa el lugar del carrusel |

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
- **Formato**: se leen JPG (también `.jfif`), PNG, WebP, HEIC/HEIF y AVIF. Cualquier
  otro se omite con un aviso, y en el modo de fichas ni siquiera cuenta para las 4.
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
`--nuevas`, `--sin-huecos producto1,producto2` y `--conservar-lienzo` (todas en `--help`).
`--conservar-lienzo` sirve para reprocesar unas pocas fotos de un producto sin moverle el lienzo
a las demás: con `--solo`, el lienzo se elegiría solo con las fotos de esa corrida.

Para un árbol que se vuelve a llenar —`catalogo/fotos/crudas/`, que crece con cada
lote de inventario— el par útil es `--por-producto --nuevas`: agrupa la salida por
producto y salta lo ya procesado cuyo archivo de origen no ha cambiado. En Windows las
rutas van entre comillas (`"C:\fotos\lote 1"`) y el segundo plano funciona igual.

**Una foto enorme se carga reducida** (20/10/2026): el lienzo nunca pasa de 2000 px, así que una
foto de más de 4000 se carga a 4000 —o a lo que haga falta para que el producto siga midiendo 1,5
veces lo que pide el lienzo; nunca hasta tener que ampliar (`salidas.lado_de_carga`)—. Solo con una
medida fiable del producto (fondo de estudio o alfa de un PNG): sobre una mesa el estimador da la
foto entera, y reducir así dejaba el producto ampliado. Medido en la
foto de 8000 px del Moto G17 _3: el pico de memoria baja de 4,4 a 2,4 GB, con la misma salida. Un
reproceso de 83 fotos en segundo plano murió por memoria justo en ella. Aun así, con lotes grandes
conviene ir en tandas de unas 20 en primer plano. Las exclusiones y `caja_origen` siguen en píxeles
de la foto original.

Una carpeta de salida acumula rondas: al reprocesar, el reporte se fusiona y las
hojas se regeneran. Por eso las opciones de salida (lienzo, anchos, formatos y
carpetas) deben ser las mismas en toda la carpeta; si cambian habiendo otras fotos
procesadas, el script se detiene y pide reprocesar todo o usar otra carpeta.

### 4. Revisar como un cliente exigente

Mira cada `revision-*.jpg` (primero van las REPETIR, luego REVISAR y LISTA). Cada foto trae tres
cuadros: antes, después y el **mapa** (20/10/2026), la foto original con lo que el retoque cambió —en
azul lo que se devolvió al producto, en rojo lo que se le quitó y quedó fuera, en amarillo lo que en el
interior no coincide con el original—, y lo que no se publica, apagado. Empieza por el mapa: un
relleno azul donde no hay producto o una mancha amarilla en una pantalla se ven sin ampliar. Para
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
real; nunca sirve para saltarse un REPETIR. Una marca REPETIR retira los archivos
de las carpetas de entrega y `--limpiar` los restaura.

### Por qué el corte de REVISAR está en 1,6× y no en 1,5×

`ampliacion_revisar` estuvo en 1,5 hasta el 16/10/2026. Se subió a 1,6 por una
decisión del negocio, con una medición detrás.

El material oficial de Apple llega en cuadros de 1000 px con el teléfono a unos
650 px: en el lienzo de 1200 que pide la tanda sale a 1,52–1,58×, y seis iPhone
(15, 16, 16e, 17, 17 Pro y 18 Pro) no podían reunir cuatro LISTA. Se midió la
nitidez de bordes (percentil 99,5 del gradiente) en la variante de 1200, con la
misma foto de un Redmi Pad 2 Pro reducida para que saliera a cada ampliación:

| Ampliación | Nitidez de bordes | Gradiente medio |
|--:|--:|--:|
| 1,05× | 100 % | 100 % |
| 1,44× (ya era LISTA) | 70 % | 98 % |
| 1,58× (era REVISAR) | 63 % | 96 % |

La pérdida es gradual, sin un salto en 1,5: el umbral de antes ya aceptaba 30
puntos y 1,58× suma 7. A 1:1 —lo que ve un teléfono de densidad 3× con la
variante de 1200— el pad a 1,44× y a 1,58× no se distinguen, y en el iPhone 15 a
1,58× los lentes y el borde del módulo de cámaras se leen bien. Dos límites de la
prueba: la reducción con Lanczos sobre una fuente limpia es optimista —una foto
pequeña de verdad trae además compresión— y fue un producto plano de bordes
duros; en mallas y tejidos la pérdida puede notarse más, y por eso esas fotos
siguen mirándose al 100 %.

La foto mínima antes de procesar en la tanda bajó con él: 1200 × 0,85 / 1,6 =
638 px. Si el material mejora, esto vuelve a 1,5.

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
| REVISAR | ampliación ≥ 1,6× · más del 1 % del interior a más de 10 ΔE del original · un celular o una tablet con un hueco encerrado · bordes inciertos en más del 8 % del producto · producto parecido a su fondo original en más del 20 % del contorno · producto confundido con el fondo nuevo en más del 25 % del contorno · fragmento descartado apreciable · 3 piezas o más (en la tanda, un kit declarado las cuenta como LISTA) · PNG recortado que llega al borde |
| LISTA | ninguno de los anteriores |

Manda el motivo más grave y las marcas a ojo se suman a las automáticas. Las
notas (dominante de color, luces quemadas, JPEG recomprimido, par de piezas,
bordes poco separados en pocos tramos) son informativas y no cambian el estado.

Rozar el borde no es estar cortado. Una foto recortada al ras —la diadema de
unos audífonos tangente al marco— toca el borde con un contacto que se angosta
hacia afuera: como mucho la mitad de lo que hay un 3 % hacia adentro y el 10 %
del lado del producto (`separar_rasantes`). Ese lado queda como nota, no como
corte; uno que atraviesa el borde con casi el mismo ancho sigue en REPETIR.

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
| `estimador.py` | Tamaño del producto antes del recorte, para elegir el lienzo |
| `hojas.py` | Regenera las hojas de revisión desde el reporte |
| `fondo.py` | Genera o verifica la plantilla del fondo |
| `entorno.py` | Diagnóstico del entorno y comando exacto de instalación |
| `avif10.py` | Estado e instalación de `avifenc` (AVIF a 10 bits) |
| `imagen.py`, `reporte.py` | Módulos internos: píxeles y reporte |

## Integración con la web

Los archivos web siguen el patrón `<nombre>-<ancho>.<formato>`, así que un cargador
de imágenes (por ejemplo, un loader de `NgOptimizedImage` en Angular) traduce el
ancho que pide el navegador directamente a la URL del archivo. Con `--variantes`
son los dos anchos que usa el sitio —el JPEG de 1200 no entra al `srcset`: es la
imagen de `og:image`—:

```html
<img src="tenis-blanco-1200.avif" srcset="tenis-blanco-480.avif 480w, tenis-blanco-1200.avif 1200w"
     sizes="(min-width: 1024px) 50vw, 100vw" alt="Tenis blanco">
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
