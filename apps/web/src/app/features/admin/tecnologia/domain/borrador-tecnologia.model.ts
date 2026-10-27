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
  /**
   * El de mercado sin ganarle al costo más del tope (ADR-0095, decisión 8), calculado por el
   * servidor. `null` cuando la lista no trajo precio de mercado: ahí no hay sugerencia.
   */
  readonly precioContadoSugerido: number | null;
  /** El sugerido de contado con la comisión de Wompi encima: el que se anunciaría. */
  readonly precioPublicadoSugerido: number | null;
  /** Los que deduce la skill de los emojis de la lista; vacío si la lista no los dice. */
  readonly coloresSugeridos: readonly string[];
  /** Vacío es «esta configuración no se vende». */
  readonly coloresElegidos: readonly string[];
  /** El de contado ya fijado; el publicado lo calcula el servidor al aprobar. */
  readonly precioVenta: number | null;
}

/** Una foto del borrador, ya retocada; la primera es la principal (ADR-0079). */
export interface FotoDeBorradorTecnologia {
  readonly id: string;
  /** Firmada: caduca, y por eso no pasa por el cargador de imágenes del catálogo. */
  readonly url: string;
  readonly ancho: number;
  readonly alto: number;
  /** El texto alternativo con que se publicará, en cada idioma: quien aprueba lo ve. */
  readonly altEs: string;
  readonly altEn: string;
}

export interface BorradorTecnologia {
  readonly id: string;
  readonly proveedorId: string;
  readonly idModelo: string;
  readonly titulo: string;
  readonly marcaSugerida: string | null;
  readonly categoriaSugerida: string | null;
  /** La ficha que se publicará al aprobar: la editada a mano si la hay, la de la skill si no. */
  readonly descripcion: string;
  /** La que preparó la skill, aunque se haya editado: es a la que se vuelve. */
  readonly descripcionDeLaSkill: string;
  readonly fichaEditada: boolean;
  /** Una lista trajo otra ficha de la skill después de la edición a mano, y no la pisó. */
  readonly fichaNuevaDeLaSkill: boolean;
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
  /** Cuántas tiene: viene también en la bandeja, que no trae las fotos. */
  readonly cantidadDeFotos: number;
  /**
   * Cuántas le faltan para aprobarse; cero en uno de configuraciones nuevas. Lo calcula el servidor,
   * que es quien sabe el mínimo: el panel no lo repite.
   */
  readonly fotosQueFaltan: number;
  /** Solo en el detalle; vacía en la bandeja. */
  readonly fotos: readonly FotoDeBorradorTecnologia[];
}

/**
 * Una foto que sube quien revisa desde el panel: un solo ancho, el del archivo. La escalera de
 * anchos y la vista previa las arma la skill; desde aquí no hay de dónde sacarlas.
 */
export interface FotoNuevaDeBorrador {
  readonly archivo: File;
  readonly ancho: number;
  readonly alto: number;
  readonly altEs: string;
  readonly altEn: string;
}

/** Lo que dejó una tanda del borrado en bloque: el panel pide otra mientras `quedan` no sea cero. */
export interface TandaDeBorradoresTecnologiaEliminados {
  readonly eliminados: number;
  readonly quedan: number;
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

/**
 * El precio de contado con que arranca el campo: el ya fijado, o el sugerido de contado. Ya no el
 * de mercado a secas: sin tope, un equipo barato de costo se sugería a lo que valía en el mercado
 * aunque eso fuera ganarle cualquier cifra (ADR-0095, decisión 8).
 */
export function precioInicial(configuracion: ConfiguracionTecnologia): number | null {
  return configuracion.precioVenta ?? configuracion.precioContadoSugerido;
}

export type ProblemaDeAprobacion = 'nadaQueVender' | 'faltaPrecio' | 'precioBajoCosto' | null;

/**
 * Lo mismo que exige el servidor, dicho antes de enviar: al menos una configuración con colores, y
 * cada una que se venda con un precio que supere su costo.
 */
export function problemaDeAprobacion(
  elecciones: readonly EleccionDeConfiguracion[],
  configuraciones: readonly ConfiguracionTecnologia[],
): ProblemaDeAprobacion {
  const queSeVenden = elecciones.filter((e) => e.colores.length > 0);
  if (queSeVenden.length === 0) {
    return 'nadaQueVender';
  }
  if (queSeVenden.some((e) => e.precioVenta === null)) {
    return 'faltaPrecio';
  }
  const costo = new Map(configuraciones.map((c) => [c.sku, c.costoProveedor] as const));
  return queSeVenden.some((e) => (e.precioVenta ?? 0) <= (costo.get(e.sku) ?? 0))
    ? 'precioBajoCosto'
    : null;
}

/**
 * El precio que alguien escribió: «849.900», «849900» o «849 900». Vacío es «todavía no»; lo que
 * no es un entero positivo —«849,900», «ochocientos»— es un error que se dice, no un precio que se
 * borra en silencio.
 */
export function precioEscrito(texto: string): number | null | 'ilegible' {
  if (texto.trim() === '') {
    return null;
  }
  const valor = Number(texto.replace(/[.\s]/g, ''));
  return Number.isInteger(valor) && valor > 0 ? valor : 'ilegible';
}

/** «Negro» y «negro» son el mismo color: el servidor tampoco los distingue. */
export function mismoColor(a: string, b: string): boolean {
  return a.trim().toLowerCase() === b.trim().toLowerCase();
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
