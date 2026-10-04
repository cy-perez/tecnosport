import { OpcionSelect } from './ts-select';

/**
 * Las opciones en orden alfabético de su etiqueta, como se lee en el idioma de la pantalla: sin
 * distinguir tildes ni mayúsculas —«Índigo» va con la i, «Coñac» antes que «Coral»— y con los
 * números en su orden y no carácter a carácter. En inglés la paleta se ordena por el nombre en
 * inglés, que es lo que se lee primero en la etiqueta.
 */
export function ordenarPorEtiqueta(
  opciones: readonly OpcionSelect[],
  idioma: string,
): OpcionSelect[] {
  const comparador = new Intl.Collator(idioma, { sensitivity: 'base', numeric: true });
  return [...opciones].sort((una, otra) => comparador.compare(una.etiqueta, otra.etiqueta));
}
