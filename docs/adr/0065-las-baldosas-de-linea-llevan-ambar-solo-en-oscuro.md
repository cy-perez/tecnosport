# ADR-0065 — Las baldosas de línea llevan ámbar solo en tema oscuro

**Fecha:** 2026-09-28
**Estado:** aceptado. Modifica a `ADR-0064` en lo que toca a las **baldosas de
"Nuestras líneas"**; la banda del hero, que es de lo que aquel ADR trataba
principalmente, no se toca y sigue con un solo `--color-acento`.

## Contexto

Esta es la cuarta vuelta de la misma decisión en cinco días, así que lo primero
es dejar claro qué es cada cosa, porque `ADR-0063` y `ADR-0064` hablan de dos
superficies distintas de la misma pantalla y es fácil confundirlas:

- **La banda del hero** — "Ver ropa", "Ver calzado deportivo"… — cuatro botones
  dentro del carrusel. `ADR-0063` les dio un tono de ámbar a cada uno y
  `ADR-0064` los devolvió a `--color-acento`, todos iguales. **Eso sigue así.**
- **Las baldosas de "Nuestras líneas"** — cuatro enlaces más abajo, en su propia
  sección. `ADR-0063` les dio la misma escala, `ADR-0064` los mandó a
  `--color-primario-suave`. **Esto es lo que cambia aquí.**

El 28 de septiembre de 2026 se pidió que las baldosas llevaran "un amarillo
similar al de la sección del carrusel, pero de un tono más suave" y, tras verlo
en pantalla, que ese ámbar quedara **solo en tema oscuro**, con el gris de vuelta
en claro.

De los dos argumentos con los que `ADR-0064` sacó el ámbar de aquí, **uno deja de
aplicar y el otro se asume a sabiendas**:

- **"El cuarto paso parece deshabilitado"** y **"cuatro tonos son cuatro
  jerarquías"** eran, los dos, problemas de la *escala*: de que hubiera cuatro
  tonos para cuatro cosas que valen lo mismo. Con un solo tono para las cuatro no
  hay paso apagado ni degradado que ordene nada.
- **"El ámbar es una sola cosa por pantalla"** sigue siendo cierto **en tema
  claro**, y la portada ya gasta la suya en los botones del carrusel. Ese es
  justamente el argumento que sobrevivió a las cuatro vueltas, y el que acabó
  decidiendo que la excepción solo valga en oscuro.

## Decisión

**Las baldosas se rellenan con `--color-primario-suave` en tema claro y con
`--color-acento-2` en tema oscuro.** Es decir: en claro se quedan como las dejó
`ADR-0064`, y la excepción del ámbar **existe solo en oscuro**.

Esa asimetría no es un apaño, y es lo único que hay que entender de este ADR:
**la regla del ámbar nace de un problema de fondo claro.** `docs/04-ui-marca.md`
la enuncia con su motivo al lado — el ámbar sobre blanco da 1,85:1, así que solo
sirve como relleno con grafito encima, y de ahí "una sola cosa por pantalla",
que en la portada ya se llevan los botones del carrusel. Sobre el lienzo oscuro
ese problema **no existe**: `--color-acento` da 10,14:1 contra `--color-fondo`,
y `tokens.json` lleva escrito desde el principio que en modo oscuro el grafito
deja de funcionar como color de marca y manda el ámbar. La excepción vive
exactamente donde la regla no aprieta.

`acento-2` y no otro: un paso por debajo del color señal, el último antes de él.
Sigue estando por debajo del ámbar pleno del carrusel, así que dentro del tema
oscuro la jerarquía entre las dos superficies se mantiene por saturación.

### Cómo se llegó aquí, porque importa

Cuatro vueltas sobre la misma superficie en cinco días:

| | decisión | qué falló |
|---|---|---|
| `ADR-0063` | un tono de ámbar por línea | el tono más claro parecía deshabilitado; cuatro tonos jerarquizan cuatro cosas iguales |
| `ADR-0064` | gris tenue de marca, los dos temas | nada; se pidió volver al ámbar |
| este, 1.ª versión | `acento-4`, luego un `acento-5` nuevo, luego `acento-3`, los dos temas | en claro competía con el carrusel, que es donde la regla del ámbar sí aprieta |
| este, definitivo | gris en claro, `acento-2` en oscuro | — |

