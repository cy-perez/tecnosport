/**
 * Google recorta el fragmento alrededor de los 155-160 caracteres. Se corta en
 * 160 y en el último espacio, no a mitad de palabra.
 */
const MAXIMO = 160;

/**
 * Convierte un texto libre —la descripción que el panel escribió para un
 * producto— en algo que sirva como `meta description`.
 *
 * Colapsa los saltos de línea y los espacios repetidos, porque el texto viene de
 * un área de texto y una `meta` con saltos dentro no es inválida pero sí ilegible
 * en la fuente; y recorta por palabra, con puntos suspensivos, en vez de dejar
 * que el buscador corte donde le toque.
 *
 * Función pura y sin dependencias a propósito: es la única parte de los
 * metadatos que depende del contenido, y es la única que se puede probar sin
 * montar un DOM.
 */
export function resumirDescripcion(texto: string | null | undefined): string {
  const limpio = (texto ?? '').replace(/\s+/g, ' ').trim();
  if (limpio.length <= MAXIMO) {
    return limpio;
  }
  const recorte = limpio.slice(0, MAXIMO);
  const ultimoEspacio = recorte.lastIndexOf(' ');
  // Sin espacios en 160 caracteres no hay palabra por la cual cortar: se corta
  // donde sea antes que devolver el texto entero.
  const base = ultimoEspacio > 0 ? recorte.slice(0, ultimoEspacio) : recorte;
  return `${base.replace(/[.,;:\s]+$/, '')}…`;
}
