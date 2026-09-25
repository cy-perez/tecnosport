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

/**
 * Las diapositivas, seleccionadas por `[role="group"][aria-roledescription]` y **no** por el valor
 * de ese atributo: desde que `aria-roledescription` pasa por Transloco —se pronuncia, así que la
 * regla dura #4 aplica— su valor es "diapositiva" en español y "slide" en inglés, y un selector
 * atado al texto se rompe al cambiar de idioma. La tira de viñetas también es `role="group"`, pero
 * sin `roledescription`, así que el selector combinado deja fuera solo lo que debe.
 */
function diapositivas(): HTMLElement[] {
  return screen
    .getAllByRole('group', { hidden: true })
    .filter((grupo) => grupo.hasAttribute('aria-roledescription'));
}

/** La diapositiva visible es la única sin `inert`. */
function indiceVisible(): number {
  return diapositivas().findIndex((grupo) => !grupo.hasAttribute('inert'));
}

/** El botón de pausa: el único de la tira cuyo nombre no es una línea de negocio. */
function botonDePausa(): HTMLButtonElement {
  return screen.getByRole('button', { name: /carrusel/i }) as HTMLButtonElement;
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
   * <b>Ningún titular de diapositiva es un encabezado</b>, y esto invierte lo que esta prueba
   * afirmaba.
   *
   * <p>Antes el `<h1>` estaba en la primera pieza y las otras tres llevaban `<p>`, para no tener
   * cuatro encabezados de nivel uno. El razonamiento era correcto y el resultado estaba roto: ese
   * `<h1>` vive dentro de una diapositiva, y en cuanto el carrusel avanza esa diapositiva queda
   * `inert` y `aria-hidden` — o sea que a los cinco segundos la portada se quedaba sin ningún
   * encabezado de nivel uno. La prueba anterior contaba nodos del DOM y no lo veía.
   *
   * <p>El `<h1>` de la portada vive ahora fuera del carrusel; que exista y sea único lo comprueba
   * `portada.page.spec.ts`, que es quien puede verlo.
   */
  it('ningún titular de diapositiva es un encabezado', async () => {
    await renderCarrusel();

    expect(screen.queryAllByRole('heading', { hidden: true })).toHaveLength(0);
  });

  /**
   * WCAG 2.2.2 (nivel A) exige un mecanismo para pausar, detener u ocultar todo movimiento
   * automático que dure más de cinco segundos. Detenerse con el puntero encima y con el foco dentro
   * <b>no es ese mecanismo</b>: en un teléfono no hay puntero y con teclado no es descubrible.
   */
  it('se puede pausar, y la pausa gana sobre el puntero', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { fixture } = await renderCarrusel();

      fireEvent.click(botonDePausa());
      await fixture.whenStable();
      await vi.advanceTimersByTimeAsync(15000);
      await fixture.whenStable();
      expect(indiceVisible()).toBe(0);

      // Entrar y salir con el puntero no puede reanudar lo que alguien pausó a propósito.
      fireEvent.mouseEnter(diapositivas()[0].closest('section') as HTMLElement);
      fireEvent.mouseLeave(diapositivas()[0].closest('section') as HTMLElement);
      await vi.advanceTimersByTimeAsync(15000);
      await fixture.whenStable();
      expect(indiceVisible()).toBe(0);

      fireEvent.click(botonDePausa());
      await fixture.whenStable();
      await vi.advanceTimersByTimeAsync(5000);
      await fixture.whenStable();
      expect(indiceVisible()).toBe(1);
    } finally {
      vi.useRealTimers();
    }
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
