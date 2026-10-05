import { Component, inject } from '@angular/core';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render } from '@testing-library/angular';
import { CarritoInexistenteError } from '../domain/carrito.errores';
import { Carrito, CarritoCotizado } from '../domain/carrito.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../domain/repositorio-carrito.puerto';
import { SnapshotLinea } from '../domain/snapshot-linea.model';
import { CarritoIdLocalStorageAlmacen } from '../infrastructure/carrito-id.almacen';
import { CarritoStore } from './carrito.store';
import { proveerAlmacenesCarrito } from '../../../../testing/carrito';

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
    }));
    return { lineas, subtotal: lineas.reduce((suma, linea) => suma + linea.subtotal, 0) };
  }
  llamadasCrear = 0;
  /**
   * Ids que el servidor ya purgó. `ver` los sigue reconociendo solo si están en
   * {@link vistosAntesDePurgar}: es el carrito que la página cargó y se purgó antes del clic.
   */
  readonly purgados = new Set<string>();
  readonly vistosAntesDePurgar = new Set<string>();
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
    if (this.vistosAntesDePurgar.has(carritoId)) {
      return { id: carritoId, usuarioId: null, lineas: [], creadoEn: '2026-01-01T00:00:00Z' };
    }
    return carritoId === this.carrito.id ? this.carrito : null;
  }

  async agregarLinea(carritoId: string, varianteId: string, cantidad: number): Promise<Carrito> {
    if (this.purgados.has(carritoId)) {
      throw new CarritoInexistenteError();
    }
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
    precioValor: 150_000,
    precioMoneda: 'COP',
  };
}

/**
 * El adaptador de Angular de TanStack Query registra sus tareas pendientes dentro de un `effect()`
 * agendado async (mismo hallazgo de ADR-0011, acá en el momento en que la consulta del carrito pasa
 * de deshabilitada a habilitada). `fixture.whenStable()` no alcanza a esperar ese registro, así que
 * se espera por el resultado con `vi.waitFor`, nunca por un tiempo fijo.
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

  it('limpiar deja el carrito sin id, sin líneas y sin nada guardado', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const { store } = await renderConRepositorio(repositorio);
    await store.agregarAlCarrito('variante-1', 2, snapshotDePrueba('variante-1'));
    await vi.waitFor(() => expect(store.cantidadTotal()).toBe(2));

    store.limpiar();

    expect(store.carritoId()).toBeNull();
    expect(store.cantidadTotal()).toBe(0);
    expect(new CarritoIdLocalStorageAlmacen().leer()).toBeNull();
  });

  /**
   * Encontrado en dev: el id guardado era de un carrito que la purga de inactivos ya había borrado,
   * y "Agregar al carrito" respondía 404 cada vez, sin salida salvo borrar `localStorage` a mano.
   */
  it('si el carrito guardado se purgó antes del clic, agregar sigue con uno nuevo', async () => {
    new CarritoIdLocalStorageAlmacen().guardar('carrito-purgado');
    const repositorio = new RepositorioCarritoFalso();
    repositorio.purgados.add('carrito-purgado');
    repositorio.vistosAntesDePurgar.add('carrito-purgado');
    const { store } = await renderConRepositorio(repositorio);
    await vi.waitFor(() => expect(store.carritoId()).toBe('carrito-purgado'));

    await store.agregarAlCarrito('variante-1', 1, snapshotDePrueba('variante-1'));

    await vi.waitFor(() => expect(store.cantidadTotal()).toBe(1));
    expect(store.carritoId()).toBe('carrito-1');
    expect(repositorio.llamadasCrear).toBe(1);
    expect(new CarritoIdLocalStorageAlmacen().leer()).toBe('carrito-1');
  });

  it('un id guardado que el servidor ya no conoce se suelta al cargar', async () => {
    new CarritoIdLocalStorageAlmacen().guardar('carrito-purgado');
    const { store } = await renderConRepositorio(new RepositorioCarritoFalso());

    await vi.waitFor(() => expect(new CarritoIdLocalStorageAlmacen().leer()).toBeNull());
    expect(store.carritoId()).toBeNull();
  });

  it('otro error al agregar no suelta el carrito', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const { store } = await renderConRepositorio(repositorio);
    await store.agregarAlCarrito('variante-1', 1, snapshotDePrueba('variante-1'));
    await vi.waitFor(() => expect(store.carritoId()).toBe('carrito-1'));
    repositorio.agregarLinea = async () => {
      throw new Error('sin existencias');
    };

    await expect(
      store.agregarAlCarrito('variante-2', 1, snapshotDePrueba('variante-2')),
    ).rejects.toThrow('sin existencias');
    expect(store.carritoId()).toBe('carrito-1');
    expect(repositorio.llamadasCrear).toBe(1);
  });
});
