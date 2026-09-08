import { Component, signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { TsVisor360 } from './ts-visor-360';

const OCHO_FOTOGRAMAS = Array.from({ length: 8 }, (_, orden) => `https://imagenes.test/f${orden}.webp`);

/** Un set distinto, para las pruebas de qué pasa cuando el visor cambia de producto. */
const OTROS_OCHO = Array.from({ length: 8 }, (_, orden) => `https://imagenes.test/otro-f${orden}.webp`);

const ANCHO_MARCO = 400;
/** Un octavo del ancho con ocho fotogramas: exactamente un paso. */
const UN_PASO = ANCHO_MARCO / 8;

@Component({
  imports: [TsVisor360],
  template: `<ts-visor-360 [imagenes]="imagenes()" />`,
})
class AnfitrionDePrueba {
  readonly imagenes = signal<readonly string[]>(OCHO_FOTOGRAMAS);
}

/**
 * jsdom no descarga imágenes, así que sin este doble `cargados` se quedaría solo con el fotograma
 * 0 y el visor mostraría siempre el mismo — que es justo lo que hace de verdad mientras la
 * precarga no ha llegado. Aquí se simula que sí llegan.
 */
class ImagenQueSiCarga {
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  private _src = '';

  set src(valor: string) {
    this._src = valor;
    queueMicrotask(() => this.onload?.());
  }

  get src(): string {
    return this._src;
  }
}

/**
 * Doble de `Image` que no carga sola: la prueba decide cuándo llega cada una. Es la única forma de
 * reproducir el caso que importa — una imagen del set anterior que llega *después* de que el visor
 * ya cambió de set.
 */
class ImagenManual {
  static solicitadas: string[] = [];
  static pendientes = new Map<string, ImagenManual>();

  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  private _src = '';

  set src(valor: string) {
    this._src = valor;
    ImagenManual.solicitadas.push(valor);
    ImagenManual.pendientes.set(valor, this);
  }

  get src(): string {
    return this._src;
  }

  static reiniciar(): void {
    ImagenManual.solicitadas = [];
    ImagenManual.pendientes = new Map();
  }

  static completar(url: string): void {
    const imagen = ImagenManual.pendientes.get(url);
    ImagenManual.pendientes.delete(url);
    imagen?.onload?.();
  }
}

/**
 * Doble de `Image` que nunca carga: reproduce un objeto roto en el bucket —un set `PUBLICADO` cuyo
 * fotograma devuelve 404— que es el caso que el visor tapaba sin dejar rastro.
 */
class ImagenQueFalla {
  static solicitadas: string[] = [];

  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  private _src = '';

  set src(valor: string) {
    this._src = valor;
    ImagenQueFalla.solicitadas.push(valor);
    queueMicrotask(() => this.onerror?.());
  }

  get src(): string {
    return this._src;
  }

  static reiniciar(): void {
    ImagenQueFalla.solicitadas = [];
  }
}

async function renderVisor() {
  const resultado = await render(AnfitrionDePrueba, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en },
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
  await resultado.fixture.whenStable();
  return resultado;
}

function marco(): HTMLElement {
  return screen.getByRole('group', { name: 'Vista 360 del producto' });
}

function fotograma(): HTMLImageElement {
  const imagen = marco().querySelector('img');
  if (!imagen) {
    throw new Error('el visor no pintó ningún fotograma');
  }
  return imagen as HTMLImageElement;
}

/** `PointerEvent` no existe en jsdom; `MouseEvent` sí trae `clientX`/`clientY` de verdad. */
function puntero(tipo: string, posicion: { x: number; y: number }): void {
  const evento = new MouseEvent(tipo, { clientX: posicion.x, clientY: posicion.y, bubbles: true });
  Object.defineProperty(evento, 'pointerId', { value: 1 });
  marco().dispatchEvent(evento);
}

function arrastrar(desde: { x: number; y: number }, hasta: { x: number; y: number }): void {
  puntero('pointerdown', desde);
  puntero('pointermove', hasta);
  puntero('pointerup', hasta);
}

describe('TsVisor360', () => {
  beforeEach(() => {
    vi.stubGlobal('Image', ImagenQueSiCarga);
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: ANCHO_MARCO,
      height: ANCHO_MARCO,
      top: 0,
      left: 0,
      right: ANCHO_MARCO,
      bottom: ANCHO_MARCO,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('arranca en el fotograma frontal', async () => {
    await renderVisor();

    expect(fotograma().getAttribute('src')).toContain('f0.webp');
    expect(screen.getByText('Fotograma 1 de 8')).toBeTruthy();
  });

  it('la flecha derecha avanza un fotograma y la izquierda retrocede', async () => {
    const { fixture } = await renderVisor();

    fireEvent.keyDown(marco(), { key: 'ArrowRight' });
    await fixture.whenStable();
    expect(screen.getByText('Fotograma 2 de 8')).toBeTruthy();

    fireEvent.keyDown(marco(), { key: 'ArrowLeft' });
    await fixture.whenStable();
    expect(screen.getByText('Fotograma 1 de 8')).toBeTruthy();
  });

  it('el índice es circular: antes del frontal está el último', async () => {
    const { fixture } = await renderVisor();

    fireEvent.keyDown(marco(), { key: 'ArrowLeft' });
    await fixture.whenStable();

    expect(screen.getByText('Fotograma 8 de 8')).toBeTruthy();
  });

  it('Inicio vuelve al frontal y Fin va al opuesto', async () => {
    const { fixture } = await renderVisor();

    fireEvent.keyDown(marco(), { key: 'End' });
    await fixture.whenStable();
    expect(screen.getByText('Fotograma 5 de 8')).toBeTruthy();

    fireEvent.keyDown(marco(), { key: 'Home' });
    await fixture.whenStable();
    expect(screen.getByText('Fotograma 1 de 8')).toBeTruthy();
  });

  it('una tecla que el visor no usa no se consume', async () => {
    await renderVisor();

    const evento = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    marco().dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(false);
  });

  it('las teclas del visor sí se consumen, para que Inicio y Fin no desplacen la página', async () => {
    await renderVisor();

    const evento = new KeyboardEvent('keydown', { key: 'End', bubbles: true, cancelable: true });
    marco().dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(true);
  });

  it('los botones visibles giran en los dos sentidos', async () => {
    const { fixture } = await renderVisor();

    fireEvent.click(screen.getByRole('button', { name: 'Girar a la derecha' }));
    await fixture.whenStable();
    expect(screen.getByText('Fotograma 2 de 8')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Girar a la izquierda' }));
    await fixture.whenStable();
    expect(screen.getByText('Fotograma 1 de 8')).toBeTruthy();
  });

  it('arrastrar hacia la izquierda gira el producto', async () => {
    const { fixture } = await renderVisor();

    arrastrar({ x: 200, y: 100 }, { x: 200 - UN_PASO * 2, y: 100 });
    await fixture.whenStable();

    expect(screen.getByText('Fotograma 3 de 8')).toBeTruthy();
  });

  it('arrastrar hacia la derecha gira al otro lado, dando la vuelta', async () => {
    const { fixture } = await renderVisor();

    arrastrar({ x: 200, y: 100 }, { x: 200 + UN_PASO, y: 100 });
    await fixture.whenStable();

    expect(screen.getByText('Fotograma 8 de 8')).toBeTruthy();
  });

  it('el arrastre vertical no gira: es el desplazamiento de la página', async () => {
    const { fixture } = await renderVisor();

    arrastrar({ x: 200, y: 100 }, { x: 200, y: 400 });
    await fixture.whenStable();

    expect(screen.getByText('Fotograma 1 de 8')).toBeTruthy();
  });

  it('mover el puntero sin haber empezado un arrastre no gira nada', async () => {
    const { fixture } = await renderVisor();

    puntero('pointermove', { x: 0, y: 100 });
    await fixture.whenStable();

    expect(screen.getByText('Fotograma 1 de 8')).toBeTruthy();
  });

  it('el fotograma que se pinta cambia con el giro, no solo el contador', async () => {
    const { fixture } = await renderVisor();

    fireEvent.keyDown(marco(), { key: 'ArrowRight' });
    await fixture.whenStable();
    // La precarga es asíncrona: el fotograma se pinta cuando la imagen terminó de cargar.
    await new Promise((listo) => queueMicrotask(() => listo(null)));
    await fixture.whenStable();

    expect(fotograma().getAttribute('src')).toContain('f1.webp');
  });

  it('mientras el fotograma pedido no ha cargado se muestra el más cercano disponible', async () => {
    vi.stubGlobal(
      'Image',
      class ImagenQueNuncaCarga {
        onload: (() => void) | null = null;
        onerror: (() => void) | null = null;
        src = '';
      },
    );
    const { fixture } = await renderVisor();

    fireEvent.keyDown(marco(), { key: 'ArrowRight' });
    await fixture.whenStable();

    // El contador ya va en el 2, pero lo que se ve sigue siendo el único cargado: el frontal.
    expect(screen.getByText('Fotograma 2 de 8')).toBeTruthy();
    expect(fotograma().getAttribute('src')).toContain('f0.webp');
  });

  // El visor sustituye un fotograma que falta por el más cercano disponible, así que un objeto roto
  // en el bucket no se ve: el visitante gira y no nota que le falta una foto. Ese es el
  // comportamiento correcto de cara al visitante y el problema de cara a quien mantiene el catálogo.
  it('un fotograma que no carga deja rastro en la consola en vez de desaparecer en silencio', async () => {
    ImagenQueFalla.reiniciar();
    vi.stubGlobal('Image', ImagenQueFalla);
    const aviso = vi.spyOn(console, 'warn').mockImplementation(() => {});

    await renderVisor();

    await vi.waitFor(() => {
      const mensajes = aviso.mock.calls.map(([mensaje]) => String(mensaje));
      expect(mensajes.some((mensaje) => mensaje.includes('https://imagenes.test/f1.webp'))).toBe(true);
      expect(mensajes.some((mensaje) => mensaje.includes('no cargó'))).toBe(true);
    });
  });

  // Dejar rastro no puede costar la tolerancia que ya existía: el fotograma roto se registra y la
  // cadena sigue con el siguiente. Si el aviso se colara antes de continuar la cadena, la precarga
  // se detendría en la primera imagen que falla y el visor se quedaría con un solo fotograma.
  it('la cadena de precarga no se detiene en el fotograma roto', async () => {
    ImagenQueFalla.reiniciar();
    vi.stubGlobal('Image', ImagenQueFalla);
    vi.spyOn(console, 'warn').mockImplementation(() => {});

    await renderVisor();

    await vi.waitFor(() => {
      expect(new Set(ImagenQueFalla.solicitadas).size).toBe(OCHO_FOTOGRAMAS.length);
    });
  });

  it('la pista de arrastre desaparece con la primera interacción', async () => {
    const { fixture } = await renderVisor();

    expect(screen.getByText('Arrastra para girar')).toBeTruthy();

    fireEvent.keyDown(marco(), { key: 'ArrowRight' });
    await fixture.whenStable();

    expect(screen.queryByText('Arrastra para girar')).toBeNull();
  });

  // No basta con que se vaya al interactuar: si nadie toca el visor, un texto permanente encima de
  // la imagen es justo lo que `docs/10-captura-360.md` prohíbe.
  it('la pista se va sola aunque nadie toque el visor', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { fixture } = await renderVisor();
      expect(screen.getByText('Arrastra para girar')).toBeTruthy();

      await vi.advanceTimersByTimeAsync(4000);
      await fixture.whenStable();

      expect(screen.queryByText('Arrastra para girar')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('el marco es enfocable y tiene nombre accesible e instrucciones en texto', async () => {
    await renderVisor();

    expect(marco().getAttribute('tabindex')).toBe('0');
    expect(
      screen.getByText('Arrastra sobre la imagen, o usa las flechas izquierda y derecha, para girar el producto.'),
    ).toBeTruthy();
  });

  // Sin esto, quien usa un lector de pantalla enfoca el visor, oye su nombre y no se entera de que
  // las flechas giran: el texto está en pantalla pero no asociado al control.
  it('las instrucciones están asociadas al marco, no solo escritas al lado', async () => {
    await renderVisor();

    const idDescripcion = marco().getAttribute('aria-describedby');
    expect(idDescripcion).toBeTruthy();

    const descripcion = document.getElementById(idDescripcion!);
    expect(descripcion?.textContent?.trim()).toBe(
      'Arrastra sobre la imagen, o usa las flechas izquierda y derecha, para girar el producto.',
    );
  });

  it('dos visores en la misma página no comparten el id de sus instrucciones', async () => {
    const { fixture } = await render(
      `<ts-visor-360 [imagenes]="imagenes" /><ts-visor-360 [imagenes]="imagenes" />`,
      {
        imports: [
          TsVisor360,
          TranslocoTestingModule.forRoot({
            langs: { es, en },
            translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
            preloadLangs: true,
          }),
        ],
        componentProperties: { imagenes: OCHO_FOTOGRAMAS },
      },
    );
    await fixture.whenStable();

    const [uno, otro] = screen.getAllByRole('group', { name: 'Vista 360 del producto' });
    expect(uno.getAttribute('aria-describedby')).not.toBe(otro.getAttribute('aria-describedby'));
  });

  it('los fotogramas son decorativos en conjunto: van sin texto alternativo', async () => {
    await renderVisor();

    expect(fotograma().getAttribute('alt')).toBe('');
  });

  // Mismo defecto que ya se corrigió en ts-paginador ("Página 1 de 0"): unos controles que no
  // llevan a ninguna parte y un contador que miente. Aquí no hay backend que lo impida — el
  // asistente de captura va a pasar sets a medio armar.
  it('sin fotogramas no pinta nada, ni contador ni botones', async () => {
    const { fixture } = await renderVisor();

    fixture.componentInstance.imagenes.set([]);
    await fixture.whenStable();

    expect(screen.queryByRole('group', { name: 'Vista 360 del producto' })).toBeNull();
    expect(screen.queryByText(/Fotograma/)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Girar a la derecha' })).toBeNull();
  });

  it('con un solo fotograma tampoco: una imagen suelta no es una rotación', async () => {
    const { fixture } = await renderVisor();

    fixture.componentInstance.imagenes.set([OCHO_FOTOGRAMAS[0]]);
    await fixture.whenStable();

    expect(screen.queryByRole('group', { name: 'Vista 360 del producto' })).toBeNull();
    expect(screen.queryByText('Fotograma 1 de 1')).toBeNull();
  });

  it('con dos fotogramas ya hay algo que girar', async () => {
    const { fixture } = await renderVisor();

    fixture.componentInstance.imagenes.set(OCHO_FOTOGRAMAS.slice(0, 2));
    await fixture.whenStable();

    expect(screen.getByText('Fotograma 1 de 2')).toBeTruthy();
  });

  // La carrera real: se cambia de producto con una imagen del anterior todavía en vuelo. Cuando
  // llega, no puede hablar por el fotograma del mismo índice del set nuevo, que nadie pidió.
  it('una imagen del set anterior que llega tarde no se da por disponible en el nuevo', async () => {
    ImagenManual.reiniciar();
    vi.stubGlobal('Image', ImagenManual);

    const { fixture } = await renderVisor();
    const vecinoDelViejo = OCHO_FOTOGRAMAS[1];
    // La cadena de precarga pidió el vecino del frontal y todavía no ha llegado.
    expect(ImagenManual.solicitadas).toContain(vecinoDelViejo);

    fixture.componentInstance.imagenes.set(OTROS_OCHO);
    await fixture.whenStable();

    ImagenManual.completar(vecinoDelViejo);
    await fixture.whenStable();

    fireEvent.keyDown(marco(), { key: 'ArrowRight' });
    await fixture.whenStable();

    // El contador va en el 2, pero el fotograma 1 del set nuevo no ha cargado: lo que se pinta es
    // el más cercano disponible, que es su frontal. Con los índices como llave, aquí se pintaba el
    // fotograma 1 del set nuevo, sin cargar.
    expect(screen.getByText('Fotograma 2 de 8')).toBeTruthy();
    expect(fotograma().getAttribute('src')).toContain('otro-f0');
  });

  it('la cadena de precarga del set anterior se abandona al cambiar de set', async () => {
    ImagenManual.reiniciar();
    vi.stubGlobal('Image', ImagenManual);

    const esDelViejo = (url: string) => OCHO_FOTOGRAMAS.includes(url);
    const { fixture } = await renderVisor();
    const vecinoDelViejo = OCHO_FOTOGRAMAS[1];
    expect(ImagenManual.solicitadas).toContain(vecinoDelViejo);

    fixture.componentInstance.imagenes.set(OTROS_OCHO);
    await fixture.whenStable();
    const pedidasDelViejo = ImagenManual.solicitadas.filter(esDelViejo).length;

    // Llega el eslabón que estaba en vuelo. Si la cadena vieja siguiera viva, encadenaría el
    // siguiente fotograma del set que ya nadie está mirando.
    ImagenManual.completar(vecinoDelViejo);
    await fixture.whenStable();

    expect(ImagenManual.solicitadas.filter(esDelViejo)).toHaveLength(pedidasDelViejo);
    expect(ImagenManual.solicitadas.filter((url) => OTROS_OCHO.includes(url)).length).toBeGreaterThan(0);
  });

  it('un set nuevo vuelve al frontal', async () => {
    const { fixture } = await renderVisor();

    fireEvent.keyDown(marco(), { key: 'ArrowRight' });
    await fixture.whenStable();
    expect(screen.getByText('Fotograma 2 de 8')).toBeTruthy();

    fixture.componentInstance.imagenes.set(OCHO_FOTOGRAMAS.slice(0, 4));
    await fixture.whenStable();

    expect(screen.getByText('Fotograma 1 de 4')).toBeTruthy();
  });
});
