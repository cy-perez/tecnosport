import { Component, signal } from '@angular/core';
import { fireEvent, render, screen } from '@testing-library/angular';
import { TsLeyenda } from './ts-leyenda';

/**
 * jsdom no tiene `popover` ni maqueta: aquí el globo se muestra con `hidden` y se comprueba qué
 * dice, cuándo aparece y que no se quede huérfano. Que se vea encima de la muestra y sin cortarse
 * en el borde de la tarjeta se comprueba en el navegador.
 */
@Component({
  imports: [TsLeyenda],
  template: `@if (montada()) {
    <button type="button" aria-label="Elegir color Azul marino" [tsLeyenda]="nombre()">
      muestra
    </button>
  }`,
})
class Anfitrion {
  readonly montada = signal(true);
  readonly nombre = signal('Azul marino');
}

const globos = () => Array.from(document.querySelectorAll<HTMLElement>('[data-leyenda]'));

describe('TsLeyenda', () => {
  afterEach(() => globos().forEach((globo) => globo.remove()));

  it('muestra el nombre al pasar el puntero y lo quita al salir', async () => {
    await render(Anfitrion);
    const boton = screen.getByRole('button', { name: 'Elegir color Azul marino' });
    expect(globos()).toHaveLength(0);

    fireEvent.mouseEnter(boton);
    expect(globos()).toHaveLength(1);
    expect(globos()[0].textContent).toBe('Azul marino');
    expect(globos()[0].hidden).toBe(false);

    fireEvent.mouseLeave(boton);
    expect(globos()[0].hidden).toBe(true);
  });

  /** Es solo visual: si el lector lo leyera, diría el color dos veces junto al `aria-label`. */
  it('no se anuncia: el nombre accesible sigue siendo el del botón', async () => {
    await render(Anfitrion);
    const boton = screen.getByRole('button', { name: 'Elegir color Azul marino' });

    fireEvent.mouseEnter(boton);

    expect(globos()[0].getAttribute('aria-hidden')).toBe('true');
    expect(boton.getAttribute('aria-describedby')).toBeNull();
  });

  it('Escape lo cierra sin mover el puntero', async () => {
    await render(Anfitrion);
    const boton = screen.getByRole('button', { name: 'Elegir color Azul marino' });

    fireEvent.mouseEnter(boton);
    fireEvent.keyDown(boton, { key: 'Escape' });

    expect(globos()[0].hidden).toBe(true);
  });

  it('un segundo paso por encima reutiliza el mismo globo con el texto de hoy', async () => {
    const { fixture } = await render(Anfitrion);
    const boton = screen.getByRole('button', { name: 'Elegir color Azul marino' });

    fireEvent.mouseEnter(boton);
    fireEvent.mouseLeave(boton);
    fixture.componentInstance.nombre.set('Vino');
    fixture.detectChanges();
    fireEvent.mouseEnter(boton);

    expect(globos()).toHaveLength(1);
    expect(globos()[0].textContent).toBe('Vino');
  });

  /** El globo vive en el `<body>`, fuera del componente: sin limpieza quedaría uno por tarjeta. */
  it('se va del documento cuando el elemento se destruye', async () => {
    const { fixture } = await render(Anfitrion);
    fireEvent.mouseEnter(screen.getByRole('button', { name: 'Elegir color Azul marino' }));
    expect(globos()).toHaveLength(1);

    fixture.componentInstance.montada.set(false);
    fixture.detectChanges();

    expect(globos()).toHaveLength(0);
  });
});
