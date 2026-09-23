# Criterios de aceptación, reporte y mediciones

## Qué comprueba `verificar.py`

Relee los archivos entregados; no se fía sólo del reporte. Sale con código 0 si todo
cumple y 1 si algo falla. Los «✗» son fallos y los «·» son avisos.

| Criterio | Cómo se comprueba |
|---|---|
| Nombres | `maestras/<nombre>.jpg` y `escritorio/<nombre>-<ancho>.<formato>` según la configuración guardada; por producto, `<producto>/maestra/<nombre>.jpg` y `<producto>/<ancho>/<nombre>.<formato>`; ningún archivo ajeno en esas carpetas |
| Maestra | el lienzo del lote —o el del producto, que el reporte guarda foto a foto—, RGB de 8 bits, perfil sRGB, sin EXIF, XMP ni IPTC; avisos si no es progresiva, si no es 4:4:4 o si pesa más de 800 KB |
| Web | cada archivo se decodifica y mide lo que dice su nombre; las JPEG sin metadatos; aviso si superan su peso objetivo |
| Encuadre | lado mayor al 85 % del lienzo de la foto (1700 ± 2 px en 2000) y centro a ≤ 2 px, medidos de nuevo sobre la máscara guardada en `.trabajo/mascaras/` |
| Fondo | esquinas en el color de `fondo_esquinas` ± 3 niveles: hoy #FFFFFF, porque el fondo es blanco plano |
| Color | Δcroma ≤ 2: distancia media en el plano a*b* (Lab) entre la foto original y la maestra decodificada, dentro del producto |
| Estados | ampliación ≥ 2× o producto cortado deben ser REPETIR; ampliación ≥ 1,5× no puede ser LISTA sin aprobación; ninguna REPETIR puede tener archivos en las carpetas de entrega |
| Configuración | aviso si una foto se procesó con parámetros distintos a los del resto |

## Campos del reporte (`reporte.json`)

Encabezado: `configuracion` (todos los parámetros usados), `huella_config`,
`entorno` (versiones y codificador AVIF), `fondo` (origen y huella de píxeles de la
plantilla), `rondas`, `resumen` y `hojas_revision`.

Por foto:

- `estado` y `motivos` (texto final); `motivos_auto` (con código), `marcas` (a ojo),
  `revisada` (aprobación con fecha y nota), `exclusiones` (rectángulos en la foto original).
- `avisos`: problemas de la salida (peso, escalones, descontaminación fallida).
  `notas`: datos informativos que no cambian el estado.
- `entrada`: formato, tamaño, orientación EXIF, calidad JPEG estimada y perfil ICC.
- `recorte`: método, lados tocados, islas descartadas, piezas, `bordes_inciertos`
  (fracción del producto con alfa entre 0,15 y 0,85) y `contraste_original`.
- `escala`, `caja_origen` (en la foto) y `caja_producto` (en el lienzo, con el
  desvío del centro).
- `tono`: EV aplicado, punto negro, recortes nuevos de blancos y negros, y el
  porcentaje de píxeles en que se limitó la subida de L* para no salir del gamut
  (sin ese límite, a* y b* cambiarían).
- `sombra.separacion` y `recorte.contraste_original`: mediciones por 36
  sectores alrededor del centro del producto. Cada una trae `sectores`, cuántos
  fallan, `fraccion`, `angulos`, el peor valor, `peor_angulo` y `donde` (los
  ángulos traducidos a direcciones; 0° es la derecha y 90° es abajo, porque en la
  imagen y crece hacia abajo).
  - `separacion`: diferencia de L* entre el borde del producto y el fondo nuevo;
    falla un sector si ΔL* < 3.
  - `contraste_original`: diferencia de color ΔE76 entre el borde del producto y
    el fondo de la foto original; falla un sector si ΔE < 8.
- `salidas.maestra` (ruta, KB, calidad) y `salidas.web[]` (ruta, ancho, formato,
  KB, calidad, bits, escalones medidos e intentos). Si la foto se marcó REPETIR
  después de procesarla, sus archivos quedan en `salidas_retenidas`.
- `vista_previa` (sólo REPETIR), `huella_origen`, `huella_config`, `ronda`, `segundos`.

## Mediciones que respaldan los valores por defecto

**Escalones del degradado.** Percentil 99 del error local respecto al fondo
ideal, en niveles de 8 bits, fuera de una franja alrededor del producto. Umbral: 0,6.

