import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../assets/i18n/en.json';
import es from '../../../../assets/i18n/es.json';
import esCarrito from '../../../../assets/i18n/scopes/carrito/es.json';
import { Carrito, CarritoCotizado } from '../domain/carrito.model';
import { SnapshotLinea } from '../domain/snapshot-linea.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../domain/repositorio-carrito.puerto';
import { CarritoStore } from '../application/carrito.store';
import { CarritoPage } from './carrito.page';
import { esperarSinViolaciones } from '../../../../testing/axe';
import {
  proveerAlmacenesCarrito,
  sembrarCarritoId,
  sembrarSnapshotLinea,
} from '../../../../testing/carrito';

/** El precio que el servidor da hoy en estas pruebas. */
const PRECIO_DE_HOY = 150_000;

class RepositorioCarritoFalso implements RepositorioCarrito {
  /** Los precios de hoy, como el servidor: {@link PRECIO_DE_HOY} por unidad. */
  async cotizar(carritoId: string): Promise<CarritoCotizado | null> {
    const carrito = await this.ver(carritoId);
    if (!carrito) {
      return null;
    }
    const lineas = carrito.lineas.map((linea) => ({
      lineaId: linea.id,
      varianteId: linea.varianteId,
      cantidad: linea.cantidad,
      precioUnitario: PRECIO_DE_HOY,
      subtotal: PRECIO_DE_HOY * linea.cantidad,
      precioContado: this.contado,
      subtotalContado: this.contado * linea.cantidad,
    }));
    return {
      lineas,
      subtotal: lineas.reduce((suma, linea) => suma + linea.subtotal, 0),
      subtotalContado: lineas.reduce((suma, linea) => suma + linea.subtotalContado, 0),
    };
  }
  /** `contado` por debajo de {@link PRECIO_DE_HOY} es un producto con descuento sin pasarela. */
  constructor(
    protected carrito: Carrito | null,
    private readonly contado = PRECIO_DE_HOY,
  ) {}

  async crear(): Promise<Carrito> {
    throw new Error('no usado en esta prueba');
  }

  async ver(carritoId: string): Promise<Carrito | null> {
    return this.carrito && this.carrito.id === carritoId ? this.carrito : null;
  }

  async agregarLinea(): Promise<Carrito> {
    throw new Error('no usado en esta prueba');
  }

  async actualizarCantidad(): Promise<Carrito> {
    throw new Error('no usado en esta prueba');
  }

  async eliminarLinea(carritoId: string, lineaId: string): Promise<Carrito> {
    if (!this.carrito) {
      throw new Error('no hay carrito');
    }
    this.carrito = { ...this.carrito, lineas: this.carrito.lineas.filter((l) => l.id !== lineaId) };
    return this.carrito;
  }
}

function snapshotDePrueba(varianteId: string): SnapshotLinea {
  return {
    varianteId,
    nombreProducto: 'Morral urbano',
    slugProducto: 'morral-urbano',
    sku: 'SKU-1',
    imagenUrl: null,
    precioValor: 150_000,
    precioMoneda: 'COP',
  };
}

/**
 * Mismo hallazgo que en carrito.store.spec.ts: el registro de `PendingTasks` de TanStack Query
 * ocurre dentro de un `effect()` async, así que `fixture.whenStable()` no alcanza a esperarlo.
 */

async function renderCarrito(repositorio: RepositorioCarrito) {
  return render(CarritoPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'carrito/es': esCarrito } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      ...proveerAlmacenesCarrito(),
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_CARRITO, useValue: repositorio },
    ],
  });
}

/** Acepta el cambio de cantidad, como un backend sano. */
class RepositorioCarritoQueActualiza extends RepositorioCarritoFalso {
  // Opcionales porque el doble base no los declara; el store siempre los manda.
  override async actualizarCantidad(
    _carritoId?: string,
    lineaId?: string,
    cantidad?: number,
  ): Promise<Carrito> {
    if (!this.carrito || cantidad === undefined) {
      throw new Error('no hay carrito');
    }
    this.carrito = {
      ...this.carrito,
      lineas: this.carrito.lineas.map((l) => (l.id === lineaId ? { ...l, cantidad } : l)),
    };
    return this.carrito;
  }
}

