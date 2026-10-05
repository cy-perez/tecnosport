import { InjectionToken } from '@angular/core';

/**
 * Dónde recuerda el navegador el correo de un pedido mientras el comprador va y vuelve de la
 * pasarela. El correo autoriza el seguimiento, y hasta el 4 de octubre de 2026 viajaba en la URL
 * —`?correo=` o como segmento de la ruta de Sistecrédito—: quedaba en los registros de la web y de
 * la API, en el historial del navegador y en los de las pasarelas. Ahora se guarda aquí antes de
 * salir y se lee al volver.
 */
export interface AlmacenCorreoDePedido {
  recordar(pedidoId: string, correo: string): void;
  correoDe(pedidoId: string): string | null;
}

export const ALMACEN_CORREO_DE_PEDIDO = new InjectionToken<AlmacenCorreoDePedido>(
  'AlmacenCorreoDePedido',
);
