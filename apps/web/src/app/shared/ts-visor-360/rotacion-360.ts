/**
 * Funciones puras del visor de rotación (`docs/10-captura-360.md`). Sin DOM y sin señales: es lo
 * que más se rompe y lo que más barato es probar.
 *
 * El arreglo de fotogramas va ordenado de 0 a N-1 en sentido antihorario visto desde arriba,
 * empezando por el frontal. Esa convención la fija el asistente de captura y no se discute por
 * producto.
 */

/**
 * Normaliza un índice a `[0, total)`: pasado el último se vuelve al primero, y antes del primero
 * se llega al último. El `%` de JavaScript conserva el signo del dividendo, así que un
 * desplazamiento negativo necesita la segunda vuelta.
 */
export function indiceCircular(indice: number, total: number): number {
  if (total <= 0 || !Number.isFinite(indice)) {
    return 0;
  }
  return ((Math.trunc(indice) % total) + total) % total;
}

/**
 * Mapea el desplazamiento horizontal del puntero a un índice de fotograma.
 *
 * La sensibilidad es relativa al ancho del contenedor —un giro completo es aproximadamente un
 * ancho de arrastre— para que se sienta igual en un teléfono y en un escritorio.
 *
 * **Arrastrar hacia la izquierda avanza el índice**: el producto gira como si lo empujaras. De ahí
 * el signo negativo.
 *
 * @param desplazamientoPx Distancia recorrida desde donde empezó el arrastre. Negativa hacia la
 *   izquierda, como la resta de `clientX`.
 * @param anchoContenedorPx Ancho visible del visor. Cero o negativo (todavía sin medir, o
 *   escondido) deja el índice donde estaba: no hay con qué escalar el arrastre.
 */
export function indiceDesdeDesplazamiento(
  desplazamientoPx: number,
  anchoContenedorPx: number,
  total: number,
  indiceInicial: number,
): number {
  if (total <= 0) {
    return 0;
  }
  if (anchoContenedorPx <= 0 || !Number.isFinite(anchoContenedorPx) || !Number.isFinite(desplazamientoPx)) {
    return indiceCircular(indiceInicial, total);
  }

  const pasosExactos = (-desplazamientoPx / anchoContenedorPx) * total;
  // `Math.round` redondea siempre hacia +∞ con los empates (-0,5 da -0, pero 0,5 da 1), y eso
  // haría que el visor girara antes hacia un lado que hacia el otro. El redondeo va sobre el
  // valor absoluto para que el medio paso cueste lo mismo en las dos direcciones.
  const pasos = Math.sign(pasosExactos) * Math.round(Math.abs(pasosExactos));

  return indiceCircular(indiceInicial + pasos, total);
}

/**
 * El fotograma opuesto al frontal, el destino de la tecla Fin. Con un número impar de fotogramas
 * no hay opuesto exacto: se elige el anterior (`floor`), para que el resultado no dependa de cómo
 * redondee cada motor.
 */
export function indiceOpuesto(total: number): number {
  if (total <= 0) {
    return 0;
  }
  return Math.floor(total / 2);
}

/**
 * Orden de precarga de los fotogramas: los vecinos del actual primero, alternando a un lado y al
 * otro, porque el siguiente arrastre puede ir en cualquier dirección. No incluye el actual, que ya
 * está pedido.
 */
export function ordenDePrecarga(indiceActual: number, total: number): number[] {
  if (total <= 1) {
    return [];
  }

  const actual = indiceCircular(indiceActual, total);
  const orden: number[] = [];

  for (let paso = 1; orden.length < total - 1; paso++) {
    const siguiente = indiceCircular(actual + paso, total);
    if (!orden.includes(siguiente)) {
      orden.push(siguiente);
    }
    const anterior = indiceCircular(actual - paso, total);
    if (orden.length < total - 1 && !orden.includes(anterior)) {
      orden.push(anterior);
    }
  }

  return orden;
}
