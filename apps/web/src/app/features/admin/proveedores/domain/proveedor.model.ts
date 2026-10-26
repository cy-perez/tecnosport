import { TipoProductoProveedor } from '../../borradores/domain/borrador.model';

/**
 * La línea del catálogo que surte el proveedor. El backend (`Proveedor.LINEAS_ADMITIDAS`) rechaza
 * cualquier otra, y un proveedor que mande bolsos y ropa se registra dos veces, una por línea,
 * porque el margen y la extracción son distintos. Bolsos, ropa y calzado llegan por la exportación
 * del chat; la tecnología, por la lista de precios que procesa la skill (`ADR-0075`).
 */
export type LineaProveedor = 'BOLSOS' | 'ROPA' | 'CALZADO' | 'TECNOLOGIA';

export const LINEAS_PROVEEDOR: readonly LineaProveedor[] = [
  'BOLSOS',
  'ROPA',
  'CALZADO',
  'TECNOLOGIA',
];

/**
 * En qué orden manda el proveedor las fotos y el texto con el precio de un producto. Solo decide
 * de quién es una foto que cae en el mismo minuto que dos precios —la exportación de Android no
 * trae segundos—: con `FOTOS_PRIMERO` es del precio de después y con `TEXTO_PRIMERO` del de antes.
 * No tiene valor por omisión: se elige mirando el chat del proveedor.
 */
export type OrdenDePublicacion = 'FOTOS_PRIMERO' | 'TEXTO_PRIMERO';

export const ORDENES_DE_PUBLICACION: readonly OrdenDePublicacion[] = [
  'FOTOS_PRIMERO',
  'TEXTO_PRIMERO',
];

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
  readonly ordenDePublicacion: OrdenDePublicacion;
  readonly publicacionAutomatica: boolean;
  readonly activo: boolean;
  /**
   * Si sube sus dos chats —el general y el de caballero— en un solo zip: `{nombre}.zip` con
   * `{nombre}.txt` y `{nombre}Men.txt`. Meraki lo hace (9 de octubre de 2026); el panel valida esa
   * estructura antes de dejar subir el archivo. Solo para quien entra por el chat.
   */
  readonly dosChatsEnUnZip: boolean;
  /**
   * Los tipos de prenda que no se le compran: el anuncio que trae uno se descarta entero, aunque
   * venga con otras prendas. A Violeta, pantalones, sudaderas y shorts (12 de octubre de 2026).
   * Solo para quien entra por el chat.
   */
  readonly tiposExcluidos: readonly TipoProductoProveedor[];
  /**
   * Si la prenda que anuncia con su acompañante —la falda con la blusa, la camiseta con la gorra—
   * se publica como set. Solo La Riverah (12 de octubre de 2026), y solo para quien entra por el
   * chat.
   */
  readonly armaSets: boolean;
  /**
   * Si surte desde Medellín: lo suyo llega al punto de TecnoSport y el comprador puede recogerlo.
   * La recogida se ofrece solo si todo el carrito es de proveedores locales (ADR-0093). La Riverah
   * y Violeta (26 de octubre de 2026), y vale para cualquier línea.
   */
  readonly local: boolean;
  /**
   * Lo que su chat tiene de distinto (ADR-0089): nacieron con Tenis Al Pie, que manda tandas de
   * modelos distintos sin SKU, sin tallas en el anuncio y sin existencias. Solo para quien entra por
   * el chat; las tallas por género, solo para el calzado.
   */
  readonly ajustesDeIngesta: AjustesDeIngesta;
}

/**
 * Las tallas que un proveedor de calzado maneja siempre, como él las nombra, y cuántas tallas más
 * arriba las nombra: las EUR son la nacional más una (ADR-0085).
 */
export interface TallasPorGenero {
  readonly dama: readonly string[];
  readonly caballero: readonly string[];
  readonly ajuste: number;
}

export interface AjustesDeIngesta {
  /** Cada foto de la publicación es un producto distinto. */
  readonly unaFotoUnProducto: boolean;
  /** La talla se confirma por WhatsApp antes de cobrar, con un enlace de compra (ADR-0086). */
  readonly ventaBajoConfirmacion: boolean;
  /** Cuántos días sigue a la venta lo que no vuelve a aparecer; `null` es la ventana general. */
  readonly diasDeVigencia: number | null;
  /** `null` si las tallas las dice cada anuncio. */
  readonly tallasPorGenero: TallasPorGenero | null;
}

export const AJUSTES_DE_INGESTA_POR_OMISION: AjustesDeIngesta = {
  unaFotoUnProducto: false,
  ventaBajoConfirmacion: false,
  diasDeVigencia: null,
  tallasPorGenero: null,
};

/** Lo que se escribe al crear o al editar. El `id` lo pone el servidor. */
export type DatosProveedor = Omit<Proveedor, 'id'>;