describe('CarritoPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('sin carrito guardado, muestra el mensaje de vacío y un enlace al catálogo', async () => {
    await renderCarrito(new RepositorioCarritoFalso(null));
    expect(await screen.findByText('Tu carrito está vacío.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ir al catálogo' })).toBeTruthy();
  });

  it('con líneas, muestra cada producto y el total', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const carrito: Carrito = {
      id: 'carrito-1',
      usuarioId: null,
      creadoEn: '2026-01-01T00:00:00Z',
      lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 2 }],
    };

    await renderCarrito(new RepositorioCarritoFalso(carrito));

    expect(await screen.findByText('Morral urbano')).toBeTruthy();
    expect((await screen.findAllByText(/300\.000/)).length).toBeGreaterThan(0);
    // Un enlace, no un botón: lo busca así el recorrido de compra de Playwright (`e2e/compra`).
    expect(screen.getByRole('link', { name: 'Ir a pagar' })).toBeTruthy();
    // Con una sola línea no hay nada que elegir.
    expect(screen.queryByRole('checkbox')).toBeNull();
    expect(screen.getByRole('link', { name: 'Seguir comprando' })).toBeTruthy();
  });

  /**
   * El precio guardado al agregar puede estar viejo: el total que se muestra es el del servidor.
   * Antes se sumaba el guardado, y el comprador aceptaba un total y se le cobraba otro.
   */
  it('el total sale de la cotización del servidor, no del precio guardado al agregar', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea({ ...snapshotDePrueba('variante-1'), precioValor: 99_000 });
    const carrito: Carrito = {
      id: 'carrito-1',
      usuarioId: null,
      creadoEn: '2026-01-01T00:00:00Z',
      lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 2 }],
    };

    await renderCarrito(new RepositorioCarritoFalso(carrito));

    expect((await screen.findAllByText(/300\.000/)).length).toBeGreaterThan(0);
    expect(screen.queryByText(/198\.000/)).toBeNull();
  });

  // El total del carrito es sin flete (docs/00-producto.md): el envío se
  // cotiza por destino en el checkout y se cobra aparte. Hay que decirlo antes
  // de que el total crezca, no después.
  it('con líneas, avisa que el envío se cotiza al pagar', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const carrito: Carrito = {
      id: 'carrito-1',
      usuarioId: null,
      creadoEn: '2026-01-01T00:00:00Z',
      lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 1 }],
    };

    await renderCarrito(new RepositorioCarritoFalso(carrito));

    expect(await screen.findByText(/El costo de envío se cotiza al pagar/)).toBeTruthy();
  });

  it('eliminar una línea la quita de la pantalla', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const carrito: Carrito = {
      id: 'carrito-1',
      usuarioId: null,
      creadoEn: '2026-01-01T00:00:00Z',
      lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 1 }],
    };

    await renderCarrito(new RepositorioCarritoFalso(carrito));
    await screen.findByText('Morral urbano');

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar Morral urbano del carrito' }));
    await vi.waitFor(() => expect(screen.queryByText('Morral urbano')).toBeNull());

    expect(screen.queryByText('Morral urbano')).toBeFalsy();
    expect(await screen.findByText('Tu carrito está vacío.')).toBeTruthy();
  });

  /**
   * Quitar una línea no decía nada, y el botón pulsado desaparecía con ella. Ahora la región
   * `status` —siempre en el DOM— lo anuncia, y el foco va al título cuando ya no queda ninguna.
   */
  it('al quitar la última línea lo anuncia y lleva el foco al título', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const carrito: Carrito = {
      id: 'carrito-1',
      usuarioId: null,
      creadoEn: '2026-01-01T00:00:00Z',
      lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 1 }],
    };
    await renderCarrito(new RepositorioCarritoFalso(carrito));
    await screen.findByText('Morral urbano');
    const region = screen.getByRole('status');

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar Morral urbano del carrito' }));

    await vi.waitFor(() => {
      expect(region.textContent).toContain('Morral urbano se quitó del carrito.');
      expect(document.activeElement).toBe(screen.getByRole('heading', { name: 'Carrito' }));
    });
  });

  it('si cambiar la cantidad falla, lo dice en vez de quedarse callado', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const carrito: Carrito = {
      id: 'carrito-1',
      usuarioId: null,
      creadoEn: '2026-01-01T00:00:00Z',
      lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 1 }],
    };
    // El doble rechaza `actualizarCantidad`, como un backend caído.
    await renderCarrito(new RepositorioCarritoFalso(carrito));
    await screen.findByText('Morral urbano');

    fireEvent.click(screen.getByRole('button', { name: 'Aumentar cantidad de Morral urbano' }));

    await vi.waitFor(() =>
      expect(screen.getByRole('status').textContent).toContain('No pudimos actualizar el carrito'),
    );
  });

  it('con un carrito vacío en el servidor, muestra el mensaje de vacío', async () => {
    sembrarCarritoId('carrito-1');
    const carrito: Carrito = {
      id: 'carrito-1',
      usuarioId: null,
      creadoEn: '2026-01-01T00:00:00Z',
      lineas: [],
    };

    await renderCarrito(new RepositorioCarritoFalso(carrito));

    expect(await screen.findByText('Tu carrito está vacío.')).toBeTruthy();
  });

  // `docs/06-testing.md`: axe automatizado en las pantallas clave.
  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const { container } = await renderCarrito(
      new RepositorioCarritoFalso({
        id: 'carrito-1',
        usuarioId: null,
        creadoEn: '2026-01-01T00:00:00Z',
        lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 2 }],
      }),
    );
    await screen.findByText('Morral urbano');

    await esperarSinViolaciones(container);
  });

  it('no vuelve a leer la foto de cada línea en cada ciclo de detección', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const { fixture } = await renderCarrito(
      new RepositorioCarritoFalso({
        id: 'carrito-1',
        usuarioId: null,
        creadoEn: '2026-01-01T00:00:00Z',
        lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 2 }],
      }),
    );
    await screen.findByText('Morral urbano');

    // Con la llamada en la plantilla esto crecía con cada ciclo: era una lectura de
    // `localStorage` por línea, siempre. Desde el `computed` no se recalcula si el carrito no
    // cambió, así que el espía no vuelve a verse llamado.
    const espia = vi.spyOn(fixture.debugElement.injector.get(CarritoStore), 'snapshotDeLinea');
    fixture.detectChanges();
    fixture.detectChanges();

    expect(espia).not.toHaveBeenCalled();
  });
});

