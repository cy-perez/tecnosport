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
import { IntentoSistecredito } from '../../domain/intento-sistecredito.model';
import { MetodoPago, Pedido, Seguimiento } from '../../domain/pedido.model';
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
    subtotal: { valor: 300_000, moneda: 'COP' },
    costoEnvio: { valor: 0, moneda: 'COP' },
    total: { valor: 300_000, moneda: 'COP' },
    creadoEn: '2026-01-01T00:00:00Z',
    contacto: null,
    datosTransferencia: null,
    ...overrides,
  };
}

/** Un pedido visto por el endpoint de seguimiento: el mismo, mas su envio y sus retractos. */
function seguimientoDePrueba(overrides: Parameters<typeof pedidoDePrueba>[0] = {}): Seguimiento {
  return { ...pedidoDePrueba(overrides), envio: null, retractos: [] };
}

class RepositorioPedidosFalso implements RepositorioPedidos {
  llamadasReintentar = 0;

  constructor(
    private seguimiento: Seguimiento | null = null,
    private pedidoReintentado: Pedido = pedidoDePrueba({
      estado: 'PAGO_PENDIENTE',
      metodoPago: 'CONTRAENTREGA',
    }),
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

  async consultarSeguimiento(): Promise<Seguimiento | null> {
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

  async crearIntentoSistecredito(): Promise<IntentoSistecredito> {
    throw new Error('no usado en esta prueba');
  }

  async registrarIdTransaccion(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}

/** Siembra `CheckoutStore.pedido` antes de que `EstadoPage` se construya —
 * su `criteriosSeguimiento` computed lo lee de inmediato, mismo motivo que
 * en `metodo-pago.page.spec.ts`. */
function anfitrionConPedidoEnMemoria(pedido: Pedido) {
  @Component({
    selector: 'app-anfitrion-de-prueba',
    imports: [EstadoPage],
    template: `<app-estado />`,
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
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
      },
    ],
  });
}

async function renderConPedidoEnMemoria(
  pedido: Pedido,
  pedidos: RepositorioPedidos,
  pagos: RepositorioPagos,
) {
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

/**
 * La cifra que corresponde a un rotulo del desglose: en un `<dl>`, el valor de un `<dt>` es el
 * `<dd>` inmediatamente siguiente. Ata rotulo y numero, que es lo unico que prueba que la etiqueta
 * no miente — que es el defecto que esta pantalla tuvo.
 */
function cifraDe(rotulo: string): string {
  const dt = screen.getByText(rotulo);
  const dd = dt.nextElementSibling;
  if (!dd || dd.tagName !== 'DD') {
    throw new Error(`"${rotulo}" no tiene un <dd> detras; el desglose cambio de forma.`);
  }
  return dd.textContent ?? '';
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
    const pedidos = new RepositorioPedidosFalso(
      seguimientoDePrueba({ estado: 'PAGADO', metodoPago: 'TARJETA' }),
    );

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

  it('muestra el retracto del comprador cuando el seguimiento lo trae', async () => {
    const pedidos = new RepositorioPedidosFalso({
      ...pedidoDePrueba({ estado: 'DEVUELTO', metodoPago: 'TARJETA' }),
      envio: null,
      retractos: [
        {
          estado: 'REEMBOLSADA',
          radicadaEn: '2026-09-14T15:00:00Z',
          motivo: null,
          productoRecibidoEn: '2026-09-16T15:00:00Z',
          limiteDeReintegro: '2026-10-02T05:00:00Z',
          montoReembolsado: { valor: 50_000, moneda: 'COP' },
          reembolsadoEn: '2026-09-20T15:00:00Z',
        },
      ],
    });

    await renderConProviders(pedidos, new RepositorioPagosFalso(), {
      pedidoId: 'pedido-1',
      correo: 'cliente@tecnosport.co',
    });

    expect(await screen.findByText('Reintegramos tu dinero.')).toBeTruthy();
  });

  it('no muestra el bloque de retracto cuando no hay ninguno', async () => {
    const pedidos = new RepositorioPedidosFalso(
      seguimientoDePrueba({ estado: 'PAGADO', metodoPago: 'TARJETA' }),
    );

    await renderConProviders(pedidos, new RepositorioPagosFalso(), {
      pedidoId: 'pedido-1',
      correo: 'cliente@tecnosport.co',
    });

    await screen.findByText(/TS-/);
    expect(screen.queryByText('Tu solicitud de retracto')).toBeNull();
  });

  // El desglose. Esta pantalla etiquetaba "Subtotal" sobre el total, y desde que el flete se cobra
  // aparte era falso: es el unico sitio donde el comprador vuelve a mirar lo que pago.
  it('desglosa subtotal, envio y total, y el total es la suma', async () => {
    const pedidos = new RepositorioPedidosFalso(
      seguimientoDePrueba({
        estado: 'DESPACHADO',
        metodoPago: 'TARJETA',
        tipoEntrega: 'ENVIO_A_DOMICILIO',
        subtotal: { valor: 300_000, moneda: 'COP' },
        costoEnvio: { valor: 14_500, moneda: 'COP' },
        total: { valor: 314_500, moneda: 'COP' },
      }),
    );

    await renderConProviders(pedidos, new RepositorioPagosFalso(), {
      pedidoId: 'pedido-1',
      correo: 'cliente@tecnosport.co',
    });

    // Cada rotulo con SU cifra, y no los seis textos sueltos dentro del <dl>. La version anterior
    // de esta prueba tomaba `subtotal.parentElement`, que es el <dl> entero: las seis afirmaciones
    // se cumplian con los numeros en cualquier orden, asi que intercambiar subtotal y costoEnvio
    // en la plantilla la dejaba verde. Y el defecto que este commit arregla era exactamente ese —
    // un rotulo correcto sobre la cifra equivocada, sostenido meses porque las dos coincidian.
    await screen.findByText('Subtotal');
    expect(cifraDe('Subtotal')).toContain('300.000');
    expect(cifraDe('Costo de envío')).toContain('14.500');
    expect(cifraDe('Total a pagar')).toContain('314.500');
  });

  // El retiro en punto no paga flete, y los pedidos anteriores a la Fase 7 lo llevaban dentro del
  // precio: una linea de "$ 0" no informa nada y en el segundo caso ademas mentiria.
  it('sin flete cobrado no pinta la linea de envio', async () => {
    const pedidos = new RepositorioPedidosFalso(seguimientoDePrueba());

    await renderConProviders(pedidos, new RepositorioPagosFalso(), {
      pedidoId: 'pedido-1',
      correo: 'cliente@tecnosport.co',
    });

    await screen.findByText('Subtotal');
    expect(screen.queryByText('Costo de envío')).toBeNull();
  });

  it('muestra la transportadora y la guia cuando el pedido ya se despacho', async () => {
    const pedidos = new RepositorioPedidosFalso({
      ...pedidoDePrueba({ estado: 'DESPACHADO', metodoPago: 'TARJETA' }),
      envio: {
        guias: [{ transportadora: 'Servientrega', guia: 'SE123456' }],
        despachadoEn: '2026-09-14T15:00:00Z',
      },
      retractos: [],
    });

    await renderConProviders(pedidos, new RepositorioPagosFalso(), {
      pedidoId: 'pedido-1',
      correo: 'cliente@tecnosport.co',
    });

    expect(await screen.findByText('Tu envío')).toBeTruthy();
    expect(screen.getByText('Servientrega')).toBeTruthy();
    expect(screen.getByText('SE123456')).toBeTruthy();
    // Con un solo paquete no se le anuncia nada de paquetes: seria ruido.
    expect(screen.queryByText(/viaja en/)).toBeNull();
  });

  /**
   * Un pedido de dos variantes sale en dos guias (`adr/0031`), y quien recibe una de dos sin
   * saberlo cree que le faltó media compra. Las dos guias y el aviso tienen que verse.
   */
  it('con dos guias muestra las dos y avisa que son dos paquetes', async () => {
    const pedidos = new RepositorioPedidosFalso({
      ...pedidoDePrueba({ estado: 'DESPACHADO', metodoPago: 'TARJETA' }),
      envio: {
        guias: [
          { transportadora: 'Servientrega', guia: 'SE123456' },
          { transportadora: 'Coordinadora', guia: 'CO987' },
        ],
        despachadoEn: '2026-09-14T15:00:00Z',
      },
      retractos: [],
    });

    await renderConProviders(pedidos, new RepositorioPagosFalso(), {
      pedidoId: 'pedido-1',
      correo: 'cliente@tecnosport.co',
    });

    expect(await screen.findByText('Tu envío')).toBeTruthy();
    expect(screen.getByText('Servientrega')).toBeTruthy();
    expect(screen.getByText('SE123456')).toBeTruthy();
    expect(screen.getByText('Coordinadora')).toBeTruthy();
    expect(screen.getByText('CO987')).toBeTruthy();
    expect(screen.getByText(/viaja en 2 paquetes/)).toBeTruthy();
  });

  it('sin envio no pinta el bloque de envio', async () => {
    const pedidos = new RepositorioPedidosFalso(
      seguimientoDePrueba({ estado: 'PAGADO', metodoPago: 'TARJETA' }),
    );

    await renderConProviders(pedidos, new RepositorioPagosFalso(), {
      pedidoId: 'pedido-1',
      correo: 'cliente@tecnosport.co',
    });

    await screen.findByText(/TS-/);
    expect(screen.queryByText('Tu envío')).toBeNull();
  });
});
