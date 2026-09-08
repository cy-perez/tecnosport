import { Component, inject } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { CheckoutStore } from '../../application/checkout.store';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { MetodoPago, Pedido } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import { EstadoPage } from './estado.page';

function pedidoDePrueba(overrides: Partial<Pedido> = {}): Pedido {
  return {
    id: 'pedido-1',
    numeroPedido: 'TS-2026-000001',
    usuarioId: null,
    correo: 'cliente@tecnosport.co',
    lineas: [
      {
        id: 'linea-1',
        varianteId: 'variante-1',
        sku: 'SKU-1',
        nombre: 'Morral urbano',
        cantidad: 2,
        precioUnitario: { valor: 150_000, moneda: 'COP' },
        tasaIva: 0.19,
        imagenUrl: null,
      },
    ],
    tipoEntrega: 'RETIRO_EN_PUNTO',
    direccion: null,
    metodoPago: 'CONTRAENTREGA',
    estado: 'CONFIRMADO_CONTRAENTREGA',
    total: { valor: 300_000, moneda: 'COP' },
    creadoEn: '2026-01-01T00:00:00Z',
    datosTransferencia: null,
    ...overrides,
  };
}

class RepositorioPedidosFalso implements RepositorioPedidos {
  llamadasReintentar = 0;

  constructor(
    private seguimiento: Pedido | null = null,
    private pedidoReintentado: Pedido = pedidoDePrueba({ estado: 'PAGO_PENDIENTE', metodoPago: 'CONTRAENTREGA' }),
  ) {}

  async crear(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async metodosDePagoDisponibles(): Promise<MetodoPago[]> {
    throw new Error('no usado en esta prueba');
  }

  async reintentarPago(): Promise<Pedido> {
    this.llamadasReintentar++;
    return this.pedidoReintentado;
  }

  async consultarSeguimiento(): Promise<Pedido | null> {
    return this.seguimiento;
  }
}

class RepositorioPagosFalso implements RepositorioPagos {
  llamadasCrearIntento = 0;

  async crearIntento(): Promise<IntentoDePago> {
    this.llamadasCrearIntento++;
    return {
      referencia: 'TS-2026-000001-1',
      monto: { valor: 300_000, moneda: 'COP' },
      firmaIntegridad: 'firma',
      llavePublica: 'pub_test_xyz',
      ambiente: 'sandbox',
    };
  }

  async registrarIdTransaccion(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}


/** Siembra `CheckoutStore.pedido` antes de que `EstadoPage` se construya —
 * su `criteriosSeguimiento` computed lo lee de inmediato, mismo motivo que
 * en `metodo-pago.page.spec.ts`. */
function anfitrionConPedidoEnMemoria(pedido: Pedido) {
  @Component({ selector: 'app-anfitrion-de-prueba', imports: [EstadoPage], template: `<app-estado />` })
  class AnfitrionDePrueba {
    private readonly checkout = inject(CheckoutStore);

    constructor() {
      this.checkout.pedido.set(pedido);
    }
  }
  return AnfitrionDePrueba;
}

async function renderConProviders(
  pedidos: RepositorioPedidos,
  pagos: RepositorioPagos,
  query: Record<string, string> = {},
) {
  return render(EstadoPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'checkout/es': esCheckout } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_PEDIDOS, useValue: pedidos },
      { provide: REPOSITORIO_PAGOS, useValue: pagos },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
    ],
  });
}

async function renderConPedidoEnMemoria(pedido: Pedido, pedidos: RepositorioPedidos, pagos: RepositorioPagos) {
  return render(anfitrionConPedidoEnMemoria(pedido), {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'checkout/es': esCheckout } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_PEDIDOS, useValue: pedidos },
      { provide: REPOSITORIO_PAGOS, useValue: pagos },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
    ],
  });
}

describe('EstadoPage', () => {
  it('con el pedido ya en memoria, lo muestra sin consultar al servidor', async () => {
    const pedidos = new RepositorioPedidosFalso();
    await renderConPedidoEnMemoria(pedidoDePrueba(), pedidos, new RepositorioPagosFalso());

    expect(await screen.findByText('Pedido TS-2026-000001')).toBeTruthy();
    expect(screen.getByText('Morral urbano')).toBeTruthy();
    expect(screen.getByText('Confirmado, pago contra entrega')).toBeTruthy();
  });

  it('sin pedido en memoria pero con pedidoId y correo en la URL, consulta el seguimiento', async () => {
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ estado: 'PAGADO', metodoPago: 'TARJETA' }));

    await renderConProviders(pedidos, new RepositorioPagosFalso(), {
      pedidoId: 'pedido-1',
      correo: 'cliente@tecnosport.co',
    });

    expect(await screen.findByText('Pedido TS-2026-000001')).toBeTruthy();
    expect(screen.getByText('Pagado')).toBeTruthy();
  });

  it('sin pedido en memoria ni datos en la URL, muestra el mensaje de no encontrado', async () => {
    await renderConProviders(new RepositorioPedidosFalso(), new RepositorioPagosFalso());
    expect(await screen.findByText('No encontramos este pedido.')).toBeTruthy();
  });

  it('un pedido PAGO_FALLIDO muestra el botón de reintentar; uno confirmado no', async () => {
    const pedidos = new RepositorioPedidosFalso();
    await renderConPedidoEnMemoria(
      pedidoDePrueba({ estado: 'PAGO_FALLIDO', metodoPago: 'TARJETA' }),
      pedidos,
      new RepositorioPagosFalso(),
    );

    expect(await screen.findByRole('button', { name: 'Reintentar pago' })).toBeTruthy();
  });

  it('un pedido confirmado por contraentrega no muestra el botón de reintentar', async () => {
    const pedidos = new RepositorioPedidosFalso();
    await renderConPedidoEnMemoria(pedidoDePrueba(), pedidos, new RepositorioPagosFalso());
    await screen.findByText('Pedido TS-2026-000001');

    expect(screen.queryByRole('button', { name: 'Reintentar pago' })).toBeFalsy();
  });

  it('reintentar con un método de Wompi crea el intento de pago', async () => {
    const ubicacionOriginal = window.location;
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...ubicacionOriginal, href: '', origin: 'https://tecnosport.co' },
    });

    const pedidos = new RepositorioPedidosFalso(
      null,
      pedidoDePrueba({ estado: 'PAGO_PENDIENTE', metodoPago: 'TARJETA' }),
    );
    const pagos = new RepositorioPagosFalso();
    await renderConPedidoEnMemoria(
      pedidoDePrueba({ estado: 'PAGO_FALLIDO', metodoPago: 'TARJETA' }),
      pedidos,
      pagos,
    );
    await screen.findByRole('button', { name: 'Reintentar pago' });

    fireEvent.click(screen.getByRole('button', { name: 'Reintentar pago' }));
    await vi.waitFor(() => expect(pedidos.llamadasReintentar).toBe(1));
    expect(pagos.llamadasCrearIntento).toBe(1);
    expect(window.location.href).toContain('https://checkout.wompi.co/p/?');

    Object.defineProperty(window, 'location', { configurable: true, value: ubicacionOriginal });
  });

  // Es la última pantalla del recorrido y la que alguien vuelve a abrir días después para ver
  // en qué va su pedido.
  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    const { container } = await renderConPedidoEnMemoria(
      pedidoDePrueba(),
      new RepositorioPedidosFalso(),
      new RepositorioPagosFalso(),
    );
    await screen.findByText('Pedido TS-2026-000001');

    await esperarSinViolaciones(container);
  });
});
