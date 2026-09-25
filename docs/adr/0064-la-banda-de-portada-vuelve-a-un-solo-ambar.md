# ADR-0064 — La banda de portada vuelve a un solo ámbar

**Fecha:** 2026-09-25
**Estado:** aceptado. **Sustituye a `ADR-0063`**, que duró un día. Con esto la
regla del ámbar de `docs/04-ui-marca.md` vuelve a no tener excepciones.

## Contexto

`ADR-0063` razonó, el 24 de septiembre de 2026, por qué la banda de portada podía
llevar cuatro tonos de ámbar —uno por línea de negocio— cuando la regla del kit
dice "una sola cosa por pantalla". El razonamiento se sostenía sobre el papel: son
el mismo ámbar, los cuatro llevan el mismo grafito encima, la escala tiene
exactamente cuatro pasos porque hay exactamente cuatro líneas, y aclara en vez de
oscurecer para no chocar con el `hover`.

Puesto en pantalla, dos cosas que ningún razonamiento de color anticipa:

- **El cuarto paso parece deshabilitado.** Aclarado un 42 % hacia la superficie,
  "Ver tecnología" no se lee como otro botón de la misma familia sino como el
  primero apagado. Y tecnología es la línea que el negocio quiere delante.
- **Cuatro tonos son cuatro jerarquías.** El degradado de saturación dice
  "primero esto, luego esto otro" aunque las cuatro líneas valgan lo mismo. La
  banda ofrece cuatro puertas, no un camino.

A lo que se suma que los cuatro botones no medían igual: cada uno tomaba el ancho
de su etiqueta, así que "Ver bolsos" era la mitad de "Ver calzado deportivo".
Cuatro anchos distintos y cuatro tonos distintos sobre la misma fila es mucha
diferencia para cuatro cosas que son la misma cosa.

## Decisión

**Los cuatro botones de la banda van en `--color-acento`, el ámbar de marca, y
del mismo tamaño.**

El tamaño lo iguala la rejilla, no el relleno: `grid-cols-2` reparte la banda en
celdas iguales —lo que pida la etiqueta más larga— y los cuatro botones se
estiran a su celda. Dos columnas y no cuatro porque en escritorio la banda ocupa
media rejilla, y cuatro columnas ahí dejarían cada botón más estrecho que su
propia etiqueta.

Las clases viven en una constante de `ts-hero.ts` y son **una sola cadena para
los cuatro**, que es lo que `ts-hero.spec.ts` comprueba: no que sean unas clases
concretas —eso se mira en el navegador, jsdom no resuelve Tailwind— sino que sean
idénticas. Es lo que se rompe si alguien vuelve a escribirlas botón por botón, y
así fue como se perdió antes el `anillo-foco-sobre-acento` de uno de los cuatro.

## Consecuencias

- La regla del ámbar vuelve a ser absoluta. La excepción sale de
  `docs/04-ui-marca.md` y `ADR-0063` queda marcado como sustituido.
- **`--color-acento-2`, `-3` y `-4` se quedan en `tokens.json` y en
  `tokens.css`, sin usar.** No se borran hoy por dos razones: quitarlos obliga a
  regenerar el kit y a mover `tools/verificar-contrastes.mjs`, que hoy vigila los
  tres pares y los da por buenos; y la escala es una derivación correcta que
  puede volver a hacer falta —una promoción, una campaña— sin volver a
  discutirla. Si al cerrar la siguiente fase siguen sin usarse, se retiran.
- La banda conserva `hover:brightness-110`, que entró con `ADR-0063` y es lo
  único de aquel cambio que no dependía de la escala: antes no tenía ningún
  estado de hover.
- La cuarta línea de la tira de confianza —"Diversas opciones de pago"— entra en
  el mismo cambio y con la misma idea: cuatro cosas iguales se pintan iguales.
  Los cuatro sellos van en rejilla, dos por fila en teléfono y cuatro desde
  tableta, en vez de un `flex-wrap` donde el cuarto colgaba solo.
