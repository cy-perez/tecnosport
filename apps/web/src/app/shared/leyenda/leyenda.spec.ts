import { Component, signal } from '@angular/core';
import { fireEvent, render, screen } from '@testing-library/angular';
import { Leyenda } from './leyenda';

/**
 * jsdom no tiene `popover` ni maqueta: aquí el globo se muestra con `hidden` y se comprueba qué
 * dice, cuándo aparece y que no se quede huérfano. Que se vea encima de la muestra y sin cortarse
 * en el borde de la tarjeta se comprueba en el navegador.
 */
@Component({
  imports: [Leyenda],
  template: `@if (montada()) {
      <button type="button" aria-label="Elegir color Azul marino" [appLeyenda]="nombre()">
        muestra
      </button>
    }
    <button type="button">otro</button>`,
})
class Anfitrion {
  readonly montada = signal(true);
  readonly nombre = signal('Azul marino');
}

const globos = () => Array.from(document.querySelectorAll<HTMLElement>('[data-leyenda]'));
const raton = { pointerType: 'mouse' };

async function renderizar() {
  const resultado = await render(Anfitrion);
  return { ...resultado, boton: screen.getByRole('button', { name: 'Elegir color Azul marino' }) };
}

describe('Leyenda', () => {
  afterEach(() => globos().forEach((globo) => globo.remove()));

  it('muestra el nombre al pasar el puntero y lo quita al salir', async () => {
    const { boton } = await renderizar();
    expect(globos()).toHaveLength(0);

    fireEvent.pointerEnter(boton, raton);
    expect(globos()).toHaveLength(1);
    expect(globos()[0].textContent).toBe('Azul marino');
    expect(globos()[0].hidden).toBe(false);

    fireEvent.pointerLeave(boton, raton);
    // Se va del documento: un globo por muestra sobrevolada se acumulaba en el <body>.
    expect(globos()).toHaveLength(0);
  });

  /** Un toque no la abre: sin `pointerleave` hasta el siguiente toque, se quedaba pegada. */
  it('un toque no la abre', async () => {
    const { boton } = await renderizar();

    fireEvent.pointerEnter(boton, { pointerType: 'touch' });

    expect(globos()).toHaveLength(0);
  });

  /** Es solo visual: si el lector lo leyera, diría el color dos veces junto al `aria-label`. */
  it('no se anuncia: el nombre accesible sigue siendo el del botón', async () => {
    const { boton } = await renderizar();

    fireEvent.pointerEnter(boton, raton);

    expect(globos()[0].getAttribute('aria-hidden')).toBe('true');
    expect(boton.getAttribute('aria-describedby')).toBeNull();
  });

  /** Abierta con el puntero, el foco puede estar en otra parte: Escape la cierra igual. */
  it('Escape la cierra aunque el foco esté en otro control', async () => {
    const { boton } = await renderizar();
    const otro = screen.getByRole('button', { name: 'otro' });
    otro.focus();

    fireEvent.pointerEnter(boton, raton);
    fireEvent.keyDown(otro, { key: 'Escape' });

    expect(globos()).toHaveLength(0);
  });

  it('con el teclado se abre, y con el foco de un clic no', async () => {
    const { boton } = await renderizar();
    const visible = vi.spyOn(boton, 'matches');

    visible.mockReturnValue(false);
    fireEvent.focusIn(boton);
    expect(globos()).toHaveLength(0);

    visible.mockImplementation((selector: string) => selector === ':focus-visible');
    fireEvent.focusIn(boton);
    expect(globos()[0]?.textContent).toBe('Azul marino');

    fireEvent.focusOut(boton);
    expect(globos()).toHaveLength(0);
  });

  it('un segundo paso por encima dice el texto de hoy', async () => {
    const { fixture, boton } = await renderizar();

    fireEvent.pointerEnter(boton, raton);
    fireEvent.pointerLeave(boton, raton);
    fixture.componentInstance.nombre.set('Vino');
    fixture.detectChanges();
    fireEvent.pointerEnter(boton, raton);

    expect(globos()).toHaveLength(1);
    expect(globos()[0].textContent).toBe('Vino');
  });

  /** El globo vive en el `<body>`, fuera del componente: sin limpieza quedaría huérfano. */
  it('se va del documento cuando el elemento se destruye', async () => {
    const { fixture, boton } = await renderizar();
    fireEvent.pointerEnter(boton, raton);
    expect(globos()).toHaveLength(1);

    fixture.componentInstance.montada.set(false);
    fixture.detectChanges();

    expect(globos()).toHaveLength(0);
  });
});
