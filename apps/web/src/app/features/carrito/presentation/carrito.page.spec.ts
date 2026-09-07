import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../assets/i18n/en.json';
import es from '../../../../assets/i18n/es.json';
import esCarrito from '../../../../assets/i18n/scopes/carrito/es.json';
import { Carrito } from '../domain/carrito.model';
import { SnapshotLinea } from '../domain/snapshot-linea.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../domain/repositorio-carrito.puerto';
import { CarritoStore } from '../application/carrito.store';
import { CarritoPage } from './carrito.page';
import { esperarSinViolaciones } from '../../../../testing/axe';
import { proveerAlmacenesCarrito, sembrarCarritoId, sembrarSnapshotLinea } from '../../../../testing/carrito';

class RepositorioCarritoFalso implements RepositorioCarrito {
  constructor(private carrito: Carrito | null) {}

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
    imagenAlt: 'Morral urbano',
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
    expect(screen.getAllByText(/300\.000/).length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Ir a pagar' })).toBeTruthy();
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

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }));
    await vi.waitFor(() => expect(screen.queryByText('Morral urbano')).toBeNull());

    expect(screen.queryByText('Morral urbano')).toBeFalsy();
    expect(await screen.findByText('Tu carrito está vacío.')).toBeTruthy();
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
