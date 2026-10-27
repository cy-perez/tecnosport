import { InjectionToken } from '@angular/core';

/**
 * Qué líneas del carrito dejó el comprador fuera del pago, entre visitas y entre pantallas.
 *
 * Se guardan las **desmarcadas** y no las marcadas: todas arrancan marcadas, y así una línea que se
 * agrega después entra marcada sin que nadie tenga que acordarse de anotarla. Quien no toca una
 * casilla paga el carrito entero, como antes de que existieran.
 *
 * Tiene que sobrevivir a la salida a la pasarela: Wompi y Sistecrédito recargan la aplicación
 * entera al volver, y una selección solo en memoria se perdía justo ahí.
 */
export interface AlmacenLineasSinMarcar {
  /** Vacío si no hay nada guardado, o si corre en el servidor. */
  leer(): readonly string[];
  guardar(lineaIds: readonly string[]): void;
}

export const ALMACEN_LINEAS_SIN_MARCAR = new InjectionToken<AlmacenLineasSinMarcar>(
  'AlmacenLineasSinMarcar',
);
