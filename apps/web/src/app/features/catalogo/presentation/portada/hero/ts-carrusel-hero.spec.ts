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
    // Una ruta comodín y no `provideRouter([])`: hay una prueba que deja que el clic llegue al
    // enlace de verdad, y sin ninguna ruta declarada el router rechaza la navegación con NG04002
    // desde fuera de la prueba — una promesa rechazada que Vitest anota como error suelto.
    providers: [provideRouter([{ path: '**', children: [] }])],
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

/** Lo que mide la ventana del carrusel en estas pruebas: jsdom no maqueta, así que se finge. */
const ANCHO_VENTANA = 800;

/**
 * La ventana que recibe los gestos: es la que lleva `aria-live`, porque es la que recorta la tira.
 * Se busca por ese atributo y no por una clase — una clase de Tailwind cambia al recolocar algo y
 * la prueba se caería sin que nada se hubiera roto de verdad.
 */
function ventana(): HTMLElement {
  const ventana = diapositivas()[0].closest('[aria-live]');
  if (!ventana) {
    throw new Error('el carrusel no pintó su ventana');
  }
  return ventana as HTMLElement;
}

/** `PointerEvent` no existe en jsdom; `MouseEvent` sí trae `clientX`/`clientY` de verdad. */
function puntero(tipo: string, posicion: { x: number; y: number }, destino?: HTMLElement): void {
  const evento = new MouseEvent(tipo, { clientX: posicion.x, clientY: posicion.y, bubbles: true });
  Object.defineProperty(evento, 'pointerId', { value: 1 });
  (destino ?? ventana()).dispatchEvent(evento);
}

/**
 * Un deslizamiento completo, con una parada intermedia.
 *
 * <p>Los dos `pointermove` no son adorno: el primero es el que decide el eje del gesto —hasta que
 * el dedo no se mueve lo bastante, el carrusel no sabe si esto es pasar de pieza o desplazar la
 * página— y con uno solo la prueba no ejercitaría esa decisión.
 */
function deslizar(desde: { x: number; y: number }, hasta: { x: number; y: number }): void {
  puntero('pointerdown', desde);
  puntero('pointermove', { x: (desde.x + hasta.x) / 2, y: (desde.y + hasta.y) / 2 });
  puntero('pointermove', hasta);
  puntero('pointerup', hasta);
}

