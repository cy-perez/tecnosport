# ADR-0086 — El color de tecnología se confirma antes de venderse

**Fecha:** 2026-10-25
**Estado:** aceptado. Reemplaza el punto 6 de ADR-0075 («los colores arrancan con
lo que sugiere la lista») y la regla 13 de la skill `listas-de-proveedor` en lo que
toca a vender: la paleta se sigue publicando completa, pero como información.

## Contexto

La lista del proveedor de tecnología casi nunca dice colores: en una de 121
productos, dos líneas traían emojis. Desde el 22 de octubre la skill sugería la
paleta oficial completa cuando la lista no marcaba ninguno, y la importación le
daba a cada color 2 unidades libres. Para quien compraba, un morado que nadie
había confirmado tenía la misma existencia que uno que la lista marcó con 🟪. Si
el proveedor no lo tenía, la única salida era cancelar con reintegro después de
cobrar.

El negocio decidió el 24 de octubre de 2026 que el color se confirma con el
proveedor antes de la compra, por WhatsApp, y que la compra se habilita con un
enlace personal. Con esto decidió también la vigencia del enlace (24 horas), que la
unidad se aparta mientras está vigente, y que no se pregunta lo que ya se sabe: el
color que la lista marcó y el modelo de un solo color.

## Decisión

1. **La paleta vive en el producto** (`producto.paleta`, V117). La aprueba el
   borrador, la llena la lista si falta, y el panel la corrige
   (`DefinirPaletaDeTecnologia`). La ficha la dice al pie de las fotos: «Colores
   del modelo según el fabricante», no «existentes», porque no son los que hay.
2. **Cada color de la paleta se puede elegir.** Definir la paleta desde el panel
   completa las variantes que falten en cada configuración que ya se vende, con el
   precio y el costo de sus hermanas, sin existencia.
3. **La variante sabe si su color está por confirmar** (`variante.color_por_confirmar`).
   La lista lo recalcula cada vez (`ConfirmacionDeColor.requiere`): no está por
   confirmar si la lista de hoy lo marca, o si la paleta tiene un solo color. La
   exportación de la skill manda aparte los colores marcados (`coloresMarcados`),
   porque los sugeridos ya no distinguen un emoji de la paleta entera; sin ese
   campo, unos sugeridos que cubren la paleta se leen como «no marcó ninguno».
4. **Un color por confirmar se pregunta por WhatsApp.** La ficha cambia el botón
   de compra por el chat con el mensaje ya escrito. Lo que decide es el servidor:
   `CrearPedido` rechaza la línea sin enlace vigente (`ColorPorConfirmarException`,
   409).
5. **El enlace de compra es un agregado propio** (`EnlaceDeCompra`, tabla
   `enlace_de_compra`): de una variante, de un solo uso, vence a las
   `ENLACE_COMPRA_HORAS_VIGENCIA` horas. Al generarlo se reserva una unidad con la
   misma vigencia; si el libro no tiene unidad libre, entra una, porque el
   proveedor acaba de confirmarla (`entrada_propia`). Al crear el pedido, la reserva
   del enlace se libera y se toma la del pedido en el mismo bloqueo, y esa reserva
   no vence antes que el enlace: con los 30 minutos del pago en línea, un PSE
   abandonado dejaba el enlace usado y la unidad suelta. Anularlo libera la unidad
   y, si entró con el enlace, la saca. El enlace se bloquea antes que el libro,
   en `CrearPedido` y en `AnularEnlaceDeCompra`, y se busca por token **y**
   variante: un token de otra variante no bloquea nada (revisión del 25/10/2026).
   Lo que no se revierte es la unidad de un enlace que **vence** sin usarse: queda
   libre hasta la lista siguiente, que la ajusta.
6. **El token se guarda en claro**, al revés que los de la cuenta (ADR-0015): vale
   24 horas, una compra, deja pagar un equipo al precio publicado, y el panel lo
   necesita entero para volver a copiarlo. Son 32 bytes aleatorios: no se adivina.
7. **La línea del carrito recuerda su enlace** (`linea_carrito.enlace_compra`) y
   el checkout lo devuelve al crear el pedido. El carrito no lo valida, igual que
   no valida existencias.

## Consecuencias

- Los términos (numeral 5) y la política de datos (numerales 5, 7, 8 y 9) dicen el
  camino y el paso por WhatsApp. La versión legal sube a `2026-10-25.1` porque se
  agregó una finalidad. Las preguntas que quedan están en `docs/14`, punto 9.
- Que el proveedor también aparte la unidad mientras el enlace está vigente es un
  supuesto del negocio, no algo que el sistema pueda garantizar.
- Hasta la primera lista después del despliegue, la migración deja por confirmar
  todas las variantes de tecnología salvo las de un modelo de un solo color: sin
  saber qué dijo la última lista, es lo prudente.
- Los productos ya publicados reciben su paleta del borrador aprobado; las
  variantes de los colores que falten nacen cuando alguien guarda la paleta en el
  panel. Para los modelos sin paleta, la skill la busca en el sitio de la marca y
  en las tiendas grandes de Colombia (decisión del 24/10/2026), y se carga por el
  mismo endpoint.
