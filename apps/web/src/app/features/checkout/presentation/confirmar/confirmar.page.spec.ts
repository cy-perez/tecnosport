import { Component, inject } from '@angular/core';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { Carrito } from '../../../carrito/domain/carrito.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../../../carrito/domain/repositorio-carrito.puerto';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { CheckoutStore } from '../../application/checkout.store';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { CrearPedidoComando, DatosEntrega } from '../../domain/pedido.comandos';
import { MetodoPago, Pedido } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import { ConfirmarPage } from './confirmar.page';
import { proveerAlmacenesCarrito, sembrarCarritoId } from '../../../../../testing/carrito';

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

  async eliminarLinea(): Promise<Carrito> {
    throw new Error('no usado en esta prueba');
  }
}

function pedidoDePrueba(overrides: Partial<Pedido> = {}): Pedido {
  return {
    id: 'pedido-1',
    numeroPedido: 'TS-2026-000001',
    usuarioId: null,
    correo: 'compra@ejemplo.co',
    lineas: [],
    tipoEntrega: 'RETIRO_EN_PUNTO',
    direccion: null,
    metodoPago: 'TARJETA',
    estado: 'PAGO_PENDIENTE',
    total: { valor: 150_000, moneda: 'COP' },
    creadoEn: '2026-01-01T00:00:00Z',
    datosTransferencia: null,
    ...overrides,
  };
}

class RepositorioPedidosFalso implements RepositorioPedidos {
  llamadasCrear = 0;

  constructor(private pedido: Pedido = pedidoDePrueba()) {}

  async crear(_comando: CrearPedidoComando): Promise<Pedido> {
    this.llamadasCrear++;
    return { ...this.pedido, metodoPago: _comando.metodoPago };
  }

  async metodosDePagoDisponibles(): Promise<MetodoPago[]> {
    throw new Error('no usado en esta prueba');
  }

  async reintentarPago(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async consultarSeguimiento(): Promise<Pedido | null> {
    throw new Error('no usado en esta prueba');
  }
}

class RepositorioPedidosQueFalla implements RepositorioPedidos {
  async crear(): Promise<Pedido> {
    throw new Error('el servidor rechazó el pedido');
  }

  async metodosDePagoDisponibles(): Promise<MetodoPago[]> {
    throw new Error('no usado en esta prueba');
  }

  async reintentarPago(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async consultarSeguimiento(): Promise<Pedido | null> {
    throw new Error('no usado en esta prueba');
  }
}

class RepositorioPagosFalso implements RepositorioPagos {
  llamadasCrearIntento = 0;

  async crearIntento(): Promise<IntentoDePago> {
    this.llamadasCrearIntento++;
    return {
      referencia: 'TS-2026-000001-1',
      monto: { valor: 150_000, moneda: 'COP' },
      firmaIntegridad: 'firma',
      llavePublica: 'pub_test_xyz',
      ambiente: 'sandbox',
    };
  }

  async registrarIdTransaccion(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}

const CARRITO_CON_LINEAS: Carrito = {
  id: 'carrito-1',
  usuarioId: null,
  creadoEn: '2026-01-01T00:00:00Z',
  lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 1 }],
};

const DATOS_ENTREGA: DatosEntrega = {
  correo: 'compra@ejemplo.co',
  tipoEntrega: 'RETIRO_EN_PUNTO',
  direccion: null,
};

/** Mismo motivo que en `metodo-pago.page.spec.ts`: el guardia corre en el
 * primer `effect()`, así que los datos tienen que existir antes de que
 * `ConfirmarPage` se construya. */
function anfitrionConDatos(metodoPago: MetodoPago) {
  @Component({ selector: 'app-anfitrion-de-prueba', imports: [ConfirmarPage], template: `<app-confirmar />` })
  class AnfitrionDePrueba {
    private readonly checkout = inject(CheckoutStore);

    constructor() {
      this.checkout.guardarDatosEntrega(DATOS_ENTREGA);
      this.checkout.elegirMetodoPago(metodoPago);
    }
  }
  return AnfitrionDePrueba;
}

@Component({ selector: 'app-ruta-muda', template: '' })
class RutaMuda {}

async function renderConDatos(
  metodoPago: MetodoPago,
  carrito: RepositorioCarrito,
  pedidos: RepositorioPedidos,
  pagos: RepositorioPagos = new RepositorioPagosFalso(),
) {
  return render(anfitrionConDatos(metodoPago), {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'checkout/es': esCheckout } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      ...proveerAlmacenesCarrito(),
      provideRouter([
        { path: 'metodo-pago', component: RutaMuda },
        { path: 'transferencia', component: RutaMuda },
        { path: 'estado', component: RutaMuda },
      ]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_CARRITO, useValue: carrito },
      { provide: REPOSITORIO_PEDIDOS, useValue: pedidos },
      { provide: REPOSITORIO_PAGOS, useValue: pagos },
    ],
  });
}

/**
 * Espera a que la consulta del carrito resuelva. Reemplaza a `esperar(50)`, que
 * pasaba o no según lo cargada que estuviera la máquina.
 */
async function esperarCarritoCargado(fixture: ComponentFixture<unknown>): Promise<void> {
  const carrito = fixture.debugElement.injector.get(CarritoStore);
  await vi.waitFor(() => expect(carrito.consulta.data()).toBeTruthy());
}