describe('TsCarruselHero', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: ANCHO_VENTANA,
      height: 400,
      top: 0,
      left: 0,
      right: ANCHO_VENTANA,
      bottom: 400,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

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
      await vi.advanceTimersByTimeAsync(4000);
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

  /**
   * Cuatro segundos desde el 25 de septiembre de 2026; fueron cinco. La prueba mira los dos lados
   * del umbral —a los 3.900 ms sigue quieta— porque una que solo comprobara "a los 4.000 ya cambió"
   * pasaría igual con un temporizador de un segundo.
   */
  it('pasa sola cada cuatro segundos', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { fixture } = await renderCarrusel();
      expect(indiceVisible()).toBe(0);

      await vi.advanceTimersByTimeAsync(3900);
      await fixture.whenStable();
      expect(indiceVisible()).toBe(0);

      await vi.advanceTimersByTimeAsync(100);
      await fixture.whenStable();
      expect(indiceVisible()).toBe(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it('deslizar hacia la izquierda trae la pieza siguiente', async () => {
    const { fixture } = await renderCarrusel();

    deslizar({ x: 600, y: 200 }, { x: 200, y: 200 });
    await fixture.whenStable();

    expect(indiceVisible()).toBe(1);
  });

  /** Hacia el otro lado desde la primera, que es circular: la anterior de la primera es la última. */
  it('deslizar hacia la derecha trae la anterior, dando la vuelta', async () => {
    const { fixture } = await renderCarrusel();

    deslizar({ x: 200, y: 200 }, { x: 600, y: 200 });
    await fixture.whenStable();

    expect(indiceVisible()).toBe(3);
  });

  /**
   * El umbral es la sexta parte del ancho. Sin él, rozar la foto al apoyar el pulgar pasaría de
   * pieza, y quien lee no se enteraría de por qué se le movió lo que estaba mirando.
   */
  it('un roce no llega a cambiar de pieza', async () => {
    const { fixture } = await renderCarrusel();

    deslizar({ x: 400, y: 200 }, { x: 350, y: 200 });
    await fixture.whenStable();

    expect(indiceVisible()).toBe(0);
  });

  /**
   * Bajar la página con el dedo apoyado en la fotografía es un gesto vertical, y el carrusel se
   * aparta: decide el eje en los primeros píxeles y suelta el gesto si no es el suyo. Lo que hace
   * `touch-action: pan-y` en el navegador de verdad no se puede comprobar en jsdom; lo que sí es
   * que un movimiento sobre todo vertical no mueve la tira.
   */
  it('bajar la página con el dedo encima no mueve el carrusel', async () => {
    const { fixture } = await renderCarrusel();

    deslizar({ x: 400, y: 500 }, { x: 360, y: 100 });
    await fixture.whenStable();

    expect(indiceVisible()).toBe(0);
  });

  /**
   * Y lo deja como estaba, que es la otra mitad: el temporizador se apaga al empezar cualquier
   * gesto, y un gesto vertical lo suelta antes de que llegue el `pointerup`. Sin volver a
   * programarlo ahí, bajar la página una vez con el pulgar apoyado en la fotografía dejaba el
   * carrusel quieto para siempre — y nadie lo habría achacado a ese roce.
   */
  it('después de desplazar la página el carrusel sigue pasando solo', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { fixture } = await renderCarrusel();

      deslizar({ x: 400, y: 500 }, { x: 360, y: 100 });
      await fixture.whenStable();
      expect(indiceVisible()).toBe(0);

      await vi.advanceTimersByTimeAsync(4000);
      await fixture.whenStable();

      expect(indiceVisible()).toBe(1);
    } finally {
      vi.useRealTimers();
    }
  });

  /**
   * El defecto que esta prueba fija: el botón de la diapositiva ocupa un buen trozo de la pieza, y
   * un deslizamiento que empieza encima de él termina con un `click` que el navegador dispara
   * igual. Sin la guarda, deslizar pasaba de pieza <b>y</b> navegaba al catálogo filtrado.
   *
   * <p>Se mira si el clic <b>llega al enlace</b>, y no `defaultPrevented`, que fue el primer
   * intento y no distingue nada: `RouterLink` llama a `preventDefault()` él solo en cuanto se hace
   * cargo de la navegación, así que la bandera sale en `true` con guarda y sin ella. Lo que sí
   * separa los dos casos es que la guarda corta la propagación en fase de captura, o sea antes de
   * que el evento llegue a su destino.
   */
  it('el clic que sigue a un arrastre no llega al enlace', async () => {
    const { fixture } = await renderCarrusel();
    const boton = botonesDeLinea()[0];
    let recibidos = 0;
    boton.addEventListener('click', () => (recibidos += 1));

    puntero('pointerdown', { x: 600, y: 200 }, boton);
    puntero('pointermove', { x: 400, y: 200 }, boton);
    puntero('pointermove', { x: 200, y: 200 }, boton);
    puntero('pointerup', { x: 200, y: 200 }, boton);
    await fixture.whenStable();
    boton.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));

    expect(recibidos).toBe(0);
    expect(indiceVisible()).toBe(1);
  });

  /** Y el clic de después sí vale: la guarda dura un gesto, no toda la vida del componente. */
  it('un clic sin arrastre de por medio sí llega al enlace', async () => {
    await renderCarrusel();
    const boton = botonesDeLinea()[0];
    let recibidos = 0;
    boton.addEventListener('click', () => (recibidos += 1));

    boton.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));

    expect(recibidos).toBe(1);
  });

  /**
   * Un `pointercancel` aborta el gesto sin decidir nada, y esta prueba fija un defecto que solo
   * apareció en el navegador.
   *
   * <p>Al arrastrar empezando <b>encima del botón</b> —que es un `<a>`— Chrome arranca su arrastre
   * nativo de enlaces y manda `pointercancel` con coordenadas que no son las del dedo. Cuando el
   * cancel se trataba como un final, el carrusel leía ese salto como un gesto y se iba a la pieza
   * <b>contraria</b> a la que pedía la mano. Aquí se reproduce con un cancel que dice `x = 0`
   * después de un arrastre hacia la derecha: la pieza no se mueve.
   */
  it('un gesto cancelado no cambia de pieza, ni siquiera al lado contrario', async () => {
    const { fixture } = await renderCarrusel();

    puntero('pointerdown', { x: 300, y: 200 });
    puntero('pointermove', { x: 500, y: 200 });
    puntero('pointercancel', { x: 0, y: 0 });
    await fixture.whenStable();

    expect(indiceVisible()).toBe(0);
  });

  /**
   * El gesto reinicia la cuenta atrás, igual que pulsar una viñeta: si no, deslizar a la pieza que
   * quieres ver justo antes de que salte te deja medio segundo para leerla.
   */
  it('deslizar reinicia la cuenta atrás', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { fixture } = await renderCarrusel();

      await vi.advanceTimersByTimeAsync(3500);
      deslizar({ x: 600, y: 200 }, { x: 200, y: 200 });
      await fixture.whenStable();
      expect(indiceVisible()).toBe(1);

      // Lo que le quedaba a la cuenta anterior. Si no se hubiera reprogramado, ya estaría en la 2.
      await vi.advanceTimersByTimeAsync(1000);
      await fixture.whenStable();
      expect(indiceVisible()).toBe(1);

      await vi.advanceTimersByTimeAsync(3000);
      await fixture.whenStable();
      expect(indiceVisible()).toBe(2);
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

  /**
   * Dirección de arte, no dos tamaños de lo mismo: en teléfono va un recorte vertical 4:5 de la
   * misma fotografía, porque la pieza ancha a 390 px mide 160 px de alto y no cabe nada encima.
   *
   * <p>Lo que la prueba puede afirmar es que el `<source>` está bien escrito. **Cuál de los dos
   * descarga el navegador no lo ve jsdom**, que no evalúa `media` ni `sizes`: eso se comprueba en el
   * navegador, y `apps/web/CLAUDE.md` lo tiene en la lista de las cuatro cosas que las pruebas no
   * atrapan.
   */
  it('ofrece un recorte vertical para el teléfono', async () => {
    const { container } = await renderCarrusel();

    const fuentes = [...container.querySelectorAll('picture source')];
    expect(fuentes).toHaveLength(4);
    expect(fuentes.map((fuente) => fuente.getAttribute('srcset'))).toEqual([
      '/imagenes/portada/hero/vertical/vertical-ropa-1000.webp',
      '/imagenes/portada/hero/vertical/vertical-calzado-1000.webp',
      '/imagenes/portada/hero/vertical/vertical-bolsos-1000.webp',
      '/imagenes/portada/hero/vertical/vertical-tecnologia-1000.webp',
    ]);
    expect(fuentes.every((fuente) => fuente.getAttribute('height') === '1250')).toBe(true);
  });
});
