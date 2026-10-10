/**
 * Un zip subido para una ingesta, visto como archivo: el historial que sirve para limpiar el
 * almacenamiento. Borrarlo se lleva solo los bytes; los lotes, sus borradores y sus fotos se quedan.
 */
export interface ArchivoDeIngesta {
  readonly id: string;
  readonly proveedorId: string;
  /** Nulo en los subidos antes de que se guardara. */
  readonly nombreOriginal: string | null;
  /** Nulo por lo mismo. */
  readonly tamanoBytes: number | null;
  /** ISO-8601, UTC. */
  readonly subidoEn: string;
  /** Nulo mientras sigue en el almacenamiento. */
  readonly borradoEn: string | null;
  /** Uno, o dos si el proveedor sube sus dos chats juntos. */
  readonly lotes: number;
  /** Algún lote que lo lee sigue abierto: todavía no se puede borrar. */
  readonly enUso: boolean;
}

export interface ArchivosDeIngestaPaginados {
  readonly items: readonly ArchivoDeIngesta[];
  readonly pagina: number;
  readonly totalPaginas: number;
  readonly totalArchivos: number;
}

/** Se puede borrar el que sigue ahí y ningún lote está leyendo. */
export function sePuedeBorrarArchivo(archivo: ArchivoDeIngesta): boolean {
  return archivo.borradoEn === null && !archivo.enUso;
}

const UNIDADES = ['byte', 'kilobyte', 'megabyte', 'gigabyte'] as const;

/**
 * El tamaño con la unidad que le queda, en el idioma de la pantalla: "18,4 MB". Base 1024, la que
 * usa el explorador de archivos donde la persona eligió el zip.
 */
export function formatearTamano(bytes: number, idioma: string): string {
  let valor = bytes;
  let unidad = 0;
  while (valor >= 1024 && unidad < UNIDADES.length - 1) {
    valor /= 1024;
    unidad++;
  }
  return new Intl.NumberFormat(idioma, {
    style: 'unit',
    unit: UNIDADES[unidad],
    unitDisplay: 'short',
    maximumFractionDigits: unidad === 0 ? 0 : 1,
  }).format(valor);
}
