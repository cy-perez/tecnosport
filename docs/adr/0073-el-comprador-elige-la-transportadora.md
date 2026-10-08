# ADR-0073 — El comprador elige la transportadora

**Fecha:** 2026-10-08
**Estado:** aceptado. Reemplaza el punto de `ADR-0021` "el servidor elige la
tarifa, no el comprador: la más económica".

## Contexto

`ADR-0021` decidió que el comprador no eligiera: el servidor tomaba la tarifa
más económica de las que Skydropx devolvía. Las razones eran un paso menos en el
checkout, una decisión menos para quien compra una camiseta y un valor menos que
vigilar desde el cliente. El propio ADR dejó escrito que se reabriera el día que
se quisiera ofrecer la elección.

El 8 de octubre de 2026 el negocio pidió ofrecerla. Al pulsar «Continuar» en el
resumen, el comprador elige con qué transportadora va su pedido, viendo el costo
de cada una. Las razones de `ADR-0021` siguen siendo ciertas; lo que cambió es que
el negocio prefiere dar la opción.

## Decisión

1. **Se elige una transportadora, no un servicio.** La cotización devuelve una
   opción por transportadora, con su tarifa más económica, de la más barata a la
   más cara (`TarifaEnvio.unaPorTransportadora`). Envía cotiza dos servicios y
   ofrecer los dos sería pedir que se distinga algo que la plataforma no explica.
2. **Del cliente viaja el nombre, nunca el costo ni el `rate_id`.** La regla 7 no
   cambia. `CrearPedido` vuelve a cotizar, toma la tarifa de esa transportadora y
   la congela. Si ya no cotiza, responde **409 `TRANSPORTADORA_NO_DISPONIBLE`**
   antes de reservar, y el checkout pide elegir otra. No se cambia por otra en
   silencio: el comprador eligió una.
3. **Sin elección, la más económica**, como antes. Un cliente viejo sigue
   funcionando.
4. **La contraentrega se ofrece con la elegida o no se ofrece.** Si la elegida no
   recauda en ese destino, ofrecerla con otra cambiaría el flete que el comprador
   ya vio y aceptó.
5. **La elección se ve donde se usa.** Se muestra en la confirmación ("Con
   Servientrega", con su costo), en el seguimiento y en el comprobante por correo.
   También en el panel, donde la primera guía del formulario de despacho ya trae
   esa transportadora escrita: las guías se crean a mano (`ADR-0071`) y quien
   despacha tiene que saber con cuál.
6. **La emisión automática, cuando se encienda, intenta primero con la elegida.**
   Si esa ya no cotiza o ya falló para ese pedido, cae en la más económica: un
   pedido pagado no se queda sin despachar.

## La interfaz

Un popover anclado a «Continuar», con el popover de Spartan Brain, el mismo
proveedor que el diálogo. Trae el foco al abrir, lo devuelve al cerrar y se cierra
con Escape o al pulsar fuera.

- **Botones:** variante `baldosa`, la de los métodos de pago, con el logo de la
  transportadora a la izquierda.
- **Costo:** en negrita y del mismo tamaño de letra, debajo de cada botón y
  enlazado con `aria-describedby`.
- **Tamaño:** columnas iguales, así que todos los botones toman el ancho del más
  grande.

Los logos los entregó el dueño del negocio. Se limpiaron a
`apps/web/logos-transportadora/` y los genera `npm run logos-pago`, monocromos
como los de pago. Una transportadora sin logo se pinta con el camión genérico y
su nombre.

## Consecuencias

- El checkout tiene un paso más, que es lo que `ADR-0021` quería evitar.
- **El costo que se muestra en el resumen sigue siendo el más económico**, porque
  la elección se hace al continuar. La confirmación muestra el de la elegida
  antes de pagar: el desglose del artículo 50 de la Ley 1480 de 2011 se cumple
  en la pantalla donde se finaliza.
- **Con contraentrega, lo que se cobra puede no ser lo que se mostró.** La
  confirmación muestra la tarifa más económica **sin** recaudo de la elegida, y el
  pedido congela la más económica **con** recaudo de esa misma transportadora.
  Hoy las tarifas que sobreviven con recaudo cuestan lo mismo (`docs/13`), pero si
  el servicio más barato de una transportadora se cae con recaudo y sobrevive uno
  más caro, se cobraría más de lo mostrado. El problema ya existía con la más
  económica global; aquí se nota más, porque la pantalla dice "Con X" y un costo.
  Es un pendiente: la confirmación debería pedir la cotización con recaudo cuando
  el método es contraentrega.
- **Si la emisión automática cae en otra transportadora**, el seguimiento y el
  comprobante siguen nombrando la elegida hasta el despacho; desde ahí, el bloque
  de la guía dice la real. Hoy no pasa, porque la emisión automática está apagada
  (`ADR-0071`).
- **Tras un 409 de transportadora, la confirmación olvida la cotización
  guardada**, para que la que dejó de cotizar no se siga mostrando ni se vuelva a
  ofrecer durante el minuto de caché.
- **Con contraentrega y la elegida desaparecida de la cotización entera**, el
  comprador ve "contraentrega no disponible" y, si cambia a pago en línea, el 409
  de transportadora. Son dos rechazos para el mismo problema, y se aceptan porque
  distinguirlos exigiría una segunda cotización.
- El seguimiento público suma un campo, el nombre de la transportadora. No es un
  costo ni un margen, es un dato del comprador como su método de pago. La prueba
  que cuenta los campos se actualizó diciéndolo.
