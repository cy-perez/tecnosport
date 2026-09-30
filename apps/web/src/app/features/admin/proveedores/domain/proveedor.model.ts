/**
 * La línea del catálogo que surte el proveedor. Solo las dos que hoy llegan por WhatsApp: el
 * backend (`Proveedor.LINEAS_ADMITIDAS`) rechaza cualquier otra, y un proveedor que mande bolsos y
 * ropa se registra dos veces, una por línea, porque el margen y la extracción son distintos.
 */
export type LineaProveedor = 'BOLSOS' | 'ROPA';

export const LINEAS_PROVEEDOR: readonly LineaProveedor[] = ['BOLSOS', 'ROPA'];

export interface Proveedor {
  readonly id: string;
  readonly nombre: string;
  /**
   * Cómo aparece el remitente en la exportación del chat. Es lo que decide qué mensajes son suyos
   * y cuáles son nuestros: si no coincide con el nombre del contacto tal como lo guarda el teléfono,
   * la ingesta no lee nada.
   */
  readonly nombreEnExportacion: string;
  readonly telefonoWhatsApp: string;
  readonly linea: LineaProveedor;
  /** Multiplica el precio del proveedor para sugerir el de venta. Por lo menos 1. */
  readonly factorDeMargen: number;
  readonly publicacionAutomatica: boolean;
  readonly activo: boolean;
}

/** Lo que se escribe al crear o al editar. El `id` lo pone el servidor. */
export type DatosProveedor = Omit<Proveedor, 'id'>;