De ahí dos cosas que quedan en el repositorio:

- **El quinto paso de la escala se retiró del kit.** Se había añadido para tener
  el amarillo más suave posible y dejó de hacer falta. Un paso derivado que no
  pinta nada es una invitación a usarlo. El 3 y el 4 se quedan porque son de
  `ADR-0063` y `ADR-0064` decidió conservarlos a propósito.
- **El color sale de `tokens.json`, nunca de un hex a mano** (regla dura #2), y
  eso no lo cambió ninguna de las vueltas.

## Consecuencias

- **Voltean tres cosas, no una.** El relleno cambia con el tema, así que el texto
  y el borde tienen que cambiar con él: `text-ts-texto` se lee sobre el gris
  (14,77:1) pero se iría a casi blanco sobre el amarillo, de ahí
  `oscuro:text-ts-sobre-acento` (10,53:1).

- **El contorno en claro es `--color-borde-control` a media opacidad, y no es
  decoración: es lo que hace que se lean como botones.** Con `--color-borde`
  daban 1,06:1 sobre su propio relleno —o sea, nada— y cuatro rectángulos grises
  sin contorno se leen como etiquetas y no como algo que se pulsa. Al 50 % da
  **1,66:1**. En oscuro no hace falta contorno —el relleno ámbar ya separa la
  baldosa del lienzo— y el borde se queda invisible a propósito (1,09:1).

- **Ese 1,66 incumple WCAG 1.4.11, que pide 3:1, y se decidió a sabiendas.** Hay
  exactamente dos formas de cumplirlo y las dos se maquetaron y se miraron el
  28 de septiembre de 2026:

  | | cómo cumple | por qué se descartó |
  |---|---|---|
  | `borde-control` a pleno color | el borde, 3,06:1 | pasa por 0,06 y se ve duro para lo que es el fondo de una sección |
  | relleno gris medio `#828488` | el relleno, 3,46:1 contra el lienzo | deja de ser el relleno tenue de marca: cuatro bloques pesados |

  Lo que sostiene la excepción es **qué son estas baldosas**: enlaces dentro de
  un `<nav>`, con su texto visible y su propio nombre accesible, no controles sin
  etiqueta cuya única pista sea el recuadro. El criterio apunta a lo segundo. El
  día que dejen de ser enlaces con texto, esto hay que revisarlo.

  Queda **sin declarar** en `npm run contrastes`, con su porqué escrito ahí
  mismo: la tabla no sabe expresar una opacidad, y declarar el color pleno diría
  que se pinta algo que no se pinta.

- **El hover pasa a `--color-sobre-acento`, y eso corrige un defecto que ya
  estaba en producción.** Era `--color-primario`, que es grafito en claro pero
  **ámbar en oscuro**. Mientras el relleno fue gris no se notó; sobre un relleno
  ámbar el hover habría *borrado* el borde en vez de marcarlo (1,09:1).
  `sobre-acento` es grafito en los dos temas y no necesita variante.

- **`npm run contrastes` aprendió a declarar el tema de un par, y este cambio es
  quien lo obligó.** El guardián evaluaba cada par en claro y en oscuro, lo cual
  es correcto mientras una combinación exista en los dos; con una superficie que
  cambia de familia al voltear, hace fallar la corrida por combinaciones que no
  se pintan en ninguna pantalla — `sobre-acento` sobre `primario-suave` da 1,23:1
  en oscuro, donde la baldosa es ámbar. Ahora un par lleva un quinto campo
  opcional, `"claro"` u `"oscuro"`, y **sin él se sigue comprobando en los dos**,
  que es lo normal y lo que atrapa a un token que cambia de familia sin avisar.

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
  con un cuarto paso — que es exactamente lo que se intentó aquí y se deshizo.
