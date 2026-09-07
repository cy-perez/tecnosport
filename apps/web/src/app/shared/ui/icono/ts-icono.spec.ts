import { render } from '@testing-library/angular';
import { iconoCarrito } from './iconos';
import { TsIcono } from './ts-icono';

const ETIQUETAS_SOPORTADAS = ['path', 'circle', 'rect', 'line', 'ellipse', 'polyline'];

async function renderIcono(clase?: string) {
  const { container } = await render(TsIcono, {
    inputs: { icono: iconoCarrito, ...(clase === undefined ? {} : { clase }) },
  });
  const svg = container.querySelector('svg');
  if (!svg) {
    throw new Error('no se pintó ningún <svg>');
  }
  return svg;
}

describe('TsIcono', () => {
  // El guardia de verdad: si Lucide empieza a usar una etiqueta que el
  // `@switch` de la plantilla no contempla, el icono se pintaría a medias sin
  // que nada falle. Esta prueba lo convierte en un fallo.
  it('pinta todos los trazos del icono, sin descartar ninguno', async () => {
    const svg = await renderIcono();

    expect(svg.children.length).toBe(iconoCarrito.length);
  });

  it('cada trazo del icono usa una etiqueta que la plantilla soporta', () => {
    for (const [etiqueta] of iconoCarrito) {
      expect(ETIQUETAS_SOPORTADAS).toContain(etiqueta);
    }
  });

  it('traslada los atributos del trazo al elemento', async () => {
    const svg = await renderIcono();
    const primerTrazo = iconoCarrito[0];

    expect(svg.querySelector('path')?.getAttribute('d')).toBe(primerTrazo[1]['d']);
  });

  // docs/04-ui-marca.md: el icono nunca es el nombre accesible. El control que
  // lo contiene lleva su `aria-label` traducido.
  it('queda fuera del árbol de accesibilidad', async () => {
    const svg = await renderIcono();

    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.getAttribute('focusable')).toBe('false');
  });

  // Hereda el color del texto: así funciona en claro y en oscuro sin una regla
  // aparte, y sin un solo HEX.
  it('dibuja con el color del texto que lo rodea', async () => {
    const svg = await renderIcono();

    expect(svg.getAttribute('stroke')).toBe('currentColor');
    expect(svg.getAttribute('fill')).toBe('none');
  });

  it('respeta la retícula de 24 del kit', async () => {
    const svg = await renderIcono();

    expect(svg.getAttribute('viewBox')).toBe('0 0 24 24');
    expect(svg.getAttribute('stroke-width')).toBe('1.5');
  });

  it('mide un token de espacio por omisión', async () => {
    const svg = await renderIcono();

    expect(svg.getAttribute('class')).toContain('size-24');
  });

  it('el tamaño se cambia por clase, y la nueva reemplaza a la de base', async () => {
    const svg = await renderIcono('size-32');

    expect(svg.getAttribute('class')).toContain('size-32');
    expect(svg.getAttribute('class')).not.toContain('size-24');
  });
});
