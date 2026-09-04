import { DatosTransferencia, EstadoPedido, MetodoPago, Pedido, TipoEntrega } from './pedido.model';

/** Espejo de la invariante del constructor de `Pedido` en el backend
 * (`ENVIO_A_DOMICILIO` exige dirección, `RETIRO_EN_PUNTO` no la lleva). */
export function requiereDireccion(tipoEntrega: TipoEntrega): boolean {
  return tipoEntrega === 'ENVIO_A_DOMICILIO';
}

/** Métodos que `CrearIntentoDePago` resuelve por el Web Checkout de Wompi
 * (`docs/09-plan-de-arranque.md`, Fase 3). Transferencia manual y
 * contraentrega no pasan por Wompi. */
export function esMetodoPagoWompi(metodoPago: MetodoPago): boolean {
  return (
    metodoPago === 'TARJETA' ||
    metodoPago === 'PSE' ||
    metodoPago === 'NEQUI' ||
    metodoPago === 'BANCOLOMBIA' ||
    metodoPago === 'ADDI'
  );
}

/** Único estado desde el que `ReintentarPago` transiciona de vuelta a
 * `PAGO_PENDIENTE` (`docs/02-modelo-datos.md`). */
export function puedeReintentarPago(estado: EstadoPedido): boolean {
  return estado === 'PAGO_FALLIDO';
}

/** `datosTransferencia` solo llega poblado cuando el pedido se pagó por
 * transferencia manual — la respuesta lo trae en `null` en cualquier otro caso. */
export function datosTransferenciaDelPedido(pedido: Pedido): DatosTransferencia | null {
  return pedido.metodoPago === 'TRANSFERENCIA_MANUAL' ? pedido.datosTransferencia : null;
}
