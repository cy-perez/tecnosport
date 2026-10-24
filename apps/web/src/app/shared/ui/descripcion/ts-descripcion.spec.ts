import { render, screen, within } from '@testing-library/angular';
import { TsDescripcion } from './ts-descripcion';

const DESCRIPCION = `Reloj con GPS y pantalla AMOLED.

## Características principales

- Hasta 14 días de batería según el fabricante.
- Resistencia al agua 5 ATM.

## Ficha técnica

| Atributo | Detalle |
|---|---|
| Pantalla | AMOLED de 1,43" |
| Conectividad | Bluetooth 5.3 · GPS | Galileo | Glonass |

---

Ficha técnica: Datos de Open Icecat (https://icecat.biz) sin garantía de ningún tipo.
`;

async function renderDescripcion(texto: string | null, nivelDeTitulo?: 2 | 3) {
  return render(TsDescripcion, {
    inputs: nivelDeTitulo ? { texto, nivelDeTitulo } : { texto },
  });
}

describe('TsDescripcion', () => {
  // El fallo que esto arregla: la ficha pintaba la descripción en un solo párrafo y el cliente
  // leía las almohadillas y las barras de la tabla en un bloque corrido.
  it('no deja a la vista la sintaxis de la descripción', async () => {
    const { container } = await renderDescripcion(DESCRIPCION);
    const texto = container.textContent ?? '';

    expect(texto).not.toContain('##');
    expect(texto).not.toContain('|---');
    expect(texto).not.toContain('| Pantalla');
    expect(container.querySelectorAll('p').length).toBeGreaterThan(1);
  });

  it('cada subtítulo es un encabezado de nivel 2 por omisión', async () => {
    await renderDescripcion(DESCRIPCION);

    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent?.trim())).toEqual([
      'Características principales',
      'Ficha técnica',
    ]);
  });

  it('dentro de una sección que ya tiene su h2, los subtítulos bajan a h3', async () => {
    await renderDescripcion(DESCRIPCION, 3);

    expect(screen.queryAllByRole('heading', { level: 2 })).toHaveLength(0);
    expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(2);
  });

  it('las viñetas son una lista de verdad', async () => {
    await renderDescripcion(DESCRIPCION);

    expect(screen.getAllByRole('listitem').map((li) => li.textContent?.trim())).toEqual([
      'Hasta 14 días de batería según el fabricante.',
      'Resistencia al agua 5 ATM.',
    ]);
  });

  it('la ficha técnica es una tabla de dos columnas con el atributo como encabezado de fila', async () => {
    await renderDescripcion(DESCRIPCION);
    const tabla = screen.getByRole('table');

    expect(
      within(tabla)
        .getAllByRole('rowheader')
        .map((th) => th.textContent?.trim()),
    ).toEqual(['Pantalla', 'Conectividad']);
    expect(within(tabla).getByRole('cell', { name: /GPS \| Galileo \| Glonass/ })).toBeTruthy();
    for (const fila of within(tabla).getAllByRole('row').slice(1)) {
      expect(fila.children).toHaveLength(2);
    }
  });

  it('las fuentes enlazan su dirección sin espacios de más alrededor', async () => {
    await renderDescripcion(DESCRIPCION);
    const enlace = screen.getByRole('link', { name: 'https://icecat.biz' });

    expect(enlace.getAttribute('href')).toBe('https://icecat.biz');
    expect(enlace.closest('p')?.textContent).toBe(
      'Ficha técnica: Datos de Open Icecat (https://icecat.biz) sin garantía de ningún tipo.',
    );
  });

  it('un texto escrito a mano se ve como se escribió, con sus saltos de línea', async () => {
    const { container } = await renderDescripcion('Camiseta de algodón.\nLavar en frío.');

    expect(container.querySelectorAll('p')).toHaveLength(1);
    expect(container.querySelectorAll('br')).toHaveLength(1);
    expect(screen.queryByRole('heading')).toBeNull();
  });

  it('sin descripción no pinta nada', async () => {
    const { container } = await renderDescripcion(null);

    expect(container.textContent?.trim()).toBe('');
  });
});
