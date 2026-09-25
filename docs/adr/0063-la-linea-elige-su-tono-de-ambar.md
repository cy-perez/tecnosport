# ADR-0063 — La línea elige su tono de ámbar

**Fecha:** 2026-09-24
**Estado:** aceptado. Matiza la regla del ámbar de `docs/04-ui-marca.md` —una
sola cosa por pantalla— para un sitio concreto y nombrado; fuera de él la regla
sigue entera.

## Contexto

La banda de portada tenía dos botones: "Ver el catálogo", en ámbar, y "Ver
tecnología", con contorno blanco. Quien llegaba al sitio no sabía qué se vende
hasta entrar al catálogo, y la tira de confianza justo debajo —envío cotizado,
contraentrega, garantía legal— repetía palabra por palabra lo que el párrafo de
apoyo ya decía.

El 24 de septiembre de 2026 se pidió un botón por línea de negocio, cada uno
llevando a la rejilla ya filtrada, «alternando el color de los botones en
amarillo en diferentes tonalidades similares al color que tiene el botón actual».

Eso choca de frente con una regla escrita del kit:

> **La regla del ámbar:** `#F5B301` es una sola cosa por pantalla y solo como
> relleno con texto grafito encima.

Y la regla no es decorativa. El ámbar es el color señal: sobre blanco da 1.85:1,
no sirve como texto ni como línea, y su único trabajo es marcar la acción que
importa. Cuatro botones ámbar en la misma banda son, literalmente, cuatro cosas
importantes.

## Decisión

**En la banda de portada, y solo ahí, el ámbar se despliega en cuatro pasos: uno
por línea de negocio.**

Los cuatro son el mismo ámbar. El generador (`packages/marca/generador/kit_ui.py`)
los deriva mezclando `--color-acento` hacia `--color-superficie` en pasos del
14 %, y salen a `tokens.css` como `--color-acento`, `--color-acento-2`,
`--color-acento-3` y `--color-acento-4`.

Tres cosas que sostienen que siga siendo el ámbar y no cuatro amarillos:

- **No tienen `sobre-` propio.** Los cuatro llevan el mismo `--color-sobre-acento`
  —el grafito—, que es la mitad de la regla original: relleno ámbar con grafito
  encima. `tools/verificar-contrastes.mjs` vigila los tres pares nuevos; el peor
  de los cuatro da 9.69:1.
- **Aclaran, no oscurecen.** Oscurecer un 12 % es exactamente lo que
  `estados_primario` hace para el `hover`, así que el tono 2 habría salido
  idéntico al hover del tono 1: pasar el ratón por "Ver ropa" la habría vuelto
  el botón de al lado. Aclarando, además, el contraste contra el grafito sube en
  cada paso en vez de bajar.
- **No hay un quinto.** La escala tiene exactamente cuatro pasos porque hay
  exactamente cuatro líneas de negocio (`LINEAS`, en `filtro-productos.model.ts`).
  No es una paleta abierta a la que se le pide un tono más cuando hace falta.

El orden de la banda —ropa, calzado, bolsos, tecnología— **no** es el canónico de
`LINEAS`, donde tecnología va primera por rotación. En la banda, la primera
posición y el tono más saturado van juntos, y qué línea encabeza la portada lo
decide el negocio. El menú lateral y el filtro del catálogo siguen ofreciendo las
cuatro en el orden del modelo.

## Consecuencias

- La regla del ámbar deja de leerse como absoluta y pasa a tener una excepción
  con nombre y sitio. `docs/04-ui-marca.md` lo dice ahí mismo, no solo aquí: una
  regla con una excepción que solo vive en un ADR es una regla que alguien va a
  romper en otro sitio creyendo que también vale.
- Cambiar `color.acento` en `tokens.json` sigue recalculando el sistema entero,
  la escala incluida. Los cuatro tonos no se escriben a mano en ninguna parte.
- Los botones de la banda ganan estado de hover, que no tenían ninguno. Es
  `hover:brightness-110` y no cuatro tokens más: aclarar un 10 % ya es lo que
  hace la variante `peligro` de `ts-boton`, y pedirle al kit cuatro `hover` para
  un estado que nadie más consume sería inflarlo sin motivo.
- El día que se añada una quinta línea de negocio hay que decidir si la escala
  crece o si la banda deja de ser un botón por línea. Crecer tiene tope: hacia el
  claro, el paso siguiente empieza a confundirse con el fondo de página.
