import { Component, signal } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import { TsPaginaFormulario } from './ts-pagina-formulario';

@Component({
  imports: [TsPaginaFormulario],
  template: `
    <ts-pagina-formulario [titulo]="titulo()" [ancho]="ancho()" [clase]="clase()">
      <p>Contenido proyectado</p>
    </ts-pagina-formulario>
  `,
})
class Anfitrion {
  readonly titulo = signal('Crear cuenta');
  readonly ancho = signal<'normal' | 'ancho'>('normal');
  readonly clase = signal('');
}

function seccion(container: HTMLElement): HTMLElement {
  const s = container.querySelector('section');
  if (!s) {
    throw new Error('no se pintó ninguna <section>');
  }
  return s;
}

describe('TsPaginaFormulario', () => {
  // El título es el `h1` de la pantalla: `docs/04-ui-marca.md` exige jerarquía
  // sin saltos y un solo h1 por página.
  it('pinta el título como encabezado de nivel 1', async () => {
    await render(Anfitrion);

    expect(screen.getByRole('heading', { level: 1, name: 'Crear cuenta' })).toBeTruthy();
  });

  it('proyecta su contenido', async () => {
    await render(Anfitrion);

    expect(screen.getByText('Contenido proyectado')).toBeTruthy();
  });

  it('el título llega traducido desde fuera, no lo resuelve el componente', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.titulo.set('Sign up');
    await fixture.whenStable();

    expect(screen.getByRole('heading', { level: 1, name: 'Sign up' })).toBeTruthy();
  });

  it('por omisión usa el ancho de formulario normal', async () => {
    const { container } = await render(Anfitrion);

    expect(seccion(container).className).toContain('max-w-formulario');
    expect(seccion(container).className).not.toContain('max-w-formulario-lg');
  });

  it('el ancho largo reemplaza al normal, no se suman', async () => {
    const { fixture, container } = await render(Anfitrion);
    fixture.componentInstance.ancho.set('ancho');
    await fixture.whenStable();

    expect(seccion(container).className).toContain('max-w-formulario-lg');
  });

  it('la clase de quien llama gana sobre la base', async () => {
    const { fixture, container } = await render(Anfitrion);
    fixture.componentInstance.clase.set('my-16');
    await fixture.whenStable();

    expect(seccion(container).className).toContain('my-16');
    expect(seccion(container).className).not.toContain('my-32');
  });
});
