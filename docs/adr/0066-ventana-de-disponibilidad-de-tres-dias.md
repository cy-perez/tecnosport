# ADR-0066 — Un producto de proveedor se oculta a los tres días sin verse

**Fecha:** 2026-09-30
**Estado:** aceptado.

## Contexto

Desde el 30 de septiembre de 2026 el catálogo tiene productos que no carga una
persona: los deja la ingesta de los chats de WhatsApp de los proveedores de
bolsos y ropa (`docs/02-modelo-datos.md`, "Proveedores por WhatsApp"). Esos
proveedores no avisan cuando algo se les acaba. Lo que hacen es **dejar de
publicarlo**: un bolso que se anunció el lunes y no vuelve a aparecer en el chat
es, casi siempre, un bolso que ya no tienen. A veces escriben "agotado", y eso la
ingesta lo entiende y lo marca; pero la señal fiable es el silencio.

Vender lo que el proveedor ya no tiene es el peor resultado posible: el pedido
se cobra, la guía se emite y a los dos días hay que cancelar y devolver el
dinero. El sitio tiene que dejar de ofrecer el producto **antes** de que alguien
lo compre, y nadie va a estar mirando cada chat para retirarlos a mano.

Las alternativas que se miraron:

- **No hacer nada y confiar en el "agotado" escrito.** Se descartó porque no es
  una costumbre: en el anexo de veinticinco mensajes con el que se calibró la
  extracción aparece una sola vez.
- **Borrar el producto cuando venza.** Se descartó porque el proveedor vuelve a
  publicar lo mismo semanas después, y borrarlo obliga a crearlo otra vez, con
  otra URL, perdiendo la ficha, las fotos ya copiadas y cualquier enlace que se
  haya compartido.
- **Marcarlo agotado en el inventario.** Se descartó porque el inventario de un
  producto de proveedor es una intención, no un conteo —se registra una
  existencia inicial al aprobar y no hay quien la cuente después— y porque
  agotado se muestra en la vitrina; lo que se quiere es que **no se liste**.

## Decisión

**Un producto de origen proveedor que no ha aparecido en ningún mensaje durante
más de la ventana de disponibilidad pasa a `OCULTO_POR_VENCIMIENTO`.** Deja de
listarse en la vitrina y de poderse comprar; su ficha sigue respondiendo, con el
producto marcado como no disponible, para que un enlace compartido no dé 404.

La ventana es **tres días** (`PROVEEDORES_VENTANA_DISPONIBILIDAD=P3D`) y es
configurable por variable de entorno. Tres porque es la cadencia real de estos
proveedores: publican a diario o cada dos días, y un fin de semana sin
publicar no significa nada. No es un dato medido, es el que se acordó con el
negocio; si la cadencia cambia, cambia la variable y no el código.

Tres consecuencias que hay que tener presentes:

- **Se oculta, no se borra.** El siguiente mensaje que traiga ese mismo producto
  —por huella o por parecido visual, `docs/02-modelo-datos.md`— lo reactiva:
  vuelve a `DISPONIBLE` con la fecha de "visto por última vez" al día.
- **Un producto `MANUAL` nunca se toca.** La tarea consulta solo los de origen
  proveedor, y el agregado vuelve a exigirlo. Los que carga una persona los
  retira una persona.
- **El "agotado" escrito es otra cosa.** Cuando el proveedor lo dice, el
  producto pasa a `AGOTADO_POR_PROVEEDOR` en el momento, sin esperar la ventana.
  Es el mismo efecto en la vitrina con otra causa, y se guarda distinta para
  poder contarlas por separado.

La tarea corre dentro de la aplicación con `@Scheduled`, cada veinticuatro
horas, como las otras once (`docs/07-infra-gcp.md`). Es idempotente: correrla
dos veces seguidas oculta una vez. Y se prueba **sin esperar**: el caso de uso
recibe el reloj por el puerto `Reloj`, la prueba lo adelanta dos minutos con una
ventana de uno, y la variable de entorno permite reproducir lo mismo contra
`bootRun` con `PT1M`.

## Consecuencias

- Un proveedor que se va de vacaciones una semana ve su catálogo desaparecer de
  la vitrina el cuarto día. Es lo que se quiere: no se vende lo que no se puede
  confirmar. Cuando vuelva a publicar, vuelve solo.
- La ventana es una sola para todos los proveedores. Si aparece uno con otra
  cadencia, hará falta moverla al proveedor; hoy no hay motivo.
- En el ambiente de dev, con la CPU asignada solo durante la petición, la tarea
  solo avanza mientras alguien usa el sitio. Es la limitación de todas las
  tareas programadas en dev, ya documentada, y en producción no aplica.
