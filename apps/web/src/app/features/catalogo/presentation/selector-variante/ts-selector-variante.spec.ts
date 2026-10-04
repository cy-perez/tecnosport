import { fireEvent, render, screen } from '@testing-library/angular';
import { EjeAtributo } from '../../domain/seleccion-variante';
import { TsSelectorVariante } from './ts-selector-variante';

const ejes: EjeAtributo[] = [
  {
    nombre: 'Color',
    unidad: null,
    opciones: [
      { valor: 'Azul marino', colorHex: '#1E3A8A', existe: true },
      { valor: 'Negro', colorHex: '#111111', existe: true },
    ],
  },
  {
    nombre: 'Talla',
    unidad: null,
    opciones: [
      { valor: 'M', colorHex: null, existe: true },
      { valor: 'L', colorHex: null, existe: true },
    ],
  },
];

describe('TsSelectorVariante', () => {
  // "Garantía: 12" se leía así en la ficha, sin decir 12 qué. La unidad es del eje.
  it('pinta la unidad del eje junto a cada valor', async () => {
    await render(TsSelectorVariante, {
      inputs: {
        ejes: [
          {
            nombre: 'Garantía',
            unidad: 'meses',
            opciones: [{ valor: '12', colorHex: null, existe: true }],
          },
        ],
        seleccion: { Garantía: '12' },
      },
    });

    expect(screen.getByRole('button', { name: '12 meses' })).toBeTruthy();
  });

  it('la muestra de color es un círculo de 24 dentro del objetivo de 44', async () => {
    await render(TsSelectorVariante, {
      inputs: { ejes, seleccion: { Color: 'Azul marino', Talla: 'M' } },
    });

    const boton = screen.getByRole('button', { name: 'Negro' });
    const muestra = boton.firstElementChild!;
    expect(boton.className).toContain('size-tactil');
    expect(muestra.className).toContain('size-24');
    expect(muestra.className).toContain('rounded-completo');
  });

  /** Una escala por debajo: 35 px de alto en vez de 44, y el texto pequeño. */
  it('el botón de la talla es compacto, también si está agotada', async () => {
    await render(TsSelectorVariante, {
      inputs: {
        ejes,
        seleccion: { Color: 'Azul marino', Talla: 'M' },
        noDisponibles: { Talla: ['L'] },
        textoNoDisponible: 'no disponible',
      },
    });

    for (const nombre of ['M', 'L, no disponible']) {
      const talla = screen.getByRole('button', { name: nombre });
      expect(talla.className, nombre).toContain('min-h-compacto');
      expect(talla.className, nombre).not.toContain('min-h-tactil');
      expect(talla.className, nombre).toContain('text-sm');
      expect(talla.className, nombre).toContain('px-16');
    }
  });

  it('elegir un color emite la selección con ese eje actualizado', async () => {
    let emitido: unknown;
    await render(TsSelectorVariante, {
      inputs: { ejes, seleccion: { Color: 'Azul marino', Talla: 'M' } },
      on: { seleccionCambio: (valor) => (emitido = valor) },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Negro' }));

    expect(emitido).toEqual({ Color: 'Negro', Talla: 'M' });
  });

  /**
   * Una talla agotada en lo elegido se ve tachada y lo dice, pero se puede elegir: la ficha salta
   * a la combinación que la tenga. Bloquearla dejaba variantes imposibles de alcanzar.
   */
  it('una talla agotada se ve tachada, lo dice y se puede elegir', async () => {
    let emitido: unknown;
    await render(TsSelectorVariante, {
      inputs: {
        ejes,
        seleccion: { Color: 'Azul marino', Talla: 'M' },
        noDisponibles: { Talla: ['L'] },
        textoNoDisponible: 'no disponible',
      },
      on: { seleccionCambio: (valor) => (emitido = valor) },
    });

    const agotada = screen.getByRole('button', { name: 'L, no disponible' });
    expect(agotada.className).toContain('line-through');
    expect(agotada.getAttribute('aria-disabled')).toBeNull();

    fireEvent.click(agotada);
    expect(emitido).toEqual({ Color: 'Azul marino', Talla: 'L' });
  });

  /**
   * Una talla de la escala que el producto no trae se ve, no se elige y lo dice, sin salir del
   * orden de tabulación: `aria-disabled` y no `disabled`.
   */
  it('una talla que el producto no trae se ve, no se elige y lo dice', async () => {
    let emitido: unknown;
    await render(TsSelectorVariante, {
      inputs: {
        ejes: [
          ejes[0],
          {
            ...ejes[1],
            opciones: [{ valor: 'XS', colorHex: null, existe: false }, ...ejes[1].opciones],
          },
        ],
        seleccion: { Color: 'Azul marino', Talla: 'M' },
        noDisponibles: { Talla: ['XS'] },
        textoNoDisponible: 'no disponible',
      },
      on: { seleccionCambio: (valor) => (emitido = valor) },
    });

    const agotada = screen.getByRole('button', { name: 'XS, no disponible' });
    expect(agotada.getAttribute('aria-disabled')).toBe('true');
    expect(agotada.hasAttribute('disabled')).toBe(false);
    expect(agotada.className).toContain('line-through');

    fireEvent.click(agotada);
    expect(emitido).toBeUndefined();
  });

  it('un color no se tacha aunque venga en la lista', async () => {
    await render(TsSelectorVariante, {
      inputs: {
        ejes,
        seleccion: { Color: 'Azul marino', Talla: 'M' },
        noDisponibles: { Color: ['Negro'] },
        textoNoDisponible: 'no disponible',
      },
    });

    expect(screen.getByRole('button', { name: 'Negro' }).getAttribute('aria-disabled')).toBeNull();
  });

  it('elegir una talla emite la selección con ese eje actualizado', async () => {
    let emitido: unknown;
    await render(TsSelectorVariante, {
      inputs: { ejes, seleccion: { Color: 'Azul marino', Talla: 'M' } },
      on: { seleccionCambio: (valor) => (emitido = valor) },
    });

    fireEvent.click(screen.getByRole('button', { name: 'L' }));

    expect(emitido).toEqual({ Color: 'Azul marino', Talla: 'L' });
  });

  it('marca la opción activa con aria-pressed', async () => {
    await render(TsSelectorVariante, {
      inputs: { ejes, seleccion: { Color: 'Azul marino', Talla: 'M' } },
    });

    expect(screen.getByRole('button', { name: 'Azul marino' }).getAttribute('aria-pressed')).toBe(
      'true',
    );
    expect(screen.getByRole('button', { name: 'Negro' }).getAttribute('aria-pressed')).toBe(
      'false',
    );
  });
});