La tabla que sigue se midió con el **degradado gris** que el catálogo usó hasta el
23/09/2026, y es la que justifica el AVIF a 10 bits, el tramado y el descarte de
WebP. Con el fondo blanco plano el único degradado que queda es el de la sombra y
todo baja; lo medido entonces está al final de esta sección.

| Codificación | Escalones | Observación |
|---|---|---|
| Fondo con tramado ±1, sin comprimir | 0,12 | piso de la medición |
| JPEG q92 (maestra) | 0,44–0,46 | 160–340 KB con producto |
| AVIF 8 bits (Pillow) q60 / q70 / q80 / q90 | 1,10 / 0,81 / 0,55 / 0,50 | escalones en bloque a q60 |
| AVIF 10 bits (avifenc 1.0.4) q60 | 0,45 sólo fondo · 0,37–0,60 con producto | 4–44 KB a 2000 px |
| AVIF 10 bits (avifenc 1.4.2, la que instala `avif10.py`) q60 | 0,31–0,33 con producto | 10–24 KB a 2000 px |
| JPEG q88 (respaldo web) | 0,41–0,44 | 120–260 KB a 2000 px |
| WebP q80 / q95 / q100 | 1,25 / 1,22 / 1,17 | anillos concéntricos a cualquier calidad |
| WebP sin pérdida | 0,12 | 1,4 MB a 2000 px |

Al reducir una imagen, el tramado del fondo se promedia y el degradado vuelve a
quedar en escalones al pasar a 8 bits; por eso cada variante se trama de nuevo. La
zona medida en las variantes se reduce por área, no con Lanczos: con Lanczos, a
480 px, hasta la imagen sin comprimir marcaba 1,38.

**Fondo blanco plano, y por qué el tramado quedó en 0.** Medido el 23/09/2026 al
cambiar el fondo, con el JBL Go 5 (`--variantes`, AVIF a 10 bits y JPEG q88):

| Ancho | AVIF con tramado ±1 | AVIF sin tramado | JPEG con tramado ±1 | JPEG sin tramado |
|---|--:|--:|--:|--:|
| 480 | 0,57 | 0,56 | 0,33 | 0,24 |
| 800 | 0,46 | 0,41 | 0,35 | 0,24 |
| 1200 | 0,42 | 0,36 | 0,36 | 0,28 |
| 1600 | 0,42 | 0,33 | 0,37 | 0,31 |
| 2000 | 0,32 | 0,32 | 0,33 | 0,33 |

Sin tramado los escalones son iguales o mejores en todos los anchos —el ruido
propio del tramado cuenta como error local— y el blanco queda en 255 exacto: con
tramado, 16 de cada 3600 píxeles de una esquina salían en 254. Los pesos no
cambian (JPEG 456 → 455 KB, AVIF 156 → 156 KB a 2000 px). Por eso
`dither_niveles` es 0 mientras el fondo sea plano.

**Qué le pasó a la separación al cambiar de fondo.** Diez fotos del catálogo, las
peores sobre gris, reprocesadas sobre blanco: la fracción del contorno con
ΔL* < 3 subió en los productos claros y bajó en los oscuros.

| Foto | Gris | Blanco |
|---|--:|--:|
| honor-x8b-11-wifi-4gb-ram-128gb-01 (plateado) | 0,306 | 0,528 |
| jbl-partybox-320-03 | 0,167 | 0,361 |
| samsung-galaxy-s25-ultra-256gb-04 | 0,417 | 0,389 |
| lenovo-tab-plus-11-wifi-8gb-ram-256gb-02 | 0,361 | 0,167 |
| lenovo-tab-plus-11-wifi-8gb-ram-256gb-04 | 0,278 | 0,056 |
| jbl-boombox-4-03 | 0,139 | 0,000 |

El umbral (0,25 del contorno) no se movió: sobre blanco marca exactamente la foto
cuyo contorno depende sólo de la sombra, que es la que hay que mirar al 100 %.

**Fiabilidad del recorte.** En el lote de prueba (15 fotos), el contraste con el
fondo original falló en el 47 % del contorno del tenis blanco sobre mesa blanca
(ΔE mínima 1,3) y en el 28 % del celular negro sobre fondo oscuro (ΔE mínima 0,65);
ninguna otra foto pasó del 6 %. De ahí el umbral: ΔE < 8 en más del 20 % del contorno.

**Tiempos** con un núcleo y 4 GB de RAM: de 5 a 16 s por foto (15 fotos en 129 s;
con `--variantes`, 214 s). La primera foto suma la carga del modelo y, en un entorno
recién instalado, la compilación de `pymatting` (58 s en total en la prueba).
