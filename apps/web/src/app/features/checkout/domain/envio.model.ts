import { LineaComando } from './pedido.comandos';
import { Direccion } from './pedido.model';

/**
 * Lo que cuesta enviar este carrito a esta dirección. Una sola opción, la más
 * económica: la elige el servidor (`ADR-0021`), y ni la lista de tarifas ni el
 * identificador del proveedor llegan hasta aquí — si viajaran al navegador,
 * alguien podría devolverlos alterados al crear el pedido.
 *
 * No dice nada de contraentrega: esta cotización se pide sin recaudo —el comprador todavía no ha
 * elegido cómo paga— y la cobertura de recaudo solo se sabe pidiéndola con recaudo. Eso lo responde
 * `POST /pedidos/metodos-de-pago-disponibles`.
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
}

/**
 * Un artículo que no se puede despachar a domicilio porque vale más de lo que la transportadora
 * asegura (`ADR-0036`). El nombre viene del servidor: es un nombre propio de producto, no un texto
 * de interfaz, así que no pasa por Transloco — la frase que lo rodea sí.
 */
export interface ArticuloNoAsegurable {
  readonly varianteId: string;
  readonly nombre: string;
}

/**
 * Las cuatro respuestas posibles de cotizar, y son cuatro y no una a propósito. Antes esto era
 * `CotizacionEnvio | null`, y ese `null` significaba "sin cobertura"; cada una de las que se le
 * fueron sumando es una forma distinta de quedarse sin envío a domicilio, y la pantalla tiene que
 * poder distinguirlas porque **piden cosas distintas de quien compra**: cambiar la dirección,
 * quitar un artículo, o nada — recoger en el punto.
 *
 * Es la misma forma que el backend tiene en su `ResultadoCotizacion`, y no por simetría: es que la
 * pregunta "¿por qué no hay envío?" tiene las mismas respuestas de los dos lados.
 */
export type ResultadoCotizacion =
  | { readonly tipo: 'TARIFA'; readonly cotizacion: CotizacionEnvio }
  | { readonly tipo: 'SIN_COBERTURA' }
  | {
      readonly tipo: 'ARTICULO_NO_ASEGURABLE';
      readonly articulos: readonly ArticuloNoAsegurable[];
    }
  /**
   * La transportadora rechazó los datos de este envío, y **reintentar no sirve**: el servidor
   * preguntó, la plataforma contestó que el cuerpo estaba mal, y la misma pregunta trae el mismo
   * rechazo. No es `isError()` —eso es "no se pudo preguntar", y ahí insistir sí ayuda— ni
   * `SIN_COBERTURA`, porque no se arregla cambiando la dirección. Lo que queda es la recogida.
   */
  | { readonly tipo: 'COTIZACION_RECHAZADA' };

export interface CotizarEnvioComando {
  readonly lineas: readonly LineaComando[];
  readonly direccion: Direccion;
}
