import { render } from '@testing-library/angular';
import { marcaFacebook, marcaInstagram, marcaWhatsapp } from './marcas.generado';
import { TsIconoMarca } from './ts-icono-marca';

async function renderMarca(clase?: string) {
  const { container } = await render(TsIconoMarca, {
    inputs: { marca: marcaFacebook, ...(clase === undefined ? {} : { clase }) },
  });
  const svg = container.querySelector('svg');
  if (!svg) {
    throw new Error('no se pintó ningún <svg>');
  }
  return svg;
}

describe('TsIconoMarca', () => {
  /**
   * La diferencia con `ts-icono`, y el motivo de que sean dos componentes: una marca es una
   * silueta rellena. Con `fill="none"` y un trazo de 1,5 —lo que hace `ts-icono`— el logo saldría
   * invisible o deformado, y nada fallaría.
   */
  it('rellena la silueta con el color del texto, sin trazo', async () => {
    const svg = await renderMarca();

    expect(svg.getAttribute('fill')).toBe('currentColor');
    expect(svg.getAttribute('stroke')).toBe('none');
  });

  it('pinta el path de la marca', async () => {
    const svg = await renderMarca();

    expect(svg.querySelector('path')?.getAttribute('d')).toBe(marcaFacebook.trazo);
  });

  // docs/04-ui-marca.md: el icono nunca es el nombre accesible. Lo lleva el enlace que lo
  // contiene, con su texto traducido.
  it('queda fuera del árbol de accesibilidad', async () => {
    const svg = await renderMarca();

    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.getAttribute('focusable')).toBe('false');
  });

  it('respeta la retícula de 24 del kit', async () => {
    const svg = await renderMarca();

    expect(svg.getAttribute('viewBox')).toBe('0 0 24 24');
  });

  it('mide un token de espacio por omisión', async () => {
    expect((await renderMarca()).getAttribute('class')).toContain('size-24');
  });

  // En dos pruebas y no en una: TestBed no se puede reconfigurar dos veces dentro del mismo `it`.
  it('el tamaño se cambia por clase, y la nueva reemplaza a la de base', async () => {
    const svg = await renderMarca('size-16');

    expect(svg.getAttribute('class')).toContain('size-16');
    expect(svg.getAttribute('class')).not.toContain('size-24');
  });

  /**
   * El archivo lo escribe `npm run iconos-marca` desde `simple-icons`, y una regeneración a medias
   * —un `path` vacío, un selector que dejó de encajar— pasaría inadvertida: el SVG se pintaría sin
   * dibujo. Esto lo convierte en un fallo.
   */
  it('las tres marcas registradas traen un path de verdad', () => {
    for (const marca of [marcaFacebook, marcaInstagram, marcaWhatsapp]) {
      expect(marca.titulo.length).toBeGreaterThan(0);
      expect(marca.trazo.startsWith('M')).toBe(true);
      expect(marca.trazo.length).toBeGreaterThan(100);
    }
  });
});
