# ADR-0062 — El menú vive en un riel lateral que se despliega al acercar el puntero

**Fecha:** 24 de septiembre de 2026
**Estado:** aceptada

## Contexto

"Catálogo" y "Panel" eran dos enlaces sueltos en el encabezado. Con el árbol de categorías
(`ADR-0061`) hay treinta destinos que ofrecer, y treinta no caben en una barra horizontal.

La referencia pedida es el menú de `angular-demo.tailadmin.com`: un riel de iconos pegado a la
izquierda que se ensancha al acercar el puntero y se recoge al retirarlo, con las ramas plegables
dentro.

## Decisión

Un componente `layout/menu-lateral/`, visible **desde el primer punto de quiebre**, con el gesto de
la referencia: 72 px recogido, 288 px desplegado, 300 ms y `cubic-bezier(.4,0,.2,1)` —los mismos que
se midieron en el sitio de referencia, ahora en `tokens.json` como `--ancho-menu-riel`,
`--ancho-menu-lateral` y `--mov-panel`—.

Dentro, dos grupos plegables —Catálogo y Panel— y, bajo Catálogo, las cuatro líneas y su árbol. Cada
grupo es un *disclosure* con `aria-expanded` y `aria-controls`, y pliega animando
`grid-template-rows` de `0fr` a `1fr`: la rejilla resuelve sola la altura del contenido, así que la
transición es exacta con dos categorías o con trece, sin adivinar un `max-height`.

### Fijarlo es otra cosa que desplegarlo

La referencia también trae un botón que deja el panel abierto, y aquí son **dos estados distintos**,
no uno con dos disparadores:

- **Desplegado** es el gesto —el puntero encima, o el foco dentro— y se apaga solo. El panel se
  pinta **encima** del contenido.
- **Fijado** es una decisión, y no se deshace hasta que quien la tomó la deshaga. Ahí el contenido
  se corre de verdad y le hace sitio.

La distinción no es cosmética: **un panel fijado que siguiera pintándose encima taparía justo lo que
se está mirando**, que es lo contrario de para lo que se fija. Por eso `app-root` reserva 72 px
normalmente y 288 con el menú fijado, con la misma transición.

El botón va **dentro del menú** y no en el encabezado, que es donde lo pone la referencia: en el
encabezado sería un control que en el teléfono no significa nada —ahí no hay menú lateral que
fijar—, y aquí desaparece con él sin condiciones extra. Lleva `aria-pressed` y no `aria-expanded`:
no abre una región, cambia un modo.

El estado vive en `MenuLateralStore`, de raíz, porque lo miran tres elementos sin relación
padre-hijo: el menú, el encabezado y `app-root`. Es la excepción que `apps/web/CLAUDE.md` documenta
para `CarritoStore`. **No se persiste**: sobrevive a toda la navegación de la sesión y se olvida al
recargar. Guardarlo exigiría un puerto, un proveedor y una guarda de plataforma para devolver el
recuerdo de un clic.

### El logo no se deja tapar

El panel desplegado crece 216 px sobre el contenido, y debajo queda el logo — el primer elemento del
encabezado, y el que dice en qué sitio estás. En vez de dejar que lo tape, **se corre esos mismos
216 px**, con la misma duración y la misma curva, para que las dos cosas se lean como un solo
movimiento. Es `translate` y no un margen: la caja del ancla se queda donde está y solo se mueve lo
pintado, así que el resto de la barra no se entera.

Solo cuando el panel está encima. Con el menú fijado no hace falta: ahí el hueco lo reserva
`app-root` y el encabezado entero ya se corrió.

La distancia sale de `--spacing-menu-asoma`, que es `calc(--ancho-menu-lateral - --ancho-menu-riel)`
y **no** un tercer número en `tokens.json`: 216 es una resta, no una decisión de diseño
independiente, y escribirla a mano garantizaría que el día que uno de los dos cambie el logo se
corra lo que ya no mide.