/**
 * ADR-0095 y el artículo 26 de la Ley 1480: el total es el publicado y lo de pagar sin pasarela
 * se dice como ahorro, nunca como un segundo total.
 */
describe('CarritoPage y el ahorro sin pasarela', () => {
  const CARRITO: Carrito = {
    id: 'carrito-1',
    usuarioId: null,
    creadoEn: '2026-01-01T00:00:00Z',
    lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 2 }],
  };

  beforeEach(() => {
    window.localStorage.clear();
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
  });

  it('debajo del total dice cuánto se ahorra el carrito entero, sin un segundo total', async () => {
    await renderCarrito(new RepositorioCarritoFalso(CARRITO, 145_000));

    expect(
      await screen.findByText(
        /^Ahorras \$\s?10\.000 pagando por transferencia, en el punto o contra entrega$/,
      ),
    ).toBeTruthy();
    expect(screen.queryByText(/290\.000/)).toBeNull();
  });

  it('sin descuento no dice nada', async () => {
    await renderCarrito(new RepositorioCarritoFalso(CARRITO));

    await screen.findByText('Morral urbano');
    expect((await screen.findAllByText(/300\.000/)).length).toBeGreaterThan(0);
    expect(screen.queryByText(/Ahorras/)).toBeNull();
  });

  it('al cambiar la cantidad anuncia el total y el ahorro juntos', async () => {
    await renderCarrito(new RepositorioCarritoQueActualiza(CARRITO, 145_000));
    await screen.findByText('Morral urbano');

    fireEvent.click(screen.getByRole('button', { name: 'Aumentar cantidad de Morral urbano' }));

    await vi.waitFor(() => {
      const anuncio = screen.getByRole('status').textContent!.replace(/\s+/g, ' ');
      expect(anuncio).toMatch(/Total: \$\s?450\.000\. Ahorras \$\s?15\.000 pagando/);
    });
  });
});

