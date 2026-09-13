/**
 * El formato de precio, fuera del componente, porque hay un sitio donde el
 * precio no es un elemento sino una palabra dentro de una frase: "Te ahorras
 * $ 9.540 de envío" se interpola en una clave de Transloco y necesita el valor
 * ya formateado. Duplicar el `Intl.NumberFormat` allí habría dejado dos
 * formatos capaces de separarse.
 *
 * docs/05-i18n.md: la moneda no se convierte, solo cambia el formato por
 * idioma — "$ 189.900" en español, "COP 189,900" en inglés.
 */
export function formatearPrecio(valor: number, moneda: string, idioma: string): string {
  const locale = idioma === 'en' ? 'en-US' : 'es-CO';
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: moneda,
    currencyDisplay: idioma === 'en' ? 'code' : 'symbol',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(valor);
}
