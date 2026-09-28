# ADR-0065 — Las baldosas de línea vuelven al ámbar, en un solo tono suave

**Fecha:** 2026-09-28
**Estado:** aceptado. Modifica a `ADR-0064` en lo que toca a las **baldosas de
"Nuestras líneas"**; la banda del hero, que es de lo que aquel ADR trataba
principalmente, no se toca y sigue con un solo `--color-acento`.

## Contexto

Esta es la tercera vuelta de la misma decisión en cinco días, así que lo primero
es dejar claro qué es cada cosa, porque `ADR-0063` y `ADR-0064` hablan de dos
superficies distintas de la misma pantalla y es fácil confundirlas:

- **La banda del hero** — "Ver ropa", "Ver calzado deportivo"… — cuatro botones
  dentro del carrusel. `ADR-0063` les dio un tono de ámbar a cada uno y
  `ADR-0064` los devolvió a `--color-acento`, todos iguales. **Eso sigue así.**
- **Las baldosas de "Nuestras líneas"** — cuatro enlaces más abajo, en su propia
  sección. `ADR-0063` les dio la misma escala, `ADR-0064` los mandó a
  `--color-primario-suave`. **Esto es lo que cambia aquí.**

El 28 de septiembre de 2026 se pidió que las baldosas llevaran "un amarillo
similar al de la sección del carrusel, pero de un tono más suave".

De los dos argumentos con los que `ADR-0064` sacó el ámbar de aquí, **uno deja de
aplicar y el otro se asume a sabiendas**:

- **"El cuarto paso parece deshabilitado"** y **"cuatro tonos son cuatro
  jerarquías"** eran, los dos, problemas de la *escala*: de que hubiera cuatro
  tonos para cuatro cosas que valen lo mismo. Con un solo tono para las cuatro no
  hay paso apagado ni degradado que ordene nada.
- **"El ámbar es una sola cosa por pantalla"** sigue siendo cierto, y la portada
  ya gasta la suya en los botones del carrusel. Esta es la excepción real y no
  se disimula.

## Decisión

**Las cuatro baldosas van en `--color-acento-5`, el mismo para las cuatro.**

`acento-5` es el ámbar de marca aclarado cinco pasos hacia la superficie
(`#FBDE8F` contra el `#F5B301` del carrusel). Lo que sostiene la excepción de
arriba es justamente eso: no es el color señal, es el color señal rebajado, así
que la jerarquía entre el carrusel y las baldosas se mantiene por saturación en
vez de por tono. Quien mira la portada sigue viendo **una** cosa ámbar plena —los
botones del hero— y una superficie tintada debajo.

**El tono 5 no existía y se pidió al kit, que es donde se piden los colores.**
La primera versión de este cambio usó `acento-4`, el más claro que había, y al
verlo en pantalla se pidió bajarlo un paso más. La salida no es escribir un
amarillo a mano —la regla dura #2 lo prohíbe y con razón: un HEX suelto no se
recalcula el día que cambie el ámbar de marca— sino alargar la rampa que
`generador/kit_ui.py` ya derivaba, que va en pasos del 14 % hacia la superficie.
Cambiar `--color-acento` sigue recalculando los cinco.

Con eso, la escala deja de significar lo que significaba: nació como "un tono
por línea de negocio" y hoy es simplemente la rampa de un mismo ámbar, donde
quien la usa elige cuánto quiere bajar la señal. El 2, el 3 y el 4 siguen
derivándose y sin pintar nada.

## Consecuencias

- **El texto pasa de `--color-texto` a `--color-sobre-acento`, y no es cosmética:
  sin eso la baldosa no se lee en tema oscuro.** Los cinco tokens de la escala
  ámbar valen lo mismo en los dos temas, mientras que `--color-texto` se va a
  casi blanco en oscuro. `sobre-acento` es grafito siempre y ya estaba vigilado
  por `npm run contrastes` (13,64:1).

- **El borde cambia de par, y el que había estaba al revés en tema oscuro sin que
  nadie lo hubiera visto.** Con `primario-suave` de relleno, el reposo era
  `--color-borde` y el hover `--color-primario`. Sobre el amarillo nuevo eso da:
  reposo 1,2:1 en claro (invisible) y 9,6:1 en oscuro (un marco negro), y hover
  grafito en claro (fuerte) pero ámbar en oscuro (1,5:1). Es decir, **en tema
  oscuro pasar el puntero habría borrado el borde en vez de marcarlo**. Ahora el
  reposo es `--color-acento` —un filo apenas más hondo que el relleno, decoración
  deliberada, y por eso *no* se declara como par vigilado— y el hover
  `--color-sobre-acento`, grafito en los dos temas, que es la afordancia.

- **El anillo de foco se queda en `anillo-foco` y no pasa a
  `anillo-foco-sobre-acento`**, que es lo que sí hacen `ts-boton` en su variante
  ámbar y el enlace de salto. Aquí sería el error contrario: `outline-offset` de
  2 px dibuja el anillo **fuera** de la baldosa, sobre el lienzo, y ahí
  `--color-sobre-acento` es grafito contra un lienzo casi negro en tema oscuro
  (1,05:1). `--color-foco` da 8,9:1 contra ese lienzo. La regla que queda escrita
  es la general: **el color del anillo lo decide la superficie sobre la que el
  anillo se dibuja, no la del elemento que lo tiene**.

- `--color-primario-suave` se queda **sin un solo uso** en el frontend. Su par
  sigue declarado en `tools/verificar-contrastes.mjs`, marcado como sin uso: es
  el único token del kit que cambia de familia con el tema —gris casi neutro en
  claro, ámbar apagado en oscuro— y el día que vuelva a usarse nadie se va a
  acordar de comprobar que el texto encima se lee.

- Lo que este ADR **no** decide, y conviene que se sepa: si algún día el hero y
  las baldosas tienen que convivir con una tercera superficie ámbar, la regla de
  "una sola cosa por pantalla" ya no va a poder sostener la diferencia por
  saturación. En ese momento hay que revisar la regla entera, no alargar la rampa
  con un sexto paso.
