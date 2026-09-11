import { LineaComando } from './pedido.comandos';
import { Direccion } from './pedido.model';

/**
 * Lo que cuesta enviar este carrito a esta dirección. Una sola opción, la más
 * económica: la elige el servidor (`ADR-0021`), y ni la lista de tarifas ni el
 * identificador del proveedor llegan hasta aquí — si viajaran al navegador,
 * alguien podría devolverlos alterados al crear el pedido.
 *
 * Es informativa. El costo que se cobra lo fija el servidor otra vez al crear
 * el pedido, así que el resumen se vuelve a pintar con lo que devuelve el
 * pedido (`docs/03-api.md`).
 */
export interface CotizacionEnvio {
  readonly costoEnvio: number;
  readonly moneda: string;
  /** Nombre para mostrar, tal como lo da la transportadora: "Coordinadora",
   * "99 minutes". Es un nombre propio y no pasa por Transloco. */
  readonly transportadora: string;
  /** Cero significa **sin estimado**, no "llega hoy": hay tarifas que no
   * declaran plazo y no se les inventa uno. */
  readonly diasEstimados: number;
  readonly venceEn: string;
  readonly admiteContraentrega: boolean;
}

export interface CotizarEnvioComando {
  readonly lineas: readonly LineaComando[];
  readonly direccion: Direccion;
}
