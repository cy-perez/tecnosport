import { MetodoDePagoConPrecio } from './pedido.model';

/**
 * El flete que se va a cobrar con el medio elegido: el que el servidor calculó para ese medio, si
 * lo mandó, o el de la cotización si no.
 *
 * <p>Con contraentrega no son el mismo: la transportadora cobra además el recaudo, y sumar a la
 * mercancía el flete de la cotización —que es sin recaudo— enseñaba en la pantalla donde se finaliza
 * la transacción un total menor que el que se cobra (art. 50 de la Ley 1480 de 2011). Mientras el
 * contrato no traiga el flete por medio, el de la cotización es lo único que hay, y es el de hoy.
 */
export function fleteDelMedio(
  eleccion: MetodoDePagoConPrecio | null,
  fleteCotizado: number,
): number {
  return eleccion?.costoEnvio?.valor ?? fleteCotizado;
}
