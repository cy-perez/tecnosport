import { Component, signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { TsPaginador } from './ts-paginador';

@Component({
  imports: [TsPaginador],
  template: `
    <ts-paginador [pagina]="pagina()" [totalPaginas]="totalPaginas()" (paginaCambiada)="destino.set($event)" />
  `,
})
class AnfitrionDePrueba {
  readonly pagina = signal(0);
  readonly totalPaginas = signal(1);
  readonly destino = signal<number | null>(null);
}

function renderPaginador(pagina: number, totalPaginas: number) {
  return render(AnfitrionDePrueba, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    componentProperties: {},
  }).then(async (resultado) => {
    resultado.fixture.componentInstance.pagina.set(pagina);
    resultado.fixture.componentInstance.totalPaginas.set(totalPaginas);
    await resultado.fixture.whenStable();
    return resultado;
  });
}

describe('TsPaginador', () => {
  it('muestra la posición en base 1 aunque la página sea 0-based', async () => {
    await renderPaginador(2, 5);

    expect(screen.getByText('Página 3 de 5')).toBeTruthy();
  });

  // El backend devuelve totalPaginas: 0 cuando no hay resultados (la lista de
  // pedidos del panel, sin pedidos todavía). Encontrado en el navegador.
  it('sin resultados no dice "de 0": sigue habiendo una página', async () => {
    await renderPaginador(0, 0);

    expect(screen.getByText('Página 1 de 1')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Siguiente' }).hasAttribute('disabled')).toBe(true);
  });

  it('con una sola página los dos botones quedan deshabilitados', async () => {
    await renderPaginador(0, 1);

    expect(screen.getByRole('button', { name: 'Anterior' }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button', { name: 'Siguiente' }).hasAttribute('disabled')).toBe(true);
  });

  it('en la primera página solo se puede avanzar', async () => {
    const { fixture } = await renderPaginador(0, 3);

    expect(screen.getByRole('button', { name: 'Anterior' }).hasAttribute('disabled')).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(fixture.componentInstance.destino()).toBe(1);
  });

  it('en la última página solo se puede retroceder', async () => {
    const { fixture } = await renderPaginador(2, 3);

    expect(screen.getByRole('button', { name: 'Siguiente' }).hasAttribute('disabled')).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Anterior' }));

    expect(fixture.componentInstance.destino()).toBe(1);
  });
});
