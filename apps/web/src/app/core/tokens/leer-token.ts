/**
 * Lee un token del kit (`packages/marca/tokens.css`) tal como lo resuelve el navegador.
 *
 * <p>Existe para lo poco que el TypeScript necesita saber de un token —una duración que programa
 * un temporizador, el color que se le pasa a la `<meta name="theme-color">`— sin copiar el valor:
 * un `4000` o un `#0E1217` escritos aquí son la segunda copia de un número que ya vive en
 * `tokens.json`, y el día que el kit cambie, esta copia se queda atrás sin que nada falle.
 *
 * <p>Lee el estilo **computado** de `<html>`, no la hoja: así devuelve el valor del tema que está
 * aplicado ahora (`[data-tema="oscuro"]` redefine los colores). Solo en el navegador; quien llama
 * pone la guarda de plataforma. Devuelve la cadena vacía si el token no está —una prueba sin
 * `tokens.css`, o un nombre mal escrito—, y quien llama decide qué hace con eso.
 */
export function leerToken(documento: Document, nombre: string): string {
  const ventana = documento.defaultView;
  if (!ventana?.getComputedStyle) {
    return '';
  }
  return ventana.getComputedStyle(documento.documentElement).getPropertyValue(nombre).trim();
}

/**
 * Una duración CSS (`4000ms`, `4s`) en milisegundos. `null` si no es una duración: quien llama no
 * debe inventarse un valor por omisión, porque ese sería justo el literal que este archivo evita.
 */
export function aMilisegundos(valor: string): number | null {
  const coincidencia = /^(\d+(?:\.\d+)?)(ms|s)$/.exec(valor.trim());
  if (!coincidencia) {
    return null;
  }
  const cantidad = Number(coincidencia[1]);
  return coincidencia[2] === 's' ? cantidad * 1000 : cantidad;
}
