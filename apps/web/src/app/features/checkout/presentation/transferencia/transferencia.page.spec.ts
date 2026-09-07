import { Component, inject, Type } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { CheckoutStore } from '../../application/checkout.store';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { MetodoPago, Pedido } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import { TransferenciaPage } from './transferencia.page';

function pedidoDePrueba(overrides: Partial<Pedido> = {}): Pedido {
  return {
    id: 'pedido-1',
    numeroPedido: 'TS-2026-000001',
    usuarioId: null,
    correo: 'cliente@tecnosport.co',
    lineas: [],
    tipoEntrega: 'RETIRO_EN_PUNTO',
    direccion: null,
    metodoPago: 'TRANSFERENCIA_MANUAL',
    estado: 'PAGO_PENDIENTE',
    total: { valor: 300_000, moneda: 'COP' },
    creadoEn: '2026-01-01T00:00:00Z',
    datosTransferencia: {
      banco: 'Bancolombia',
      tipoCuenta: 'Ahorros',
      numeroCuenta: '000-000000-00',
      titular: 'Tecno Sport',
      referencia: 'TS-2026-000001',
    },
    ...overrides,
  };
}

class RepositorioPedidosFalso implements RepositorioPedidos {
  constructor(private seguimiento: Pedido | null = null) {}

  async crear(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async metodosDePagoDisponibles(): Promise<MetodoPago[]> {
    throw new Error('no usado en esta prueba');
  }

  async reintentarPago(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async consultarSeguimiento(): Promise<Pedido | null> {
    return this.seguimiento;
  }
}

class RepositorioPagosFalso implements RepositorioPagos {
  async crearIntento(): Promise<IntentoDePago> {
    throw new Error('no usado en esta prueba');
  }

  async registrarIdTransaccion(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}


function anfitrionConPedidoEnMemoria(pedido: Pedido) {
  @Component({ selector: 'app-anfitrion-de-prueba', imports: [TransferenciaPage], template: `<app-transferencia />` })
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
  query: Record<string, string> = {},
  anfitrion: Type<unknown> = TransferenciaPage,
) {
  return render(anfitrion, {
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
      { provide: REPOSITORIO_PAGOS, useValue: new RepositorioPagosFalso() },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
    ],
  });
}

describe('TransferenciaPage', () => {
  it('con el pedido ya en memoria, muestra los datos de la cuenta', async () => {
    await renderConProviders(
      new RepositorioPedidosFalso(),
      {},
      anfitrionConPedidoEnMemoria(pedidoDePrueba()),
    );

    expect(await screen.findByText('Bancolombia')).toBeTruthy();
    expect(screen.getByText('000-000000-00')).toBeTruthy();
    expect(screen.getByText('TS-2026-000001')).toBeTruthy();
  });

  it('sin pedido en memoria pero con pedidoId y correo en la URL, consulta el seguimiento', async () => {
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba());

    await renderConProviders(pedidos, { pedidoId: 'pedido-1', correo: 'cliente@tecnosport.co' });

    expect(await screen.findByText('Bancolombia')).toBeTruthy();
  });

  it('sin pedido en memoria ni datos en la URL, muestra el mensaje de no encontrado', async () => {
    await renderConProviders(new RepositorioPedidosFalso());
    expect(await screen.findByText('No encontramos los datos de esta transferencia.')).toBeTruthy();
  });

  it('un pedido que no es de transferencia manual muestra el mensaje de no encontrado', async () => {
    await renderConProviders(
      new RepositorioPedidosFalso(),
      {},
      anfitrionConPedidoEnMemoria(pedidoDePrueba({ metodoPago: 'CONTRAENTREGA', datosTransferencia: null })),
    );
    expect(await screen.findByText('No encontramos los datos de esta transferencia.')).toBeTruthy();
  });
});