/** Con varias líneas se elige qué se paga ahora; lo demás se queda en el carrito. */
describe('CarritoPage y la selección de lo que se paga', () => {
  beforeEach(() => {
    window.localStorage.clear();
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea({ ...snapshotDePrueba('variante-1'), nombreProducto: 'Morral urbano' });
    sembrarSnapshotLinea({ ...snapshotDePrueba('variante-2'), nombreProducto: 'Gorra plana' });
  });

  const carritoDeDos: Carrito = {
    id: 'carrito-1',
    usuarioId: null,
    creadoEn: '2026-01-01T00:00:00Z',
    lineas: [
      { id: 'linea-1', varianteId: 'variante-1', cantidad: 1 },
      { id: 'linea-2', varianteId: 'variante-2', cantidad: 1 },
    ],
  };

  const casilla = (nombre: string) =>
    screen.getByRole('checkbox', { name: nombre }) as HTMLInputElement;

  it('todas arrancan marcadas y el total es el de las dos', async () => {
    await renderCarrito(new RepositorioCarritoFalso(carritoDeDos));

    expect(await screen.findByText('2 de 2 productos en este pago')).toBeTruthy();
    expect(casilla('Incluir en este pago: Morral urbano').checked).toBe(true);
    expect(casilla('Incluir en este pago: Gorra plana').checked).toBe(true);
    expect(casilla('Seleccionar todos').checked).toBe(true);
    expect((await screen.findAllByText(/300\.000/)).length).toBeGreaterThan(0);
  });

  it('desmarcar una baja el total al de lo marcado', async () => {
    await renderCarrito(new RepositorioCarritoFalso(carritoDeDos));
    await screen.findByText('2 de 2 productos en este pago');

    fireEvent.click(casilla('Incluir en este pago: Gorra plana'));

    expect(await screen.findByText('1 de 2 productos en este pago')).toBeTruthy();
    expect(casilla('Seleccionar todos').checked).toBe(false);
    expect(screen.queryByText(/300\.000/)).toBeNull();
    // Se anuncia: el total cambia lejos de la casilla que se tocó.
    expect(screen.getByRole('status').textContent).toMatch(/1 de 2 productos en este pago/);
  });

  it('sin nada marcado, ir a pagar no es un enlace y dice qué falta', async () => {
    await renderCarrito(new RepositorioCarritoFalso(carritoDeDos));
    await screen.findByText('2 de 2 productos en este pago');

    fireEvent.click(casilla('Seleccionar todos'));
    expect(screen.queryByRole('link', { name: 'Ir a pagar' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Ir a pagar' }));

    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      expect.stringContaining('Marca al menos un producto para ir a pagar.'),
    );
  });

  it('con algo marcado, ir a pagar es el enlace al checkout', async () => {
    await renderCarrito(new RepositorioCarritoFalso(carritoDeDos));
    await screen.findByText('2 de 2 productos en este pago');

    fireEvent.click(casilla('Incluir en este pago: Morral urbano'));

    expect(screen.getByRole('link', { name: 'Ir a pagar' }).getAttribute('href')).toMatch(
      /\/checkout$/,
    );
    expect(screen.queryByRole('button', { name: 'Ir a pagar' })).toBeNull();
  });
});
