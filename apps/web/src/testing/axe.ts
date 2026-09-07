import axe, { type AxeResults, type RunOptions } from 'axe-core';

/**
 * Auditoría de accesibilidad sobre un fragmento ya renderizado.
 *
 * Se usa `axe-core` directo y no un envoltorio (`vitest-axe` va por la 0.1.0 y
 * `jest-axe` es de otro runner): el motor no tiene peers y hace exactamente lo
 * que hace falta. Cada dependencia es deuda.
 *
 * **Qué NO comprueba, y por qué no es un hueco:** en jsdom no hay maquetación
 * —nada tiene tamaño ni posición—, así que las reglas que dependen de píxeles
 * pintados no pueden correr. La de contraste es la principal, y está cubierta
 * aparte y mejor por `npm run contrastes`, que calcula los pares reales de
 * `tokens.css` en los dos temas. Desactivarla aquí es reconocer eso, no
 * esconderlo.
 */
const REGLAS_SIN_MAQUETACION = ['color-contrast', 'target-size'] as const;

export interface OpcionesAxe {
  /** Reglas adicionales a desactivar, con el motivo escrito en la prueba. */
  readonly desactivar?: readonly string[];
}

export async function auditarAccesibilidad(
  elemento: Element,
  opciones: OpcionesAxe = {},
): Promise<AxeResults> {
  const rules: RunOptions['rules'] = {};
  for (const regla of [...REGLAS_SIN_MAQUETACION, ...(opciones.desactivar ?? [])]) {
    rules[regla] = { enabled: false };
  }

  return axe.run(elemento, {
    // Los dos niveles que `docs/04-ui-marca.md` exige: WCAG 2.2 AA.
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'] },
    rules,
  });
}

/** Formatea las violaciones para que el fallo diga qué arreglar y dónde. */
export function describirViolaciones(resultados: AxeResults): string {
  return resultados.violations
    .map((v) => {
      const nodos = v.nodes.map((n) => `      ${n.html}`).join('\n');
      return `  [${v.impact}] ${v.id}: ${v.help}\n    ${v.helpUrl}\n${nodos}`;
    })
    .join('\n\n');
}

/** Falla la prueba con el detalle si hay violaciones. */
export async function esperarSinViolaciones(
  elemento: Element,
  opciones: OpcionesAxe = {},
): Promise<void> {
  const resultados = await auditarAccesibilidad(elemento, opciones);
  if (resultados.violations.length > 0) {
    throw new Error(
      `axe encontró ${resultados.violations.length} violación(es) de WCAG 2.2 AA:\n\n` +
        describirViolaciones(resultados),
    );
  }
}
