---
name: fotos-estudio-degradado
description: Convierte lotes de fotos de producto en tomas de estudio uniformes con el estilo de TecnoSport — aísla el producto sin tocar su color, su forma ni sus logos, lo escala al 85 % de un lienzo de 2000×2000, lo centra sobre un fondo degradado gris idéntico en todo el catálogo con una sombra de contacto sutil y exporta la maestra JPEG y el AVIF web, con un reporte honesto que marca cada foto como LISTA, REVISAR o REPETIR. Úsala siempre que alguien pida fotos «de estudio», «con fondo gris», «con degradado», «con sombra», «como las de TecnoSport» o que combinen con el catálogo que ya tiene ese estilo, aunque no diga «editar». No la uses para fondo blanco puro o fotos para Mercado Libre y Amazon (eso es fotos-de-producto), para piezas publicitarias (diseno-publicitario) ni para fotogramas del visor 360. Covers studio product photos with grey gradient backgrounds and contact shadow, batch background removal, AVIF export and image QA.
---

# Fotos de estudio con degradado (estilo TecnoSport)

Actúa como el retocador del estudio de TecnoSport. Llegan fotos tomadas con el
celular —sobre la cama, una mesa o el piso, a veces reenviadas por WhatsApp— y el
catálogo necesita que todas se vean iguales: mismo fondo gris degradado, mismo
tamaño de producto, misma sombra. Tu valor no es sólo procesar: es **decir con
honestidad qué foto sirve, cuál hay que mirar de cerca y cuál hay que repetir**.

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
| Fotos «de estudio», «fondo gris», «con degradado», «con sombra», «como las de la tienda» | esta |
| Fondo blanco puro, Mercado Libre, Amazon, Instagram Shopping | `fotos-de-producto` |
| Volantes, banners, historias o anuncios hechos con las fotos | `diseno-publicitario` |
| Fotogramas del visor 360 | ninguna: aquí cada foto se recorta y se escala por su cuenta, así que en una secuencia el producto cambiaría de tamaño y el borde temblaría entre fotogramas |

## Qué se entrega

```
SALIDA/
├── maestras/<nombre>.jpg            2000×2000 · JPEG q92 progresivo 4:4:4 · sRGB · sin EXIF/XMP/IPTC · ≤ 800 KB
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

## Especificación

| Aspecto | Valor por defecto (`config.json`) |
|---|---|
| Lienzo | 2000×2000; con `--por-producto`, el mayor escalón de `lienzos_escala` que el material del producto alcance |
| Encuadre | lado mayor de la caja del producto (alfa ≥ 10 %) al 85 % del lienzo: 1700 ± 2 px, centrado ± 2 px |
| Fondo | plantilla `assets/fondo-2000x2000.png`: degradado radial #FFFFFF → #A5A5A5, blanco hasta el 26 % del radio y rampa lineal hasta la esquina, con tramado ±1 de semilla fija, idéntico en todo el catálogo |
| Sombra | #000000 al 63 %, dilatación 6 px, desenfoque σ 58 px, desplazada 14 px hacia abajo, modo normal, siempre debajo del producto |
| Recorte | rembg `isnet-general-use` en dos pasadas; islas < 0,5 % descartadas con aviso; borde descontaminado y contraído 1 px |
| Adornos | se quitan solos los elementos ajenos separados del cuerpo, pequeños (< 15 %) y de un color que no aparece en él (≥ 20 en a*b*): los destellos de «Galaxy AI» y adornos de render parecidos. Las piezas legítimas comparten el color del cuerpo y se conservan |
| Tono | sólo L*: niveles con recorte ≤ 0,5 %, ganancia ≤ 0,3 EV, microcontraste leve; a* y b* intactos (Δcroma ≤ 2) |
| Enfoque | después de escalar, sobre L*: radio 1 px, 70 %, umbral 2 |
| Escala | ≥ 1,5× → REVISAR · ≥ 2× → REPETIR (el producto debe medir ≥ 1700 px en la foto) |
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
- **Silueta legible**: un producto claro puede fundirse con el centro del degradado.
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
- **Producto claro sobre el centro claro del degradado**: la silueta queda suave.
  El fondo no se oscurece foto por foto porque rompería la uniformidad.
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
- **WebP**: con pérdida deja anillos en este degradado (1,1–1,25 niveles medidos,
  aun a calidad 100); por eso el respaldo de AVIF es JPEG. Si un sistema exige
  WebP, `--ajuste 'formatos_web=["avif","webp"]'` lo genera y el reporte lo advierte.
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
maestras, con una advertencia: un redimensionador genérico promedia el tramado y
el degradado vuelve a mostrar escalones al codificar a 8 bits. Por eso
`procesar.py` vuelve a tramar el fondo en cada variante; si los tamaños se generan
en la nube, ese proceso debe hacer lo mismo o codificar AVIF a 10 bits.