### Tres cosas que **no** se copiaron

**1. La página no se desplaza al desplegar.** En la referencia, el contenido se corre 220 px cada
vez que el puntero roza el borde izquierdo. En una vitrina eso mueve las tarjetas de producto bajo
el cursor de quien iba a hacer clic. Aquí el gesto pinta el panel **encima**, con sombra, y el
contenido solo se mueve cuando alguien lo decide con el botón de fijar. El gesto se siente igual y
nada salta.

**2. Se abre también con el foco del teclado.** Un desplegable que solo responde al ratón no lo
puede usar quien navega con Tab. Por eso el estado es una señal y no un `:hover` de CSS: `focusin`
la enciende igual que `mouseenter`, y `focusout` solo la apaga cuando el foco se fue de verdad —
saltar de un control a otro **de dentro** no recoge el panel, o el recorrido con Tab se cerraría
solo en el segundo salto.

**3. En el teléfono no existe.** No hay puntero que acercar, así que el árbol se queda donde ya
estaba: el panel desplegable del encabezado, que se abre con un botón. Repetirlo aquí sería un menú
que nadie puede abrir. Y es `hidden desde-movil:contents` —una media query— y no un `@if` con el
ancho de la ventana: el ancho no se conoce en el servidor, y un `@if` dejaría el HTML servido y el
hidratado sin coincidir.

### Detalles que costaron

- **`display: contents` y no `block`.** `app-root` es una rejilla de tres filas —encabezado,
  contenido, pie— y un cuarto hijo en el flujo se lleva una cuarta fila implícita, que descuadra el
  pie. Con `contents` el host no genera caja y el único hijo que queda es el `<nav>`, que es `fixed`
  y por tanto está fuera del flujo.
- **El puerto de categorías subió a `app.config.ts`.** El menú se pinta en `app.html`, **fuera** del
  `<router-outlet>`, así que el inyector de una ruta no lo alcanza: provisto solo en la ruta del
  catálogo, el menú se caía con `NullInjectorError` en toda pantalla que no fuera la vitrina. Es la
  implementación **pública**: el menú lo ve cualquiera, con sesión o sin ella.
- **Llave de caché propia**, `['catalogo','menu']`. La comparten la vitrina y el panel con
  adaptadores distintos, así que reusarla dejaría el contenido del menú a merced de quién la pidiera
  primero — el mismo defecto que `usarMarcasAdmin` ya documentaba. El CRUD de categorías la invalida
  explícitamente: una categoría nueva que no aparece en el menú hasta recargar es una categoría que
  nadie encuentra.
- **Los grupos abiertos no se cierran al recoger el panel.** Quien vuelve a acercar el puntero
  espera encontrar la rama donde la dejó. Es estado de navegación, no del gesto.
- **Con "reducir movimiento" el ancho cambia de golpe**, no en una versión acelerada del mismo
  barrido. Aquí pesa más que en el panel móvil porque la distancia es de 216 px.

### El encabezado pierde su navegación de escritorio

"Catálogo" y "Panel" se fueron al menú. Tener el mismo destino en dos sitios de la misma pantalla no
es redundancia inofensiva: son dos landmarks de navegación que dicen cosas distintas. En móvil los
enlaces siguen en el panel desplegable, de donde nunca se fueron.

## Consecuencias

- Hay dos `<nav>` en la página, y cada uno tiene que decir cuál es: el menú lleva
  `aria-label="Menú del sitio"`.
- El contenido de todas las pantallas se corre 72 px a la izquierda desde el primer punto de
  quiebre, y 288 con el menú fijado. El ancho máximo de contenido (1200 px) no cambia.
- Hay una petición más en el arranque —el árbol de categorías— en toda pantalla, no solo en la
  vitrina. Se paga con `staleTime` de cinco minutos: el árbol no se mueve solo, y quien lo mueve
  invalida la llave.
