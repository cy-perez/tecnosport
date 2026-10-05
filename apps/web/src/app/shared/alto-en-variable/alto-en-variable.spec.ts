import { Component, signal } from '@angular/core';
import { render } from '@testing-library/angular';
import { AltoEnVariable } from './alto-en-variable';

/**
 * jsdom no tiene `ResizeObserver` ni maqueta, así que el observador es un doble que se dispara a
 * mano y el alto se fija en `getBoundingClientRect`. Lo que de verdad se pinta —que la reserva del
 * CSS cubra la barra— se comprueba en el navegador.
 */
class ResizeObserverFalso {
  static instancias: ResizeObserverFalso[] = [];
  desconectado = false;
  constructor(readonly avisar: () => void) {
    ResizeObserverFalso.instancias.push(this);
  }
  observe(): void {
    this.avisar();
  }
  disconnect(): void {
    this.desconectado = true;
  }
}

@Component({
  imports: [AltoEnVariable],
  template: `@if (montada()) {
    <div data-testid="barra" appAltoEnVariable="--alto-de-prueba"></div>
  }`,
})
class Anfitrion {
  readonly montada = signal(true);
}

let altoFalso = 0;

/** El alto que "mide" cualquier elemento: jsdom no maqueta y siempre daría 0. */
function fijarAlto(alto: number): void {
  altoFalso = alto;
}

const publicado = () => document.documentElement.style.getPropertyValue('--alto-de-prueba');

describe('AltoEnVariable', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(
      () => ({ height: altoFalso }) as DOMRect,
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    ResizeObserverFalso.instancias = [];
    document.documentElement.style.removeProperty('--alto-de-prueba');
  });

  it('publica el alto del elemento y lo actualiza cuando cambia', async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverFalso);
    fijarAlto(127.4);
    const { fixture } = await render(Anfitrion);
    await fixture.whenStable();

    expect(publicado()).toBe('128px');

    fijarAlto(72);
    ResizeObserverFalso.instancias[0].avisar();
    expect(publicado()).toBe('72px');
  });

  // Fuera de la ficha la regla no aplica, pero un valor viejo colgado de <html> confundiría a la
  // próxima pantalla que use la misma variable.
  it('la retira y deja de observar al destruirse', async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverFalso);
    fijarAlto(90);
    const { fixture } = await render(Anfitrion);
    await fixture.whenStable();
    expect(publicado()).toBe('90px');

    fixture.componentInstance.montada.set(false);
    fixture.detectChanges();

    expect(publicado()).toBe('');
    expect(ResizeObserverFalso.instancias[0].desconectado).toBe(true);
  });

  it('sin ResizeObserver no publica nada y el CSS usa su respaldo', async () => {
    vi.stubGlobal('ResizeObserver', undefined);
    const { fixture } = await render(Anfitrion);
    await fixture.whenStable();

    expect(publicado()).toBe('');
  });
});
