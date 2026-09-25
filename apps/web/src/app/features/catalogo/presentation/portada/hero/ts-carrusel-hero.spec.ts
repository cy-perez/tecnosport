import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import { TsCarruselHero } from './ts-carrusel-hero';

async function renderCarrusel() {
  const resultado = await render(TsCarruselHero, {
    inputs: { idioma: 'es' },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([])],
  });
  await resultado.fixture.whenStable();
  return resultado;
}

/** Los cuatro botones de línea, uno por diapositiva, en el orden del carrusel. */
function botonesDeLinea(): HTMLAnchorElement[] {
  return screen
    .getAllByRole('link', { hidden: true })
    .filter((enlace): enlace is HTMLAnchorElement =>
      (enlace.getAttribute('href') ?? '').startsWith('/es/productos?linea='),
    );
}

/** Las viñetas de abajo. Se distinguen de cualquier otro botón por llevar `aria-label`. */
function indicadores(): HTMLButtonElement[] {
  return [
    screen.getByRole('button', { name: 'Ropa deportiva' }),
    screen.getByRole('button', { name: 'Calzado deportivo' }),
    screen.getByRole('button', { name: 'Bolsos' }),
    screen.getByRole('button', { name: 'Tecnología' }),
  ] as HTMLButtonElement[];
}

/** La diapositiva visible es la única sin `inert`. */
function indiceVisible(): number {
  const grupos = screen
    .getAllByRole('group', { hidden: true })
    .filter((grupo) => grupo.getAttribute('aria-roledescription') === 'slide');
  return grupos.findIndex((grupo) => !grupo.hasAttribute('inert'));
}

describe('TsCarruselHero', () => {
  it('ofrece las cuatro líneas de negocio, cada una a su rejilla filtrada', async () => {
    await renderCarrusel();

    expect(botonesDeLinea().map((enlace) => enlace.getAttribute('href'))).toEqual([
      '/es/productos?linea=ROPA',
      '/es/productos?linea=CALZADO',
      '/es/productos?linea=BOLSOS',
      '/es/productos?linea=TECNOLOGIA',
    ]);
  });

  /**
   * Cuatro `<h1>` en la misma página son cuatro encabezados de nivel uno para un lector de pantalla
   * y para un rastreador. El titular de las otras tres es texto destacado, no la cabecera del
   * documento — y esto es exactamente lo que se rompe si alguien "unifica" las dos ramas del `@if`.
   */
  it('solo la primera pieza lleva el encabezado de la página', async () => {
    await renderCarrusel();

    const titulares = screen.getAllByRole('heading', { level: 1, hidden: true });
    expect(titulares).toHaveLength(1);
    expect(titulares[0].textContent?.trim()).toBe('Vístete para moverte');
  });

  /**
   * Es lo que separa un carrusel de cuatro bloques apilados: las tres que no tocan quedan `inert`,
   * así que su botón sale del orden de tabulación y el foco no se va a un enlace fuera de pantalla.
   */
  it('solo la pieza visible es alcanzable, y la viñeta dice cuál es', async () => {
    await renderCarrusel();

    expect(indiceVisible()).toBe(0);
    expect(indicadores().map((boton) => boton.getAttribute('aria-current'))).toEqual([
      'true',
      null,
      null,
      null,
    ]);
  });

  it('pulsar una viñeta trae su pieza', async () => {
    const { fixture } = await renderCarrusel();

    fireEvent.click(indicadores()[2]);
    await fixture.whenStable();

    expect(indiceVisible()).toBe(2);
    expect(indicadores()[2].getAttribute('aria-current')).toBe('true');
    expect(indicadores()[0].getAttribute('aria-current')).toBeNull();
  });

  it('las flechas del teclado mueven el carrusel desde la tira de viñetas', async () => {
    const { fixture } = await renderCarrusel();

    const tira = indicadores()[0].parentElement as HTMLElement;

    fireEvent.keyDown(tira, { key: 'ArrowRight' });
    await fixture.whenStable();
    expect(indiceVisible()).toBe(1);

    fireEvent.keyDown(tira, { key: 'ArrowLeft' });
    fireEvent.keyDown(tira, { key: 'ArrowLeft' });
    await fixture.whenStable();
    // Da la vuelta: una a la izquierda desde la primera es la última.
    expect(indiceVisible()).toBe(3);
  });

  it('pasa sola cada cinco segundos', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { fixture } = await renderCarrusel();
      expect(indiceVisible()).toBe(0);

      await vi.advanceTimersByTimeAsync(5000);
      await fixture.whenStable();

      expect(indiceVisible()).toBe(1);
    } finally {
      vi.useRealTimers();
    }
  });

  /**
   * La APG lo exige de todo carrusel que rota solo, y aquí no basta con acortar la animación: hay
   * que no programar el temporizador. Se prueba con el interruptor del propio sitio
   * (`data-movimiento`) porque es el que se puede fijar desde una prueba; la preferencia del
   * sistema entra por el mismo `if`.
   */
  it('no rota sola cuando se pidió menos movimiento', async () => {
    document.documentElement.setAttribute('data-movimiento', 'reducido');
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { fixture } = await renderCarrusel();

      await vi.advanceTimersByTimeAsync(15000);
      await fixture.whenStable();

      expect(indiceVisible()).toBe(0);
    } finally {
      vi.useRealTimers();
      document.documentElement.removeAttribute('data-movimiento');
    }
  });

  it('enseña los cuatro sellos de confianza', async () => {
    await renderCarrusel();

    expect(screen.getByText('Envíos a todo el país')).toBeTruthy();
    expect(screen.getByText('Opción de pago contraentrega')).toBeTruthy();
    expect(screen.getByText('Diversas opciones de pago')).toBeTruthy();
    expect(screen.getByText('Garantía legal en todo')).toBeTruthy();
  });

  /**
   * Solo la primera pieza compite por el ancho de banda de la primera pintura. Priorizar cuatro
   * imágenes es no priorizar ninguna (apps/web/CLAUDE.md, NG02955).
   */
  it('prioriza la primera imagen y deja las demás perezosas', async () => {
    const { container } = await renderCarrusel();

    const imagenes = [...container.querySelectorAll('img')];
    const anchas = imagenes.filter((img) => (img.getAttribute('src') ?? '').includes('/ancho/'));
    expect(anchas).toHaveLength(4);
    expect(anchas[0].getAttribute('fetchpriority')).toBe('high');
    expect(anchas[0].getAttribute('loading')).toBe('eager');
    expect(anchas.slice(1).map((img) => img.getAttribute('loading'))).toEqual([
      'lazy',
      'lazy',
      'lazy',
    ]);
    expect(anchas.slice(1).every((img) => img.getAttribute('fetchpriority') === null)).toBe(true);
  });
});
