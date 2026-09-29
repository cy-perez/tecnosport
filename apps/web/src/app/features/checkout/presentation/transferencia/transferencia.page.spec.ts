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
import { IntentoSistecredito } from '../../domain/intento-sistecredito.model';
import { MetodoPago, Pedido, Seguimiento } from '../../domain/pedido.model';
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
    subtotal: { valor: 300_000, moneda: 'COP' },
    costoEnvio: { valor: 0, moneda: 'COP' },
    total: { valor: 300_000, moneda: 'COP' },
    creadoEn: '2026-01-01T00:00:00Z',
    contacto: null,
    datosTransferencia: {
      cuentas: [
        {
          entidad: 'Bancolombia',
          tipoCuenta: 'Ahorros',
          numeroCuenta: '000-000000-00',
          titular: 'Tecno Sport',
        },
      ],
      referencia: 'TS-2026-000001',
    },
    ...overrides,
  };
}

/** Un pedido visto por el endpoint de seguimiento: el mismo, mas sus retractos. */
function seguimientoDePrueba(overrides: Parameters<typeof pedidoDePrueba>[0] = {}): Seguimiento {
  return { ...pedidoDePrueba(overrides), envio: null, retractos: [] };
}

class RepositorioPedidosFalso implements RepositorioPedidos {
  constructor(private seguimiento: Seguimiento | null = null) {}

  async crear(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async metodosDePagoDisponibles(): Promise<MetodoPago[]> {
    throw new Error('no usado en esta prueba');
  }

  async reintentarPago(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async consultarSeguimiento(): Promise<Seguimiento | null> {
    return this.seguimiento;
  }

  /** El mismo pedido por los dos caminos: lo que cambia en el servidor es por dónde se entra. */
  async consultarSeguimientoPorNumero(): Promise<Seguimiento | null> {
    return this.seguimiento;
  }
}

class RepositorioPagosFalso implements RepositorioPagos {
  async crearIntento(): Promise<IntentoDePago> {
    throw new Error('no usado en esta prueba');
  }

  async crearIntentoSistecredito(): Promise<IntentoSistecredito> {
    throw new Error('no usado en esta prueba');
  }

  async registrarIdTransaccion(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}

function anfitrionConPedidoEnMemoria(pedido: Pedido) {
  @Component({
    selector: 'app-anfitrion-de-prueba',
    imports: [TransferenciaPage],
    template: `<app-transferencia />`,
  })
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
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
      },
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

  /**
   * <b>Se pintan TODAS las cuentas, no la primera.</b> Desde el 28 de septiembre de 2026 el sitio
   * acepta tres —Nequi, Daviplata y una de ahorros de BBVA— y el comprador elige a cuál
   * transfiere; enseñar una sola lo dejaría sin la opción que quizá es la única que tiene.
   *
   * <p>Lo que esta prueba cuida es justo lo que un `@for` mal escrito rompe sin fallar: pintar el
   * primero y callar el resto. Con una sola cuenta —que es como estuvo el sitio hasta hoy— las dos
   * versiones se ven igual, así que el escenario necesita dos.
   *
   * <p>Y la referencia va <b>una sola vez</b>: es del pedido y vale para todas. Repetirla en cada
   * caja invitaría a pensar que cada cuenta lleva la suya.
   */
  it('muestra todas las cuentas ofrecidas, y la referencia una sola vez', async () => {
    const pedido = pedidoDePrueba();
    const conDosCuentas = {
      ...pedido,
      datosTransferencia: {
        cuentas: [
          ...pedido.datosTransferencia!.cuentas,
          {
            entidad: 'Nequi',
            tipoCuenta: 'billetera',
            numeroCuenta: '300 000 0000',
            titular: 'Tecno Sport',
          },
        ],
        referencia: pedido.datosTransferencia!.referencia,
      },
    };

    await renderConProviders(
      new RepositorioPedidosFalso(),
      {},
      anfitrionConPedidoEnMemoria(conDosCuentas),
    );

    expect(await screen.findByText('Bancolombia')).toBeTruthy();
    expect(screen.getByText('000-000000-00')).toBeTruthy();
    expect(screen.getByText('Nequi')).toBeTruthy();
    expect(screen.getByText('300 000 0000')).toBeTruthy();

    expect(screen.getAllByText('TS-2026-000001')).toHaveLength(1);
  });

  it('sin pedido en memoria pero con pedidoId y correo en la URL, consulta el seguimiento', async () => {
    const pedidos = new RepositorioPedidosFalso(seguimientoDePrueba());

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
      anfitrionConPedidoEnMemoria(
        pedidoDePrueba({ metodoPago: 'CONTRAENTREGA', datosTransferencia: null }),
      ),
    );
    expect(await screen.findByText('No encontramos los datos de esta transferencia.')).toBeTruthy();
  });
});
