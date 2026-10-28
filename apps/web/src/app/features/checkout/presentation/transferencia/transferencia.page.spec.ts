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
import { MetodoDePagoConPrecio, Pedido, Seguimiento } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import { TransferenciaPage } from './transferencia.page';
import {
  ALMACEN_CORREO_DE_PEDIDO,
  AlmacenCorreoDePedido,
} from '../../domain/almacen-correo-de-pedido.puerto';

/** El navegador que recordó el correo antes de ir a la pasarela, en memoria. */
class AlmacenCorreoEnMemoria implements AlmacenCorreoDePedido {
  readonly guardados = new Map<string, string>();
  recordar(pedidoId: string, correo: string): void {
    this.guardados.set(pedidoId, correo);
  }
  correoDe(pedidoId: string): string | null {
    return this.guardados.get(pedidoId) ?? null;
  }
}

/** Lo que antes venía en la URL —pedido y correo— ahora lo recuerda el navegador. */
function almacenDesde(query: Record<string, string> = {}): AlmacenCorreoEnMemoria {
  const almacen = new AlmacenCorreoEnMemoria();
  if (query['pedidoId'] && query['correo']) {
    almacen.recordar(query['pedidoId'], query['correo']);
  }
  return almacen;
}

function pedidoDePrueba(overrides: Partial<Pedido> = {}): Pedido {
  return {
    id: 'pedido-1',
    numeroPedido: 'TS-2026-000001',
    usuarioId: null,
    correo: 'cliente@tecnosport.co',
    nombreComprador: null,
    lineas: [],
    tipoEntrega: 'RETIRO_EN_PUNTO',
    direccion: null,
    metodoPago: 'TRANSFERENCIA_MANUAL',
    estado: 'PAGO_PENDIENTE',
    subtotal: { valor: 300_000, moneda: 'COP' },
    descuento: { valor: 0, moneda: 'COP' },
    costoEnvio: { valor: 0, moneda: 'COP' },
    total: { valor: 300_000, moneda: 'COP' },
    creadoEn: '2026-01-01T00:00:00Z',
    contacto: null,
    transportadora: null,
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

  async metodosDePagoConPrecio(): Promise<MetodoDePagoConPrecio[]> {
    throw new Error('no usado en esta prueba');
  }

  async reintentarPago(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async consultarSeguimiento(): Promise<Seguimiento | null> {
    return this.seguimiento;
  }

  async solicitarEnlaceDeSeguimiento(): Promise<void> {
    throw new Error('no usado en esta prueba');
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
      { provide: ALMACEN_CORREO_DE_PEDIDO, useValue: almacenDesde(query) },
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
   * ADR-0095: el monto a transferir ya trae el descuento, y se dice: es lo que explica por qué no
   * es el precio de la vitrina.
   */
  it('el monto a transferir dice que ya incluye el descuento', async () => {
    await renderConProviders(
      new RepositorioPedidosFalso(),
      {},
      anfitrionConPedidoEnMemoria(
        pedidoDePrueba({
          subtotal: { valor: 290_000, moneda: 'COP' },
          descuento: { valor: 10_000, moneda: 'COP' },
          total: { valor: 290_000, moneda: 'COP' },
        }),
      ),
    );

    const nota = await screen.findByText(
      /^Ya incluye un descuento de \$\s?10\.000 por pagar por transferencia\.$/,
    );
    expect(nota.closest('dd')?.textContent).toMatch(/290\.000/);
  });

  it('sin descuento el monto va solo', async () => {
    await renderConProviders(
      new RepositorioPedidosFalso(),
      {},
      anfitrionConPedidoEnMemoria(pedidoDePrueba()),
    );

    expect((await screen.findAllByText(/300\.000/)).length).toBeGreaterThan(0);
    expect(screen.queryByText(/descuento/)).toBeNull();
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
