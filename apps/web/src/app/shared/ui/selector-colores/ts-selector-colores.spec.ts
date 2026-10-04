import { Component, signal } from '@angular/core';
import { fireEvent, render, screen } from '@testing-library/angular';
import { esperarSinViolaciones } from '../../../../testing/axe';
import { OpcionColor, TextosSelectorColores, TsSelectorColores } from './ts-selector-colores';

const OPCIONES: OpcionColor[] = [
  { valor: 'Blanco', etiqueta: 'Blanco', muestra: { patron: null, colores: ['#FFFFFF'] } },
  { valor: 'Negro', etiqueta: 'Negro', muestra: { patron: null, colores: ['#111111'] } },
  { valor: 'Rojo', etiqueta: 'Rojo', muestra: { patron: null, colores: ['#C62828'] } },
  {
    valor: 'Animal print',
    etiqueta: 'Animal print',
    muestra: { patron: 'ANIMAL_PRINT', colores: ['#C19A6B', '#3B2A1A'] },
  },
];

const TEXTOS: TextosSelectorColores = {
  ninguno: 'Vale para todos los colores',
  buscar: 'Buscar color',
  maximo: 'Ya hay 3 colores. Desmarca uno para cambiarlo.',
  sinResultados: 'Ningún color se llama así.',
};

/** Un anfitrión que hace lo que haría un formulario: guarda lo que el control emite. */
@Component({
  imports: [TsSelectorColores],
  template: `
    <ts-selector-colores
      idCampo="color"
      label="Color de la foto 1"
      [opciones]="opciones"
      [seleccion]="seleccion()"
      [textos]="textos"
      (seleccionCambio)="seleccion.set($event)"
    />
  `,
})
class Anfitrion {
  readonly opciones = OPCIONES;
  readonly textos = TEXTOS;
  readonly seleccion = signal<string[]>([]);
}

async function renderSelector() {
  const resultado = await render(Anfitrion);
  const anfitrion = resultado.fixture.componentInstance;
  return { ...resultado, anfitrion };
}

function abrir() {
  fireEvent.click(screen.getByRole('button', { name: /Color de la foto 1/ }));
}

describe('TsSelectorColores', () => {
  it('cerrado dice que vale para todos y declara la región que abre', async () => {
    await renderSelector();

    const boton = screen.getByRole('button', { name: /Color de la foto 1/ });
    expect(boton.textContent).toContain('Vale para todos los colores');
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(boton.getAttribute('aria-controls')).toBe('color-lista');
    expect(screen.queryByLabelText('Negro')).toBeNull();
  });

  /** El caso de la camiseta Ferrari: el orden de marcado es el orden de la combinación. */
  /** El nombre del botón dice la etiqueta y lo marcado: sin lo segundo, no se oye qué hay. */
  it('el botón se nombra con la etiqueta y los colores marcados', async () => {
    const { anfitrion, fixture } = await renderSelector();
    anfitrion.seleccion.set(['Negro', 'Rojo']);
    fixture.detectChanges();

    expect(
      await screen.findByRole('button', { name: 'Color de la foto 1 Negro / Rojo' }),
    ).toBeTruthy();
  });

  it('el orden en que se marcan es el orden de la combinación', async () => {
    const { anfitrion } = await renderSelector();
    abrir();

    fireEvent.click(screen.getByLabelText('Negro'));
    fireEvent.click(screen.getByLabelText('Rojo'));

    expect(anfitrion.seleccion()).toEqual(['Negro', 'Rojo']);
    expect(screen.getByLabelText('Negro (1)')).toBeTruthy();
    expect(screen.getByLabelText('Rojo (2)')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Color de la foto 1/ }).textContent).toContain(
      'Negro / Rojo',
    );
  });

  it('desmarcar uno corre a los que siguen', async () => {
    const { anfitrion } = await renderSelector();
    abrir();
    fireEvent.click(screen.getByLabelText('Blanco'));
    fireEvent.click(screen.getByLabelText('Negro'));
    fireEvent.click(screen.getByLabelText('Rojo'));

    fireEvent.click(screen.getByLabelText('Blanco (1)'));

    expect(anfitrion.seleccion()).toEqual(['Negro', 'Rojo']);
    expect(screen.getByLabelText('Negro (1)')).toBeTruthy();
  });

  /** Al llegar a tres, las demás se deshabilitan y un aviso lo explica. */
  it('no deja marcar más de tres y lo dice', async () => {
    const { anfitrion } = await renderSelector();
    abrir();
    fireEvent.click(screen.getByLabelText('Blanco'));
    fireEvent.click(screen.getByLabelText('Negro'));
    fireEvent.click(screen.getByLabelText('Rojo'));

    const cuarta = screen.getByLabelText('Animal print') as HTMLInputElement;
    expect(cuarta.disabled).toBe(true);
    fireEvent.click(cuarta);
    expect(anfitrion.seleccion()).toEqual(['Blanco', 'Negro', 'Rojo']);
    expect(screen.getByRole('status').textContent).toContain('Ya hay 3 colores');
  });

  it('el buscador filtra sin distinguir tildes ni mayúsculas', async () => {
    await renderSelector();
    abrir();

    fireEvent.input(screen.getByLabelText('Buscar color'), { target: { value: 'ANIMAL' } });

    expect(screen.getByLabelText('Animal print')).toBeTruthy();
    expect(screen.queryByLabelText('Negro')).toBeNull();

    fireEvent.input(screen.getByLabelText('Buscar color'), { target: { value: 'fucsia' } });
    expect(screen.getByText('Ningún color se llama así.')).toBeTruthy();
  });

  /** El foco de vuelta al botón no se ve en jsdom (`docs/06-testing.md`): se mira en el navegador. */
  it('Escape cierra la lista', async () => {
    await renderSelector();
    abrir();

    fireEvent.keyDown(screen.getByLabelText('Buscar color'), { key: 'Escape' });

    expect(screen.queryByLabelText('Buscar color')).toBeNull();
    expect(
      screen.getByRole('button', { name: /Color de la foto 1/ }).getAttribute('aria-expanded'),
    ).toBe('false');
  });

  it('no tiene violaciones de accesibilidad abierto', async () => {
    const { container } = await renderSelector();
    abrir();
    fireEvent.click(screen.getByLabelText('Negro'));

    await esperarSinViolaciones(container);
  });
});
