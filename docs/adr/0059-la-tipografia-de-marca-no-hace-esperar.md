# ADR-0059 — La tipografía de marca no hace esperar ni cambia a mitad de lectura

**Fecha:** 2026-09-23
**Estado:** aceptado, medido y **confirmado con el par** el 23 de septiembre de 2026.
`font-display` pasa de `swap` a `optional` en las cuatro caras. El efecto sobre estilo y layout
**se repite en las dos parejas** de `npm run pareja` —cuatro corridas en orden alternado— en
legales y en la ficha; en la portada no se repite. La primera pareja suelta decía más de lo que
hay: ver "Lo que se midió" abajo.

## Contexto

Las cuatro tipografías del sitio pesan 273 KiB, están autoalojadas y ya van recortadas al alfabeto
latino (`docs/04-ui-marca.md`). Llevaban `font-display: swap` desde que existen, que es la elección
por omisión razonable: el texto se pinta con la fuente de respaldo en vez de quedar invisible, y
cuando llega la de marca se cambia.

**Lo que cuesta ese cambio estaba sin medir, y se midió.** En la traza de la pantalla de legales,
con `swap`:

- Archivo termina de bajar en `t+445,7` y en `t+447,4` hay un `Layout` de **10 ms** con 278 de 285
  objetos sucios.
- IBM Plex Sans termina en `t+460,6` y en `t+461,4` hay otro de **43 ms**, 276 de 285.

Son dos relayouts **de la página entera**, uno por archivo, 1 ms después de que aterriza cada uno.
53 ms de una pantalla que no tiene ni una imagen. No es un caso raro: es lo que `swap` hace por
definición, en cada pantalla y en cada visita sin caché.

Dos cosas más aparecieron mirando eso, y las dos importan para entender la decisión:

- **No hay ni un `@font-face` en el CSS crítico que el SSR pone en línea**, así que las tipografías
  no se piden hasta que aplica la hoja diferida, hacia los 390 ms. Descubrirlas antes no es una
  mejora: metería 273 KiB por delante del primer pintado.
- **El arnés tiene dos modos por culpa de esos 273 KiB** —66-72 y 89-90 de rendimiento, según si
  alcanzan a bajar antes del primer pintado—. Eso es un artefacto de la medición y está tratado
  aparte; `optional` no lo arregla ni se pretendía que lo hiciera.

## Decisión

**`font-display: optional` en las cuatro caras.**

El navegador da una ventana corta. Si la tipografía de marca llegó, se usa desde el primer pintado.
Si no llegó, **esa visita se queda con la de respaldo y no cambia a mitad de lectura**; la de marca
entra desde la caché en la visita siguiente.

Lo que eso compra, en orden de importancia para quien compra desde un teléfono:

1. **La página no se reacomoda debajo del dedo.** Con `swap`, el texto ya leído cambia de forma y
   de posición a los 400-700 ms. Con `optional` eso no ocurre nunca.
2. **El relayout por archivo desaparece** en la única conexión donde de verdad pesa: la lenta.
3. Y de paso, el texto nunca queda invisible esperando: eso `swap` ya lo garantizaba y no se pierde.

**Lo que cuesta, dicho sin adorno: en una primera visita lenta el sitio no se ve con Archivo.** Se
ve con la familia de respaldo, que para titulares es Arial Narrow o Helvetica Neue. Es una decisión
de marca y la tomó el dueño del negocio el 23 de septiembre de 2026, con la contrapartida delante.

## Lo que se midió, y lo que la medición no puede decir

Primero, una sola pareja de corridas en la misma sesión: estilo y layout de 731 a 461 ms en
legales y de 546 a 396 en la ficha. El arnés no dice "sí" a un tiempo con una pareja, y hace bien:
**repetido el par en orden alternado, el efecto sigue ahí pero es más chico.**

| estilo y layout | pareja 1 | pareja 2 | veredicto del arnés |
|---|---|---|---|
| legales | 648 → 475 (**−173**) | 681 → 452 (**−229**) | consistente: 2/2 en el mismo sentido |
| ficha | 543 → 430 (**−113**) | 513 → 412 (**−101**) | consistente: 2/2 en el mismo sentido |
| portada | 669 → 635 (−34) | 615 → 654 (**+39**) | NO se repite |

