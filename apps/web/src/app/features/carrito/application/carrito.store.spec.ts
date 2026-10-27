import { Component, inject } from '@angular/core';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render } from '@testing-library/angular';
import { CarritoInexistenteError } from '../domain/carrito.errores';
import { Carrito, CarritoCotizado } from '../domain/carrito.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../domain/repositorio-carrito.puerto';
import { SnapshotLinea } from '../domain/snapshot-linea.model';
import { CarritoIdLocalStorageAlmacen } from '../infrastructure/carrito-id.almacen';
import { CarritoStore } from './carrito.store';
import { LineasSinMarcarLocalStorageAlmacen } from '../infrastructure/lineas-sin-marcar.almacen';
import { proveerAlmacenesCarrito, sembrarLineasSinMarcar } from '../../../../testing/carrito';

/** El precio que el servidor da hoy en estas pruebas. */
const PRECIO_DE_HOY = 150_000;

class RepositorioCarritoFalso implements RepositorioCarrito {
  /** Los precios de hoy, como el servidor: {@link PRECIO_DE_HOY} por unidad. */
  async cotizar(carritoId: string): Promise<CarritoCotizado | null> {
    const carrito = await this.ver(carritoId);
    if (!carrito) {
      return null;
    }
    const lineas = carrito.lineas.map((linea) => {
      const seVende = !this.sinPrecio.has(linea.varianteId);
      return {
        lineaId: linea.id,
        varianteId: linea.varianteId,
        cantidad: linea.cantidad,
        precioUnitario: seVende ? PRECIO_DE_HOY : null,
        subtotal: seVende ? PRECIO_DE_HOY * linea.cantidad : null,
        precioContado: seVende ? this.contado : null,
        subtotalContado: seVende ? this.contado * linea.cantidad : null,
      };
    });
    return {
      lineas,
      subtotal: lineas.reduce((suma, linea) => suma + (linea.subtotal ?? 0), 0),
      subtotalContado: lineas.reduce((suma, linea) => suma + (linea.subtotalContado ?? 0), 0),
    };
  }
  /** Variantes que ya no se venden: la cotización las trae sin precio. */
  readonly sinPrecio = new Set<string>();
  llamadasEliminar = 0;
  readonly actualizadas: { lineaId: string; cantidad: number }[] = [];
  /** Lo que otra pestaña le hizo al carrito sin que esta se enterara: la caché no lo tiene. */
  cambiarEnOtraPestana(lineas: Carrito['lineas']): void {
    this.carrito = { ...this.carrito, lineas };
  }
  /** El precio de contado por unidad; por debajo del de hoy, hay descuento sin pasarela. */
  contado = PRECIO_DE_HOY;
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
    this.actualizadas.push({ lineaId, cantidad });
    this.carrito = {
      ...this.carrito,
      lineas: this.carrito.lineas.map((l) => (l.id === lineaId ? { ...l, cantidad } : l)),
    };
    return this.carrito;
  }

  async eliminarLinea(carritoId: string, lineaId: string): Promise<Carrito> {
    this.llamadasEliminar++;
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

  /** ADR-0095: el ahorro sin pasarela es la resta de los dos subtotales del servidor. */
  it('ahorroSinPasarela es el subtotal publicado menos el de contado', async () => {
    const repositorio = new RepositorioCarritoFalso();
    repositorio.contado = 145_000;
    const { store } = await renderConRepositorio(repositorio);

    await store.agregarAlCarrito('variante-1', 2, snapshotDePrueba('variante-1'));

    await vi.waitFor(() => expect(store.ahorroSinPasarela()).toBe(10_000));
    expect(store.subtotal()).toBe(300_000);
  });

  it('ahorroSinPasarela es cero sin descuento y sin cotización', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const { store } = await renderConRepositorio(repositorio);
    expect(store.ahorroSinPasarela()).toBe(0);

    await store.agregarAlCarrito('variante-1', 2, snapshotDePrueba('variante-1'));

    await vi.waitFor(() => expect(store.subtotal()).toBe(300_000));
    expect(store.ahorroSinPasarela()).toBe(0);
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

/**
 * Pagar solo una parte del carrito: todas arrancan marcadas, lo desmarcado se queda para otra
 * compra, y una línea que ya no se vende no se paga ni tiene casilla.
 */
describe('CarritoStore y la selección de lo que se paga', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  async function conLineas(repositorio: RepositorioCarritoFalso, ...variantes: string[]) {
    const { store } = await renderConRepositorio(repositorio);
    for (const variante of variantes) {
      await store.agregarAlCarrito(variante, 1, snapshotDePrueba(variante));
      await vi.waitFor(() => expect(store.carritoId()).not.toBeNull());
    }
    await vi.waitFor(() => expect(store.cotizacion.data()?.lineas.length).toBe(variantes.length));
    return store;
  }

  it('todas arrancan marcadas, también la que se agrega después', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const store = await conLineas(repositorio, 'variante-1', 'variante-2');
    store.marcar('linea-variante-1', false);

    await store.agregarAlCarrito('variante-3', 1, snapshotDePrueba('variante-3'));

    await vi.waitFor(() =>
      expect(store.lineasPorPagar().map((linea) => linea.id)).toEqual([
        'linea-variante-2',
        'linea-variante-3',
      ]),
    );
    expect(store.estaMarcada('linea-variante-3')).toBe(true);
  });

  it('el subtotal y el ahorro son los de lo marcado', async () => {
    const repositorio = new RepositorioCarritoFalso();
    repositorio.contado = 145_000;
    const store = await conLineas(repositorio, 'variante-1', 'variante-2');
    expect(store.subtotal()).toBe(300_000);

    store.marcar('linea-variante-2', false);

    expect(store.subtotal()).toBe(150_000);
    expect(store.ahorroSinPasarela()).toBe(5_000);
    expect(store.todasMarcadas()).toBe(false);
  });

  it('marcarTodas desmarca y vuelve a marcar todas', async () => {
    const store = await conLineas(new RepositorioCarritoFalso(), 'variante-1', 'variante-2');

    store.marcarTodas(false);
    expect(store.lineasPorPagar()).toEqual([]);
    expect(store.subtotal()).toBe(0);

    store.marcarTodas(true);
    expect(store.lineasPorPagar().length).toBe(2);
    expect(store.todasMarcadas()).toBe(true);
  });

  it('una línea que ya no se vende queda fuera del pago y del subtotal', async () => {
    const repositorio = new RepositorioCarritoFalso();
    repositorio.sinPrecio.add('variante-2');
    const store = await conLineas(repositorio, 'variante-1', 'variante-2', 'variante-3');

    expect(store.lineaSeVende('linea-variante-2')).toBe(false);
    expect(store.lineasPorPagar().map((linea) => linea.id)).toEqual([
      'linea-variante-1',
      'linea-variante-3',
    ]);
    expect(store.subtotal()).toBe(300_000);
  });

  it('con una sola línea, y marcada, no hay nada que elegir', async () => {
    const store = await conLineas(new RepositorioCarritoFalso(), 'variante-1');

    expect(store.seleccionHabilitada()).toBe(false);
    expect(store.lineasPorPagar().map((linea) => linea.id)).toEqual(['linea-variante-1']);
  });

  /** Una desmarcada nunca vuelve sola al pago: conserva su casilla para que la marquen. */
  it('al quitar la otra, la desmarcada sigue fuera y con su casilla', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const store = await conLineas(repositorio, 'variante-1', 'variante-2');
    store.marcar('linea-variante-1', false);

    await store.eliminarLinea('linea-variante-2');

    await vi.waitFor(() => expect(store.consulta.data()?.lineas.length).toBe(1));
    expect(store.seleccionHabilitada()).toBe(true);
    expect(store.lineasPorPagar()).toEqual([]);
    expect(store.estaMarcada('linea-variante-1')).toBe(false);
  });

  /**
   * Encontrado en la revisión: con «sin casillas todo cuenta como marcado», si la marcada dejaba
   * de venderse en mitad del checkout, el pedido salía con la que el comprador desmarcó.
   */
  it('si la marcada deja de venderse, no se paga la desmarcada', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const store = await conLineas(repositorio, 'variante-1', 'variante-2');
    store.marcar('linea-variante-2', false);

    repositorio.sinPrecio.add('variante-1');
    await store.cotizacion.refetch();

    await vi.waitFor(() => expect(store.lineaSeVende('linea-variante-1')).toBe(false));
    expect(store.lineasPorPagar()).toEqual([]);
    expect(store.hayLineasVendibles()).toBe(true);
  });

  it('sin ninguna línea que se venda, no hay vendibles ni nada por pagar', async () => {
    const repositorio = new RepositorioCarritoFalso();
    repositorio.sinPrecio.add('variante-1').add('variante-2');
    const store = await conLineas(repositorio, 'variante-1', 'variante-2');

    expect(store.hayLineasVendibles()).toBe(false);
    expect(store.seleccionHabilitada()).toBe(false);
    expect(store.lineasPorPagar()).toEqual([]);
  });

  it('desmarcar queda guardado en el almacén', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const previo = await conLineas(repositorio, 'variante-1', 'variante-2');
    previo.marcar('linea-variante-2', false);

    expect(new LineasSinMarcarLocalStorageAlmacen().leer()).toEqual(['linea-variante-2']);
  });

  it('una desmarcada guardada se respeta al cargar', async () => {
    sembrarLineasSinMarcar(['linea-variante-1']);
    const store = await conLineas(new RepositorioCarritoFalso(), 'variante-1', 'variante-2');

    expect(store.lineasPorPagar().map((linea) => linea.id)).toEqual(['linea-variante-2']);
  });

  it('quitarPagadas quita lo que llevó el pedido y deja lo demás marcado', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const store = await conLineas(repositorio, 'variante-1', 'variante-2', 'variante-3');
    store.marcar('linea-variante-3', false);

    await store.quitarPagadas([
      { varianteId: 'variante-1', cantidad: 1 },
      { varianteId: 'variante-2', cantidad: 1 },
    ]);

    await vi.waitFor(() =>
      expect(store.consulta.data()?.lineas.map((linea) => linea.id)).toEqual(['linea-variante-3']),
    );
    expect(store.carritoId()).toBe('carrito-1');
    expect(store.estaMarcada('linea-variante-3')).toBe(true);
    expect(new LineasSinMarcarLocalStorageAlmacen().leer()).toEqual([]);
  });

  /** Con pasarela, entre salir a pagar y volver pagado se pudo subir la cantidad. */
  it('quitarPagadas deja las unidades que se agregaron después de pagar', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const store = await conLineas(repositorio, 'variante-1', 'variante-2');
    await store.actualizarCantidad('linea-variante-1', 3);

    await store.quitarPagadas([{ varianteId: 'variante-1', cantidad: 1 }]);

    expect(repositorio.actualizadas.at(-1)).toEqual({ lineaId: 'linea-variante-1', cantidad: 2 });
    expect(repositorio.llamadasEliminar).toBe(0);
  });

  it('quitarPagadas con todo pagado limpia el carrito sin borrar línea por línea', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const store = await conLineas(repositorio, 'variante-1', 'variante-2');

    await store.quitarPagadas([
      { varianteId: 'variante-1', cantidad: 1 },
      { varianteId: 'variante-2', cantidad: 1 },
    ]);

    expect(store.carritoId()).toBeNull();
    expect(repositorio.llamadasEliminar).toBe(0);
  });

  /** La caché puede tener un minuto: lo agregado en otra pestaña no está en ella. */
  it('quitarPagadas decide con el carrito del servidor, no con la caché', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const store = await conLineas(repositorio, 'variante-1');
    repositorio.cambiarEnOtraPestana([
      { id: 'linea-variante-1', varianteId: 'variante-1', cantidad: 1 },
      { id: 'linea-variante-9', varianteId: 'variante-9', cantidad: 1 },
    ]);

    await store.quitarPagadas([{ varianteId: 'variante-1', cantidad: 1 }]);

    expect(store.carritoId()).toBe('carrito-1');
    await vi.waitFor(() =>
      expect(store.consulta.data()?.lineas.map((linea) => linea.id)).toEqual(['linea-variante-9']),
    );
  });

  it('si quitar una línea falla, las demás pagadas igual salen', async () => {
    const repositorio = new RepositorioCarritoFalso();
    const store = await conLineas(repositorio, 'variante-1', 'variante-2', 'variante-3');
    const original = repositorio.eliminarLinea.bind(repositorio);
    repositorio.eliminarLinea = async (carritoId: string, lineaId: string) => {
      if (lineaId === 'linea-variante-1') {
        throw new Error('se cayó la red');
      }
      return original(carritoId, lineaId);
    };

    await store.quitarPagadas([
      { varianteId: 'variante-1', cantidad: 1 },
      { varianteId: 'variante-2', cantidad: 1 },
    ]);

    await vi.waitFor(() =>
      expect(store.consulta.data()?.lineas.map((linea) => linea.id)).toEqual([
        'linea-variante-1',
        'linea-variante-3',
      ]),
    );
  });
});
