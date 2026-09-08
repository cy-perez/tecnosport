import { Component, signal } from '@angular/core';
import { fireEvent, render, screen } from '@testing-library/angular';
import { TsCheckbox } from './ts-checkbox';

@Component({
  imports: [TsCheckbox],
  template: `
    <ts-checkbox
      idCasilla="reducir"
      etiqueta="Reducir movimiento"
      [marcado]="marcado()"
      [deshabilitado]="deshabilitado()"
      [sobreMarca]="sobreMarca()"
      (marcadoCambio)="ultimoEmitido.set($event)"
    />
  `,
})
class Anfitrion {
  readonly marcado = signal(false);
  readonly deshabilitado = signal(false);
  readonly sobreMarca = signal(false);
  readonly ultimoEmitido = signal<boolean | null>(null);
}

const casilla = () => screen.getByRole('checkbox', { name: 'Reducir movimiento' }) as HTMLInputElement;

describe('TsCheckbox', () => {
  it('es una casilla nativa cuyo nombre accesible es su etiqueta', async () => {
    await render(Anfitrion);

    expect(casilla().tagName).toBe('INPUT');
    expect(casilla().type).toBe('checkbox');
    expect(casilla().id).toBe('reducir');
  });

  it('informa el estado nuevo, no un simple aviso de cambio', async () => {
    const { fixture } = await render(Anfitrion);

    fireEvent.click(casilla());

    expect(fixture.componentInstance.ultimoEmitido()).toBe(true);
  });

  // El motivo de que este componente exista. `docs/04-ui-marca.md` pide 44 px y
  // WCAG 2.2 AA (2.5.8) exige 24 × 24: la casilla nativa que había en el pie
  // medía 13 × 13, y su etiqueta 19 px de alto. El objetivo es la etiqueta
  // entera, así que `min-h-tactil` tiene que estar ahí y no en la caja.
  //
  // Se afirma sobre la clase porque jsdom no hace layout: medir aquí daría
  // cero siempre. La altura real se verifica en el navegador.
  it('el objetivo táctil es la etiqueta entera', async () => {
    await render(Anfitrion);

    const etiqueta = casilla().closest('label');

    expect(etiqueta?.className).toContain('min-h-tactil');
    expect(etiqueta?.getAttribute('for')).toBe('reducir');
  });

  // En tema claro `--color-foco` y `--color-marca` son el mismo `#1B1F26`, así
  // que sobre la franja del pie el anillo normal no se ve. Es el mismo problema
  // que obligó a crear `anillo-foco-sobre-marca`.
  it('sobre la franja de marca usa el anillo de foco que sí contrasta', async () => {
    const { fixture } = await render(Anfitrion);
    expect(casilla().className).toContain('anillo-foco');
    expect(casilla().className).not.toContain('anillo-foco-sobre-marca');

    fixture.componentInstance.sobreMarca.set(true);
    await fixture.whenStable();

    expect(casilla().className).toContain('anillo-foco-sobre-marca');
  });

  // Trampa real: el visto se dibuja encima del control. Sin
  // `pointer-events-none` se comería el clic justo cuando está marcada — o sea,
  // no se podría desmarcar nunca.
  it('el visto no intercepta el clic que desmarca', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.marcado.set(true);
    await fixture.whenStable();

    const visto = document.querySelector('ts-icono');

    expect(visto).toBeTruthy();
    expect(visto?.className).toContain('pointer-events-none');
  });

  it('sin marcar no dibuja el visto', async () => {
    await render(Anfitrion);

    expect(document.querySelector('ts-icono')).toBeNull();
  });

  // Se afirma sobre `disabled` y no sobre "no emite al pulsarla", que fue el
  // primer intento y falló: `fireEvent.click` despacha el evento a mano y jsdom
  // ejecuta la activación del <input> sin mirar `disabled`, así que el change
  // salía igual. Un navegador de verdad no entrega el clic. Forzar la prueba
  // habría sido afirmar una fidelidad que el entorno no tiene; el atributo
  // `disabled` sí es la garantía real, y de él cuelga el comportamiento.
  it('deshabilitada marca el control como tal y avisa al puntero', async () => {
    const { fixture } = await render(Anfitrion);
    fixture.componentInstance.deshabilitado.set(true);
    await fixture.whenStable();

    expect(casilla().disabled).toBe(true);
    expect(casilla().closest('label')?.className).toContain('cursor-not-allowed');
  });
});
