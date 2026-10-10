/**
 * La estructura acordada del zip de un proveedor que sube sus dos chats juntos —el general y el de
 * caballero— (9 de octubre de 2026). Para Meraki: `Meraki.zip`, con `Meraki.txt` (el general),
 * `MerakiMen.txt` (el de caballero) y las fotos de los dos. El nombre sale del proveedor.
 *
 * Es exacta a propósito: con un solo zip, el servidor encola primero el chat de caballero y luego
 * el general, y para eso tiene que saber cuál `.txt` es cuál. Un archivo con otra forma no se deja
 * subir. El servidor la vuelve a validar al leerlo (`ChatDelZip.elegir`): esto es para avisar antes
 * de subir decenas de megas, no para reemplazar aquello.
 */
export interface EstructuraDeDosChats {
  readonly zip: string;
  readonly general: string;
  readonly caballero: string;
}

export function estructuraDeDosChats(nombreDelProveedor: string): EstructuraDeDosChats {
  const base = nombreDelProveedor.trim();
  return { zip: `${base}.zip`, general: `${base}.txt`, caballero: `${base}Men.txt` };
}

/**
 * @param nombreDelArchivo el del zip que se eligió
 * @param entradas los nombres de lo que trae el zip, con su carpeta si la tiene
 * @returns si es exactamente la estructura acordada: el nombre del zip, y entre sus archivos
 *   exactamente dos `.txt`, el general y el de caballero. Lo demás —las fotos, los videos— puede
 *   ir como sea. Las carpetas no cuentan: el servidor lee los archivos por su nombre.
 */
export function esLaEstructuraAcordada(
  estructura: EstructuraDeDosChats,
  nombreDelArchivo: string,
  entradas: readonly string[],
): boolean {
  if (nombreDelArchivo !== estructura.zip) {
    return false;
  }
  const textos = entradas
    .filter((entrada) => !entrada.endsWith('/'))
    .map((entrada) => entrada.slice(entrada.lastIndexOf('/') + 1))
    .filter((nombre) => nombre.toLowerCase().endsWith('.txt'));
  return (
    textos.length === 2 &&
    textos.includes(estructura.general) &&
    textos.includes(estructura.caballero)
  );
}
