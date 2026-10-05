import { AlmacenCorreoDePedido } from '../domain/almacen-correo-de-pedido.puerto';

/**
 * El correo con el que se consulta un pedido al llegar a una pantalla de checkout, por orden: el
 * fragmento de la URL —el enlace de los correos lo pone después del `#`, que el navegador nunca
 * manda al servidor— y lo que el navegador recordó antes de ir a la pasarela. Lo que viene en el
 * fragmento se recuerda también, para que un refresco no lo pierda.
 */
export function correoDelPedido(
  pedidoId: string,
  fragmento: string | null,
  almacen: AlmacenCorreoDePedido,
): string | null {
  const delFragmento = fragmento ? new URLSearchParams(fragmento).get('correo') : null;
  if (delFragmento) {
    almacen.recordar(pedidoId, delFragmento);
    return delFragmento;
  }
  return almacen.correoDe(pedidoId);
}
