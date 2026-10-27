import { InjectionToken } from '@angular/core';

/**
 * Qué carrito originó un pedido que salió a pagarse en una pasarela (Wompi o Sistecrédito), para
 * vaciarlo solo cuando vuelva pagado.
 *
 * <p>Confirmar vaciaba el carrito al salir hacia la pasarela, antes de que existiera el pago: quien
 * cancelaba en Wompi, o a quien le negaban el crédito, volvía a un carrito vacío y tenía que armarlo
 * de nuevo. Ahora el carrito se queda, y la pantalla de estado lo vacía cuando el pedido vuelve
 * pagado **y** el carrito de este navegador es el mismo que lo originó: a esa pantalla también se
 * llega desde el enlace de un correo, días después y con otra compra a medias.
 *
 * Desde que se elige qué pagar, anota también **qué líneas** fueron al pedido: al volver pagado
 * salen solo esas, y lo que se dejó fuera sigue en el carrito.
 */
export interface AlmacenCarritoPorPagar {
  anotar(pedidoId: string, carritoId: string, lineaIds: readonly string[]): void;
  carritoDe(pedidoId: string): string | null;
  /**
   * `null` si no se sabe: un pedido anotado antes de que existiera la selección. Quien lo lee
   * vacía entonces el carrito entero, que era lo que ese pedido llevaba.
   */
  lineasDe(pedidoId: string): readonly string[] | null;
  olvidar(pedidoId: string): void;
}

/** Sin fábrica por omisión, como los almacenes del carrito: lo provee `app.config.ts`. */
export const ALMACEN_CARRITO_POR_PAGAR = new InjectionToken<AlmacenCarritoPorPagar>(
  'AlmacenCarritoPorPagar',
);
