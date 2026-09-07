import { Component, inject } from '@angular/core';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render } from '@testing-library/angular';
import { Carrito } from '../domain/carrito.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../domain/repositorio-carrito.puerto';
import { SnapshotLinea } from '../domain/snapshot-linea.model';
import { CarritoStore } from './carrito.store';
import { proveerAlmacenesCarrito } from '../../../../testing/carrito';

class RepositorioCarritoFalso implements RepositorioCarrito {
  llamadasCrear = 0;
  private carrito: Carrito = {
    id: 'carrito-1',
    usuarioId: null,
    lineas: [],
    creadoEn: '2026-01-01T00:00:00Z',
  };

  async crear(): Promise<Carrito> {
    this.llamadasCrear++;
    return this.carrito;
  }

  async ver(carritoId: string): Promise<Carrito | null> {
    return carritoId === this.carrito.id ? this.carrito : null;
  }

  async agregarLinea(carritoId: string, varianteId: string, cantidad: number): Promise<Carrito> {
    const existente = this.carrito.lineas.find((l) => l.varianteId === varianteId);
    const lineas = existente
      ? this.carrito.lineas.map((l) =>
          l.varianteId === varianteId ? { ...l, cantidad: l.cantidad + cantidad } : l,
        )
      : [...this.carrito.lineas, { id: `linea-${varianteId}`, varianteId, cantidad }];
    this.carrito = { ...this.carrito, lineas };
    return this.carrito;
  }

  async actualizarCantidad(carritoId: string, lineaId: string, cantidad: number): Promise<Carrito> {
    this.carrito = {
      ...this.carrito,
      lineas: this.carrito.lineas.map((l) => (l.id === lineaId ? { ...l, cantidad } : l)),
    };
    return this.carrito;
  }

  async eliminarLinea(carritoId: string, lineaId: string): Promise<Carrito> {
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
 * El adaptador de Angular de TanStack Query registra sus tareas pendientes dentro de un `effect()`
 * agendado async (mismo hallazgo de ADR-0011, acá en el momento en que la consulta del carrito pasa
 * de deshabilitada a habilitada). `fixture.whenStable()` no alcanza a esperar ese registro — una
 * pequeña espera real, mismo recurso que ya usa `filtros-productos.spec.ts` para el debounce.
 */

@Component({ selector: 'app-anfitrion-de-prueba', template: '' })
class AnfitrionDePrueba {
  readonly store = inject(CarritoStore);
}

async function renderConRepositorio(repositorio: RepositorioCarrito) {
  const { fixture } = await render(AnfitrionDePrueba, {
    providers: [
      ...proveerAlmacenesCarrito(),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_CARRITO, useValue: repositorio },
    ],
  });
  return { store: fixture.componentInstance.store, fixture };
}

describe('CarritoStore', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('agregarAlCarrito crea un carrito la primera vez que se agrega algo', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const { store } = await renderConRepositorio(repositorio);

    await store.agregarAlCarrito('variante-1', 2, snapshotDePrueba('variante-1'));
    await vi.waitFor(() => expect(repositorio.llamadasCrear).toBe(1));
    expect(store.carritoId()).toBe('carrito-1');
  });

  it('agregar una segunda vez no vuelve a crear el carrito', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const { store } = await renderConRepositorio(repositorio);

    await store.agregarAlCarrito('variante-1', 1, snapshotDePrueba('variante-1'));
    // Se espera a que el carrito exista, no a que pasen 100 ms: la segunda
    // llamada solo tiene sentido cuando la primera ya lo creó.
    await vi.waitFor(() => expect(store.carritoId()).not.toBeNull());
    await store.agregarAlCarrito('variante-2', 1, snapshotDePrueba('variante-2'));
    await vi.waitFor(() => expect(repositorio.llamadasCrear).toBe(1));
  });

  it('cantidadTotal suma la cantidad de todas las líneas', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const { store } = await renderConRepositorio(repositorio);

    await store.agregarAlCarrito('variante-1', 2, snapshotDePrueba('variante-1'));
    await vi.waitFor(() => expect(store.carritoId()).not.toBeNull());
    await store.agregarAlCarrito('variante-2', 3, snapshotDePrueba('variante-2'));
    await vi.waitFor(() => expect(store.cantidadTotal()).toBe(5));
  });

  it('eliminarLinea la quita y cantidadTotal baja', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const { store } = await renderConRepositorio(repositorio);
    await store.agregarAlCarrito('variante-1', 2, snapshotDePrueba('variante-1'));
    // Se espera a que la línea exista: es lo que la siguiente instrucción lee.
    await vi.waitFor(() => expect(store.consulta.data()?.lineas.length).toBeGreaterThan(0));
    const lineaId = store.consulta.data()?.lineas[0].id as string;

    await store.eliminarLinea(lineaId);
    await vi.waitFor(() => expect(store.cantidadTotal()).toBe(0));
  });
});