describe('ConfirmarPage', () => {
  let ubicacionOriginal: Location;

  beforeEach(() => {
    window.localStorage.clear();
    ubicacionOriginal = window.location;
    // jsdom no navega de verdad ("Not implemented: navigation") — se
    // reemplaza por un objeto mutable para poder verificar a dónde se
    // redirige sin que jsdom lo intercepte. `Object.defineProperty` en vez
    // de una asignación directa: el setter de `window.location` solo acepta
    // `string` en los tipos de TypeScript (equivalente a asignar `.href`).
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...ubicacionOriginal, href: '', origin: 'https://tecnosport.co' },
    });
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', { configurable: true, value: ubicacionOriginal });
  });

  it('muestra correo, tipo de entrega, método de pago y subtotal', async () => {
    sembrarCarritoId('carrito-1');

    await renderConDatos('CONTRAENTREGA', new RepositorioCarritoFalso(CARRITO_CON_LINEAS), new RepositorioPedidosFalso());

    expect(await screen.findByText('compra@ejemplo.co')).toBeTruthy();
    expect(screen.getByText('Retiro en punto (Medellín, sin costo)')).toBeTruthy();
    expect(screen.getByText('Pago contra entrega')).toBeTruthy();
  });

  it('con un método de Wompi, crea el pedido, pide el intento y redirige al Web Checkout', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso();
    const pagos = new RepositorioPagosFalso();

    const { fixture } = await renderConDatos(
      'TARJETA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      pagos,
    );
    await screen.findByText('compra@ejemplo.co');
    // El carrito llega por TanStack Query: sin esperar, el click puede llegar
    // antes de que `carrito.consulta.data()` resuelva y `confirmar()` sale
    // temprano sin hacer nada (mismo hallazgo de ADR-0011). Se espera por la
    // consulta y no por un tiempo fijo: en este escenario no hay snapshot, así
    // que el subtotal es 0 igualmente y **el DOM no tiene ninguna señal**.
    await esperarCarritoCargado(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));
    await vi.waitFor(() => expect(pedidos.llamadasCrear).toBe(1));
    expect(pagos.llamadasCrearIntento).toBe(1);
    expect(window.location.href).toContain('https://checkout.wompi.co/p/?');
    expect(window.location.href).toContain('reference=TS-2026-000001-1');
  });

  it('con transferencia manual, navega a la pantalla de transferencia sin pedir intento de pago', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'TRANSFERENCIA_MANUAL' }));
    const pagos = new RepositorioPagosFalso();

    const { fixture } = await renderConDatos(
      'TRANSFERENCIA_MANUAL',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      pagos,
    );
    await screen.findByText('compra@ejemplo.co');
    // El carrito llega por TanStack Query: sin esperar, el click puede llegar
    // antes de que `carrito.consulta.data()` resuelva y `confirmar()` sale
    // temprano sin hacer nada (mismo hallazgo de ADR-0011). Se espera por la
    // consulta y no por un tiempo fijo: en este escenario no hay snapshot, así
    // que el subtotal es 0 igualmente y **el DOM no tiene ninguna señal**.
    await esperarCarritoCargado(fixture);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));
    await vi.waitFor(() => expect(navegar).toHaveBeenCalled());

    expect(navegar).toHaveBeenCalledWith(
      ['../transferencia'],
      expect.objectContaining({ queryParams: { pedidoId: 'pedido-1', correo: 'compra@ejemplo.co' } }),
    );
    expect(pagos.llamadasCrearIntento).toBe(0);
  });

  it('con contraentrega, navega a la pantalla de estado', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'CONTRAENTREGA' }));

    const { fixture } = await renderConDatos('CONTRAENTREGA', new RepositorioCarritoFalso(CARRITO_CON_LINEAS), pedidos);
    await screen.findByText('compra@ejemplo.co');
    // El carrito llega por TanStack Query: sin esperar, el click puede llegar
    // antes de que `carrito.consulta.data()` resuelva y `confirmar()` sale
    // temprano sin hacer nada (mismo hallazgo de ADR-0011). Se espera por la
    // consulta y no por un tiempo fijo: en este escenario no hay snapshot, así
    // que el subtotal es 0 igualmente y **el DOM no tiene ninguna señal**.
    await esperarCarritoCargado(fixture);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));
    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith(['../estado'], expect.anything()));
  });

  it('si crear el pedido falla, muestra un error y no navega', async () => {
    sembrarCarritoId('carrito-1');

    const { fixture } = await renderConDatos(
      'TARJETA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosQueFalla(),
    );
    await screen.findByText('compra@ejemplo.co');
    // El carrito llega por TanStack Query: sin esperar, el click puede llegar
    // antes de que `carrito.consulta.data()` resuelva y `confirmar()` sale
    // temprano sin hacer nada (mismo hallazgo de ADR-0011). Se espera por la
    // consulta y no por un tiempo fijo: en este escenario no hay snapshot, así
    // que el subtotal es 0 igualmente y **el DOM no tiene ninguna señal**.
    await esperarCarritoCargado(fixture);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(
      await screen.findByText('No se pudo confirmar el pedido. Revisa tus datos e intenta de nuevo.'),
    ).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('un reintento no vuelve a crear el pedido si ya existe uno de este intento', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'CONTRAENTREGA' }));

    const { fixture } = await renderConDatos('CONTRAENTREGA', new RepositorioCarritoFalso(CARRITO_CON_LINEAS), pedidos);
    await screen.findByText('compra@ejemplo.co');
    await esperarCarritoCargado(fixture);
    const checkout = fixture.debugElement.injector.get(CheckoutStore);
    // Simula que ya se creó en un intento anterior (p. ej. la app volvió del
    // Web Checkout y el usuario retrocedió).
    await checkout.crearPedido({
      correo: 'compra@ejemplo.co',
      lineas: [{ varianteId: 'variante-1', cantidad: 1 }],
      tipoEntrega: 'RETIRO_EN_PUNTO',
      direccion: null,
      metodoPago: 'CONTRAENTREGA',
    });
    expect(pedidos.llamadasCrear).toBe(1);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));
    await vi.waitFor(() => expect(pedidos.llamadasCrear).toBe(1));
  });
});
