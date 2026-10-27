# ADR-0094: la recogida se entrega en el punto y se puede pagar al recoger

Fecha: 2026-10-26
Estado: aceptada. Cierra «Lo que falta para encenderla» de `ADR-0093`: la recogida queda
encendida en dev (`RETIRO_EN_PUNTO_HABILITADO`) con los términos `2026-10-26.1`.

## Contexto

`ADR-0093` dejó construida la regla de quién puede recoger —todo el carrito de proveedores
locales— y la recogida apagada, porque el sistema no sabía entregar en el punto: un pedido de
recogida solo llegaba a `ENTREGADO` pasando por `DESPACHADO`, que exige una guía y le escribe al
comprador que su paquete salió con una transportadora. El negocio quería además que quien recoge
pudiera pagar allí.

El 26 de octubre de 2026 el negocio fijó los tres datos que faltaban: el pedido listo se guarda
**48 horas**; en el punto se recibe **efectivo o transferencia**; y se entrega a quien muestre
**el número de pedido**. Y aprobó dos propuestas: las 48 horas corren desde el aviso de que está
listo, no desde la compra, y el pago en el punto se verifica igual que la contraentrega.

## Decisión

1. **Un estado propio, `LISTO_PARA_RECOGER`.** `EN_PREPARACION → LISTO_PARA_RECOGER → ENTREGADO`,
   o `→ CANCELADO`. `MarcarListoParaRecoger` lo pone y le escribe al comprador la dirección, el
   horario, su número de pedido, cuánto paga si paga en el punto y hasta cuándo se le guarda.
   `DespacharPedido` rechaza una recogida (`ENTREGA_NO_APLICA`, 409), y `MarcarEntregado` rechaza
   una recogida lista: por ahí bastaría un clic para saltarse el número.
2. **El número lo compara el servidor.** `EntregarEnPunto` recibe el número que mostró quien vino
   y lo compara sin mayúsculas ni espacios; si no coincide responde `NUMERO_DE_PEDIDO_NO_COINCIDE`
   (422) y no entrega. La fecha del registro `ENTREGADO` es la de la entrega en el punto, y de ella
   corren el retracto y la garantía (`Pedido.fechaDeEntrega()`).
3. **`MetodoPago.PAGO_EN_PUNTO`**, solo con `RETIRO_EN_PUNTO`: `MetodosDePagoDisponibles` no lo
   ofrece a domicilio, `CrearPedido` lo rechaza antes de reservar (`PAGO_EN_PUNTO_NO_DISPONIBLE`) y
   el agregado también. Al entregar se registra `MedioDePagoEnPunto` —efectivo o transferencia— en
   el motivo del historial, y se confirman las reservas, como hace la contraentrega al entregar.
   El dinero entra al entregar: `dineroRecibido()` lo cuenta desde `ENTREGADO`.
4. **Entra por `CONFIRMADO_CONTRAENTREGA`.** Se paga al recibir, igual que la contraentrega
   (`MetodoPago.seCobraAlRecibir()`): se confirma sin pago, su reserva no vence, el plazo de
   entrega corre desde la compra y la verificación de 48 horas de `VencerContraentregaSinVerificar`
   lo cancela si nadie lo confirma, con su propio texto. Reusar el estado evitó un segundo camino
   paralelo con la misma verificación; el precio es un nombre que ya no describe todo lo que
   abarca, y el panel y la pantalla del pedido lo rotulan según el método.
5. **Las 48 horas desde el aviso** (`Pedido.PLAZO_PARA_RECOGER`, `Pedido.recogidaVenceEn()`). Las
   cuenta el historial, no una columna. `VencerRecogidasSinRetirar` cancela solas las que se pagan
   en el punto y libera su mercancía: no se cobró nada. **Las pagadas no se cancelan solas**,
   porque `CancelarPedido` exige la constancia del reintegro y el reintegro lo hace una persona:
   el panel marca la fila «Recogida vencida» y se cancela con el motivo nuevo
   `MotivoCancelacion.NO_RECOGIDO` (y `MotivoReintegro.NO_RECOGIDO`), que solo se acepta con el
   plazo vencido.

## Consecuencias

- Las 48 horas son una constante del dominio y no configuración: es un dato del negocio que los
  términos publican, y cambiarlo exige cambiar el texto.
- El vigilante del plazo legal de entrega no mira `LISTO_PARA_RECOGER`. Un pedido listo dura como
  mucho 48 horas antes de cancelarse; si el plazo se cumple al dejarlo listo o al entregarlo es una
  de las preguntas al abogado (`docs/14` punto 11).
- Entregar a quien muestre el número no pide documento. Es la decisión del negocio y también está
  en `docs/14`.
- La recogida pagada y vencida depende de que alguien mire el panel: no hay aviso al negocio
  aparte. Si se acumulan, el aviso es el siguiente paso.
- La bandera se enciende con `terraform apply` sobre `infra/envs/dev`; el despliegue de la API no
  aplica Terraform.
