/** En qué quedó un enlace de compra. El vencimiento lo dice la hora, no una acción. */
export type EstadoEnlaceDeCompra = 'VIGENTE' | 'USADO' | 'ANULADO' | 'VENCIDO';

/**
 * Un enlace de compra de un color de tecnología confirmado con el proveedor (ADR-0086), como lo ve
 * el panel: con el token entero, para armar la URL que se manda por WhatsApp.
 */
export interface EnlaceDeCompraAdmin {
  readonly id: string;
  readonly token: string;
  readonly varianteId: string;
  /** `null` si el producto se borró después de generar el enlace. */
  readonly producto: string | null;
  readonly slug: string | null;
  /** «256 GB · Morado»: la variante como la dice el pedido. */
  readonly detalle: string | null;
  readonly estado: EstadoEnlaceDeCompra;
  readonly creadoEn: string;
  readonly expiraEn: string;
  readonly pedidoId: string | null;
}

/**
 * La URL de la ficha que abre el enlace: la del idioma de quien la va a recibir, con el token en
 * `?enlace=`. La ficha lo lee de ahí, elige la variante y deja agregarla al carrito.
 */
export function urlDeCompra(origen: string, idioma: string, slug: string, token: string): string {
  return `${origen}/${idioma}/productos/${slug}?enlace=${encodeURIComponent(token)}`;
}
