# Pagos y envíos

## Métodos de pago

| Método | Proveedor | Cuándo entra el dinero |
|---|---|---|
| Tarjeta débito y crédito | Wompi | Al aprobar |
| PSE | Wompi | Al aprobar |
| Nequi | Wompi | Al aprobar |
| Bancolombia a la mano y botón Bancolombia | Wompi | Al aprobar |
| Addi, pago a cuotas | Wompi | Al aprobar el crédito |
| Transferencia manual | Ninguno | Al conciliar el comprobante |
| **Contraentrega** | Transportadora con recaudo | Días después de la entrega |

## Wompi

- **La aprobación no se decide con lo que trae el navegador.** El parámetro de
  retorno solo sirve para mostrar una pantalla. La verdad es el webhook firmado
  más una consulta directa a la transacción.
- **La firma del webhook se verifica siempre.** Un evento sin firma válida se
  descarta y se registra.
- **Firma de integridad** al crear la transacción, sobre referencia, monto y
  moneda, para que nadie altere el precio en el camino.
- Referencia única e idempotente por intento de pago.
- Ningún dato de tarjeta toca el servidor propio. Eso mantiene el alcance de PCI
  en el mínimo, y no hay que arruinarlo agregando un campo de tarjeta por
  comodidad.
- Un trabajo programado concilia los pagos que quedaron pendientes y nunca
  recibieron webhook. Los webhooks se pierden; el dinero no puede perderse con
  ellos.
- Ambiente de pruebas hasta que los recorridos completos pasen. Las llaves de
  producción entran solo por Secret Manager.

## Contraentrega

Es el método con más riesgo operativo del sistema, y el diseño lo refleja.

**Disponibilidad.** No se ofrece siempre. El servidor decide si aparece, según:

- **Cobertura de la ciudad de destino.** Tabla propia, alimentada por lo que
  cubre la transportadora con recaudo.
- **Monto máximo.** Configurable en `CONTRAENTREGA_MONTO_MAXIMO`. Un celular de
  cuatro millones contra entrega es una pérdida esperando ocurrir.
- **Categorías excluidas.** Configurable. La recomendación de arranque es excluir
  celulares por encima del monto máximo y aceptar el resto.
- **Historial del comprador.** Si un correo o un teléfono ya rechazó pedidos en la
  entrega, no se le ofrece más. Se registra, no se olvida.

Estas reglas son de negocio y viven en el dominio, en un caso de uso
`MetodosDePagoDisponibles`. **El frontend no decide nada de esto**: pide la lista
al servidor y muestra lo que le devuelvan.

**Flujo.**

1. El cliente elige contraentrega y confirma el pedido. No se cobra nada.
2. El pedido queda en `CONFIRMADO_CONTRAENTREGA` y **reserva inventario sin
   vencimiento por tiempo**.
3. Verificación antes de despachar: contacto por WhatsApp o llamada. Queda
   registrado quién verificó y cuándo. Un pedido contraentrega no verificado no
   se despacha.
4. Se despacha con recaudo. La guía lleva el valor a cobrar.
5. Entregado: pasa a `RECAUDO_PENDIENTE`. Rechazado: pasa a
   `RECHAZADO_EN_ENTREGA`, se libera el inventario y se registra el motivo.
6. La transportadora consigna. Se concilia y pasa a `RECAUDO_CONCILIADO`.

**El recaudo pendiente es visible en el panel.** Un pedido entregado hace veinte
días sin conciliar es plata en la calle, y el sistema tiene que gritarlo, no
esconderlo en un reporte.

**Costo.** El recaudo tiene una comisión de la transportadora. Se registra en el
pedido como costo real, separado del flete, para que el margen del pedido sea
verdadero y no una estimación optimista.

## Transferencia manual

Se muestran los datos de la cuenta y una referencia única. El pedido queda en
`PAGO_PENDIENTE` con reserva de 24 horas, no de 30 minutos. El administrador
concilia el comprobante en el panel. Vencido el plazo sin comprobante, se libera.

## Envío

No se cotiza. El precio publicado de cada producto ya incluye un costo de envío
estándar, igual para todo el país, según `adr/0012`. El checkout no pide ciudad
ni calcula nada para fijar ese valor: el cliente ve el mismo precio compre desde
donde compre.

Lo que sí varía es lo que el negocio paga a la transportadora por cada envío
real. Ese costo se registra en `Envio` como costo real, separado del recaudo de
contraentrega, para que el margen del pedido sea verdadero y no una estimación
optimista — es información interna, nunca algo que el cliente cotiza o ve.

**Disponibilidad de contraentrega.** Aunque el envío no se cotiza, la ciudad de
destino sigue determinando si contraentrega está disponible: `GET
/api/v1/envios/cobertura` expone las ciudades cubiertas por la transportadora
con recaudo, según las reglas de la sección anterior.

## Retiro en punto

Sin costo, sin recaudo. Requiere elegir el punto de Medellín y genera un código
de retiro. Es el camino más simple y hay que mantenerlo así.
