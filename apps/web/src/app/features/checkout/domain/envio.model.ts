import { LineaComando } from './pedido.comandos';
import { Direccion } from './pedido.model';

/**
 * Una transportadora con su tarifa más económica para este carrito y destino (`ADR-0073`). Lo que
 * el comprador elige es la transportadora, y es lo único que vuelve al servidor: el costo lo fija
 * otra vez quien crea el pedido.
 */
export interface OpcionEnvio {
  /** Nombre propio, tal como lo da la plataforma: no pasa por Transloco. */
  readonly transportadora: string;
  readonly costoEnvio: number;
  readonly moneda: string;
  /** Cero es **sin estimado**, como en `CotizacionEnvio`. */
  readonly diasEstimados: number;
}

/**
 * La opción de la transportadora elegida, comparando el nombre como lo hace el servidor
 * (`TarifaEnvio.esDe`): sin mayúsculas ni espacios de sobra. `undefined` si ya no cotizó.
 */
export function opcionDeTransportadora(
  opciones: readonly OpcionEnvio[],
  transportadora: string,
): OpcionEnvio | undefined {
  const clave = transportadora.trim().toLowerCase();
  return opciones.find((opcion) => opcion.transportadora.trim().toLowerCase() === clave);
}

/**
 * Lo que cuesta enviar este carrito a esta dirección. Arriba, la opción más económica; en
 * `opciones`, una por transportadora para que el comprador elija (`ADR-0073`). El identificador del
 * proveedor no llega hasta aquí — si viajara al navegador, alguien podría devolverlo alterado al
 * crear el pedido.
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
  /** De la más económica a la más cara. La primera es la de arriba. */
  readonly opciones: readonly OpcionEnvio[];
}

/**
 * Qué formas de entrega ofrece hoy el negocio. La recogida en el punto se apagó el 8 de octubre de
 * 2026 y la decide el servidor (`RETIRO_EN_PUNTO_HABILITADO`): la pantalla solo la deja de ofrecer.
 */
export interface ModalidadesDeEntrega {
  readonly envioADomicilio: boolean;
  readonly retiroEnPunto: boolean;
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
   * El artículo todavía no se ha medido, así que no se puede cotizar (`ADR-0046`). Para quien
   * compra es lo mismo que `ARTICULO_NO_ASEGURABLE` —no te lo podemos enviar, lo puedes recoger—
   * y por eso comparte forma. Lo que las separa no se le cuenta: aquella no se arregla nunca y
   * esta se arregla en cuanto alguien pase el producto por la báscula.
   */
  | {
      readonly tipo: 'ARTICULO_SIN_MEDIDAS';
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
