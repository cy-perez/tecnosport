/**
 * Un modelo de tecnología que llegó en la lista del proveedor y espera revisión (ADR-0075). La
 * lista ya trae la ficha, la paleta oficial y el costo de cada configuración; lo que se decide
 * aquí es qué colores se venden de cada una y a qué precio.
 */
export type EstadoBorradorTecnologia = 'EN_REVISION' | 'APROBADO' | 'RECHAZADO';

export const ESTADOS_BORRADOR_TECNOLOGIA: readonly EstadoBorradorTecnologia[] = [
  'EN_REVISION',
  'APROBADO',
  'RECHAZADO',
];

/** Una memoria, almacenamiento y SIM. Cada color elegido será una variante del producto. */
export interface ConfiguracionTecnologia {
  readonly sku: string;
  readonly titulo: string;
  readonly ram: string | null;
  readonly almacenamiento: string | null;
  readonly sim: string | null;
  readonly costoProveedor: number;
  readonly precioMercado: number | null;
  /** Los que deduce la skill de los emojis de la lista; vacío si la lista no los dice. */
  readonly coloresSugeridos: readonly string[];
  /** Vacío es «esta configuración no se vende». */
  readonly coloresElegidos: readonly string[];
  readonly precioVenta: number | null;
}

export interface BorradorTecnologia {
  readonly id: string;
  readonly proveedorId: string;
  readonly idModelo: string;
  readonly titulo: string;
  readonly marcaSugerida: string | null;
  readonly categoriaSugerida: string | null;
  readonly descripcion: string;
  /** Los colores oficiales del modelo; vacía si no se conocen, y entonces se escriben a mano. */
  readonly paleta: readonly string[];
  readonly configuraciones: readonly ConfiguracionTecnologia[];
  /**
   * El producto que ya vende este modelo si el borrador es de configuraciones nuevas; o el que
   * nació al aprobarlo.
   */
  readonly productoId: string | null;
  readonly estado: EstadoBorradorTecnologia;
  readonly motivoRechazo: string | null;
  readonly vistoEn: string;
  readonly creadoEn: string;
}

export interface EleccionDeConfiguracion {
  readonly sku: string;
  readonly colores: readonly string[];
  readonly precioVenta: number | null;
}

/** Marca y categoría del catálogo: solo hacen falta para un modelo nuevo. */
export interface AprobarBorradorTecnologia {
  readonly marcaId: string | null;
  readonly categoriaId: string | null;
}

export function borradorTecnologiaEditable(borrador: BorradorTecnologia): boolean {
  return borrador.estado === 'EN_REVISION';
}

/** Un borrador con producto mientras está en revisión completa ese producto: no crea otro. */
export function completaUnProductoExistente(borrador: BorradorTecnologia): boolean {
  return borrador.estado === 'EN_REVISION' && borrador.productoId !== null;
}

/**
 * Los colores con que arranca la casilla de una configuración: lo que ya se eligió, o si nada,
 * lo que sugiere la lista y está en la paleta. Sin sugerencia no se marca nada: vender un color
 * que el proveedor no dijo tener es justo lo que esta revisión existe para evitar.
 */
export function coloresIniciales(
  configuracion: ConfiguracionTecnologia,
  paleta: readonly string[],
): string[] {
  if (configuracion.coloresElegidos.length > 0) {
    return [...configuracion.coloresElegidos];
  }
  if (paleta.length === 0) {
    return [...configuracion.coloresSugeridos];
  }
  return configuracion.coloresSugeridos.filter((color) => paleta.includes(color));
}

/** El precio con que arranca el campo: el ya fijado, o el de mercado como punto de partida. */
export function precioInicial(configuracion: ConfiguracionTecnologia): number | null {
  return configuracion.precioVenta ?? configuracion.precioMercado;
}

export type ProblemaDeAprobacion = 'nadaQueVender' | 'faltaPrecio' | null;

/**
 * Lo mismo que exige el servidor, dicho antes de enviar: al menos una configuración con colores, y
 * cada una que se venda con su precio.
 */
export function problemaDeAprobacion(
  elecciones: readonly EleccionDeConfiguracion[],
): ProblemaDeAprobacion {
  const queSeVenden = elecciones.filter((e) => e.colores.length > 0);
  if (queSeVenden.length === 0) {
    return 'nadaQueVender';
  }
  return queSeVenden.some((e) => e.precioVenta === null) ? 'faltaPrecio' : null;
}

/** «Negro, Azul claro»: sin paleta, los colores se escriben separados por coma. */
export function separarColoresEscritos(texto: string): string[] {
  return [
    ...new Set(
      texto
        .split(',')
        .map((color) => color.trim())
        .filter((color) => color.length > 0),
    ),
  ];
}
