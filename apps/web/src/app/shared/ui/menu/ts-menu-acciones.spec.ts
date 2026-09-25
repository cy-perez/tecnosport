import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { fireEvent, render, screen } from '@testing-library/angular';
import { AccionDeMenu, TsMenuAcciones } from './ts-menu-acciones';

@Component({
  imports: [TsMenuAcciones],
  template: `
    <ts-menu-acciones
      etiqueta="Acciones de Morral urbano"
      [acciones]="acciones()"
      (elegida)="elegida.set($event)"
    />
  `,
})
class Anfitrion {
  readonly acciones = signal<readonly AccionDeMenu[]>([
    { id: 'editar', etiqueta: 'Editar', enlace: ['p1', 'editar'] },
    { id: 'publicar', etiqueta: 'Publicar' },
    { id: 'eliminar', etiqueta: 'Eliminar', destructiva: true },
  ]);
  readonly elegida = signal<string | null>(null);
}

async function renderMenu() {
  return render(Anfitrion, { providers: [provideRouter([])] });
}

function disparador() {
  return screen.getByRole('button', { name: 'Acciones de Morral urbano' });
}

describe('TsMenuAcciones', () => {
  /** Cerrado no hay nada: el panel se monta al abrirlo, en un portal del CDK. */
  it('cerrado no pinta ninguna opción', async () => {
    await renderMenu();

    expect(screen.queryByRole('menu')).toBeNull();
    expect(screen.queryByRole('menuitem')).toBeNull();
  });

  /**
   * El nombre accesible viene del input y no del icono: `ts-icono` pinta `aria-hidden`, así que sin
   * esto el botón no tendría nombre ninguno. Y lleva el nombre de la fila porque una tabla tiene
   * uno por producto.
   */
  it('el disparador toma su nombre del input, no del icono', async () => {
    await renderMenu();

    expect(disparador()).toBeTruthy();
  });

  it('al abrir ofrece las opciones como menú', async () => {
    await renderMenu();

    fireEvent.click(disparador());

    expect(screen.getByRole('menu')).toBeTruthy();
    expect(screen.getAllByRole('menuitem').map((o) => o.textContent?.trim())).toEqual([
      'Editar',
      'Publicar',
      'Eliminar',
    ]);
  });

  /**
   * Las que llevan `enlace` son `<a>` con `href`, no botones que navegan por código: así se pueden
   * abrir en otra pestaña, que es lo que hace todo el día quien carga catálogo.
   */
  it('una opción con enlace es un ancla navegable', async () => {
    await renderMenu();
    fireEvent.click(disparador());

    const editar = screen.getByRole('menuitem', { name: 'Editar' });
    expect(editar.tagName).toBe('A');
    expect(editar.getAttribute('href')).toBe('/p1/editar');
  });

  it('una opción sin enlace emite su id al elegirla', async () => {
    const { fixture } = await renderMenu();
    fireEvent.click(disparador());

    fireEvent.click(screen.getByRole('menuitem', { name: 'Publicar' }));

    expect(fixture.componentInstance.elegida()).toBe('publicar');
  });

  /** Elegir cierra el menú: si no, la fila queda tapada por el panel mientras se confirma. */
  it('elegir una opción cierra el menú', async () => {
    await renderMenu();
    fireEvent.click(disparador());

    fireEvent.click(screen.getByRole('menuitem', { name: 'Publicar' }));

    expect(screen.queryByRole('menu')).toBeNull();
  });

  /**
   * Lo que no tiene vuelta se pinta distinto. Es la única afirmación sobre clases de este archivo,
   * y va contra la regla de "nunca por clase CSS" a sabiendas: el color es la única diferencia
   * entre esta opción y las demás, así que o se comprueba aquí o no lo comprueba nada.
   */
  it('una opción destructiva va en el color de error', async () => {
    await renderMenu();
    fireEvent.click(disparador());

    expect(screen.getByRole('menuitem', { name: 'Eliminar' }).className).toContain('text-ts-error');
    expect(screen.getByRole('menuitem', { name: 'Publicar' }).className).not.toContain(
      'text-ts-error',
    );
  });
});