Son cifras del simulador, que multiplica el trabajo de CPU por 4: en el reloj, entre 43 y 57 ms en
legales y entre 25 y 28 en la ficha. El peso y los bytes de tipografía no se mueven en ninguna
pareja, como tenía que ser.

**En la portada no se repite, y no es por el artefacto**: las dos mitades de las dos parejas se
midieron con las cuatro tipografías dentro del FCP, o sea en el mismo modo, y aun así el signo se
invierte entre una pareja y la otra. Lo que se puede afirmar de la portada es que este cambio no le
hace nada medible; el suyo es otro problema.

Ninguna otra métrica sobrevive al par. En legales el FCP, el LCP y el TBT repiten signo a favor,
pero los tres por debajo del piso que la herramienta mide para sí misma; en la ficha el rendimiento
repite **−1 y −2 puntos** —a la contra, y también bajo el piso de 5— y el FCP **+4 y +7 ms**, bajo
el de 10. Nada de eso se afirma: lo único que sostiene el par es el estilo y layout.

**Y una cosa que la traza dice y conviene no tapar: sobre `localhost` el relayout no desaparece,
encoge** —de 10+43 ms a 7+7 en legales—. No podía desaparecer ahí: en localhost las tipografías
llegan dentro de la ventana, así que se aplican igual y aplicarlas cuesta un relayout. Donde
desaparece del todo es donde no llegan a tiempo, y eso es precisamente la conexión que el arnés no
reproduce y el comprador sí tiene. La mejora medida en el arnés es, entonces, **el piso** de la que
recibe una persona con mala señal, no el techo.

## Alternativas descartadas

**Dejar `swap`.** Es lo que había. Cuesta un relayout de la página entera por archivo de tipografía
y un cambio visible a mitad de lectura, siempre, en toda visita sin caché.

**Precargar las tipografías** con `<link rel="preload">` para que lleguen antes del primer pintado.
Suena a mejora y es lo contrario: pone 273 KiB por delante de lo que hay que pintar. En el modelo
del arnés empeora el FCP simulado en torno a 1,4 s, y para una persona con mala señal el efecto es
el mismo pero de verdad.

**Adelgazarlas fijando las variables a pesos estáticos.** Medido antes de proponerlo, y **engorda**:
Archivo pasa de 138,6 KiB a 167,6 con tres pesos, e IBM Plex Sans de 91,9 a 147,8. La variable ya es
la forma barata en cuanto se usa más de un peso, y `font-display` se usa en 400, 500 y 700.

**`font-display: block`.** Deja el texto invisible hasta tres segundos. Es peor que todo lo demás.

**Declarar métricas de respaldo** (`size-adjust`, `ascent-override`) en un `@font-face` de la
familia de respaldo, para que las dos midan parecido. No se hizo, y no es lo mismo que esto: con
`optional` ya no hay salto **dentro** de una visita. Lo que quedaría por afinar es que la visita con
respaldo y la visita con marca se parezcan entre sí. Si se nota, esa es la salida.

## Consecuencias

- **`fuentes.css` es generado y se regeneró.** La línea se cambió en
  `packages/marca/generador/fuentes.py`, no en el CSS (regla 3 del `CLAUDE.md`).
- **Para poder regenerarlo sin red hubo que añadir una bandera**, `--rehacer-css`: reconstruye
  `fuentes.css` leyendo el propio `fuentes.css` —familia, archivo y peso de cada `@font-face`— y no
  toca un solo `woff2`. Sin ella, cambiar una línea del `@font-face` obligaba a volver a bajar las
  familias de Google, que arrastra la versión de hoy de cada una: otra cosa, y se mezcla mal en el
  mismo commit. `--desde-local` no servía, porque dice en voz alta que el CSS no cambia.
- El generador escribe ahora con LF explícito. `Path.write_text` traduce los saltos de línea en
  Windows, y un archivo generado que entra al repositorio con los finales cambiados de golpe es un
  diff de todas sus líneas por un cambio de una.
- La copia de `apps/web/src/assets/marca/fuentes.css` la escribe `copiar-marca.mjs` en cada build:
  no se edita ahí tampoco.
- `npm run kit` sigue en verde: el kit se regenera igual y las tipografías guardadas siguen
  escribiendo lo que el sitio escribe.
