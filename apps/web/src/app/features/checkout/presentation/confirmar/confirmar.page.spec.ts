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
import {
  REPOSITORIO_CARRITO,
  RepositorioCarrito,
} from '../../../carrito/domain/repositorio-carrito.puerto';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { CheckoutStore } from '../../application/checkout.store';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { CrearPedidoComando, DatosEntrega } from '../../domain/pedido.comandos';
import { MetodoPago, Pedido, Seguimiento } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import { CotizacionEnvio, ResultadoCotizacion } from '../../domain/envio.model';
import { REPOSITORIO_ENVIOS, RepositorioEnvios } from '../../domain/repositorio-envios.puerto';
import { ConfirmarPage } from './confirmar.page';
import { CarritoIdLocalStorageAlmacen } from '../../../carrito/infrastructure/carrito-id.almacen';
import {
  proveerAlmacenesCarrito,
  sembrarCarritoId,
  sembrarSnapshotLinea,
} from '../../../../../testing/carrito';

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
    subtotal: { valor: 150_000, moneda: 'COP' },
    costoEnvio: { valor: 0, moneda: 'COP' },
    total: { valor: 150_000, moneda: 'COP' },
    creadoEn: '2026-01-01T00:00:00Z',
    contacto: null,
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

  async consultarSeguimiento(): Promise<Seguimiento | null> {
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

  async consultarSeguimiento(): Promise<Seguimiento | null> {
    throw new Error('no usado en esta prueba');
  }
}

class RepositorioPagosQueFalla implements RepositorioPagos {
  async crearIntento(): Promise<IntentoDePago> {
    throw new Error('el proveedor de pagos no respondió');
  }

  async registrarIdTransaccion(): Promise<void> {
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
  contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
  autorizaDatos: true,
};

const DATOS_ENTREGA_A_DOMICILIO: DatosEntrega = {
  correo: 'compra@ejemplo.co',
  tipoEntrega: 'ENVIO_A_DOMICILIO',
  direccion: {
    codigoDaneDepartamento: '05',
    departamento: 'Antioquia',
    codigoDaneCiudad: '05001',
    ciudad: 'Medellín',
    direccion: 'Circular 4 # 70-20',
    indicaciones: null,
    barrio: null,
  },
  contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
  autorizaDatos: true,
};

function snapshotDePrueba(varianteId: string) {
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

/** Mismo motivo que en `metodo-pago.page.spec.ts`: el guardia corre en el
 * primer `effect()`, así que los datos tienen que existir antes de que
 * `ConfirmarPage` se construya. */
function anfitrionConDatos(metodoPago: MetodoPago, datos: DatosEntrega = DATOS_ENTREGA) {
  @Component({
    selector: 'app-anfitrion-de-prueba',
    imports: [ConfirmarPage],
    template: `<app-confirmar />`,
  })
  class AnfitrionDePrueba {
    private readonly checkout = inject(CheckoutStore);

    constructor() {
      this.checkout.guardarDatosEntrega(datos);
      this.checkout.elegirMetodoPago(metodoPago);
    }
  }
  return AnfitrionDePrueba;
}

@Component({ selector: 'app-ruta-muda', template: '' })
class RutaMuda {}

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
class RepositorioEnviosFalso implements RepositorioEnvios {
  constructor(
    private readonly respuesta: CotizacionEnvio | ResultadoCotizacion | null | Error = COTIZACION,
  ) {}

  /**
   * Acepta una tarifa suelta o un resultado entero. Lo primero es azúcar para los casos de
   * siempre —`null` sigue siendo "sin cobertura", como cuando el puerto devolvía eso— y lo
   * segundo es lo que necesita el caso del artículo no asegurable, que lleva datos consigo.
   */
  async cotizar(): Promise<ResultadoCotizacion> {
    if (this.respuesta instanceof Error) {
      throw this.respuesta;
    }
    if (this.respuesta === null) {
      return { tipo: 'SIN_COBERTURA' };
    }
    return 'tipo' in this.respuesta
      ? this.respuesta
      : { tipo: 'TARIFA', cotizacion: this.respuesta };
  }
}

const COTIZACION: CotizacionEnvio = {
  costoEnvio: 9_540,
  moneda: 'COP',
  transportadora: '99 minutes',
  diasEstimados: 2,
  venceEn: '2026-09-14T12:00:00Z',
};

async function renderConDatos(
  metodoPago: MetodoPago,
  carrito: RepositorioCarrito,
  pedidos: RepositorioPedidos,
  pagos: RepositorioPagos = new RepositorioPagosFalso(),
  datos: DatosEntrega = DATOS_ENTREGA,
  envios: RepositorioEnvios = new RepositorioEnviosFalso(),
) {
  return render(anfitrionConDatos(metodoPago, datos), {
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
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_CARRITO, useValue: carrito },
      { provide: REPOSITORIO_PEDIDOS, useValue: pedidos },
      { provide: REPOSITORIO_ENVIOS, useValue: envios },
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

  /**
   * El artículo 50 de la Ley 1480 de 2011 exige el desglose —productos, envío aparte y la suma—
   * **antes de finalizar la transacción**, y la transacción se finaliza con el botón de esta
   * pantalla, no con el de dos pasos atrás. Aquí solo se veía "Subtotal".
   */
  it('a domicilio, muestra el envío por separado y el total a pagar', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderConDatos(
      'NEQUI',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(),
      new RepositorioPagosFalso(),
      DATOS_ENTREGA_A_DOMICILIO,
    );

    expect(await screen.findByText('Costo de envío')).toBeTruthy();
    // Se espera al precio y no al rótulo: mientras la cotización viaja, esa celda dice
    // "Calculando el costo de envío…".
    expect((await screen.findAllByText(/9\.540/)).length).toBeGreaterThan(0);
    expect(screen.getByText('Total a pagar')).toBeTruthy();
    expect((await screen.findAllByText(/159\.540/)).length).toBeGreaterThan(0);
  });

  /** Recogiendo en el punto no hay flete que desglosar, y el total es el subtotal. */
  it('con retiro en punto, no muestra línea de envío y el total es el subtotal', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderConDatos(
      'CONTRAENTREGA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(),
    );

    expect(await screen.findByText('Total a pagar')).toBeTruthy();
    expect(screen.queryByText('Costo de envío')).toBeFalsy();
    // Subtotal y total valen lo mismo cuando no hay flete, así que aparece dos veces.
    expect(await screen.findAllByText(/150\.000/)).toHaveLength(2);
  });

  it('muestra correo, tipo de entrega, método de pago y subtotal', async () => {
    sembrarCarritoId('carrito-1');

    await renderConDatos(
      'CONTRAENTREGA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(),
    );

    expect(await screen.findByText('compra@ejemplo.co')).toBeTruthy();
    // Quien recibe y su teléfono se revisan aquí, donde se finaliza la transacción.
    expect(screen.getByText('Ana Pérez')).toBeTruthy();
    expect(screen.getByText('3138816711')).toBeTruthy();
    // Del JSON y no repetida aquí: la etiqueta ya cambió una vez —le sobraba un "sin costo" que
    // es falso mientras el flete va embebido en el precio— y lo que se verifica es que se pinte.
    expect(screen.getByText(esCheckout.resumen.retiro_en_punto)).toBeTruthy();
    expect(screen.getByText('Pago contra entrega')).toBeTruthy();
  });

  /**
   * Quien retira necesita saber a dónde va, y este es el único punto del recorrido donde se le
   * puede decir antes de pagar. Va junto al tipo de entrega y no dentro de la etiqueta de la
   * opción: una etiqueta de radio con la dirección dentro no se lee bien con lector de pantalla.
   */
  it('con retiro en punto, muestra la dirección del punto y cómo se coordina', async () => {
    sembrarCarritoId('carrito-1');

    await renderConDatos(
      'CONTRAENTREGA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(),
    );

    expect(await screen.findByText(esCheckout.resumen.retiro_direccion)).toBeTruthy();
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
    const pedidos = new RepositorioPedidosFalso(
      pedidoDePrueba({ metodoPago: 'TRANSFERENCIA_MANUAL' }),
    );
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
      expect.objectContaining({
        queryParams: { pedidoId: 'pedido-1', correo: 'compra@ejemplo.co' },
      }),
    );
    expect(pagos.llamadasCrearIntento).toBe(0);
  });

  it('con contraentrega, navega a la pantalla de estado', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'CONTRAENTREGA' }));

    const { fixture } = await renderConDatos(
      'CONTRAENTREGA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
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
      await screen.findByText(
        'No se pudo confirmar el pedido. Revisa tus datos e intenta de nuevo.',
      ),
    ).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
  });

  /**
   * Sin tarifa el servidor responde 409 y hace bien. Esta pantalla mandaba el pedido igual y
   * traducía ese 409 a "revisa tus datos e intenta de nuevo": culpaba al comprador de algo que no
   * era suyo y le proponía lo único que no arregla nada, que es reintentar. Ahora ni se manda, y
   * el texto dice qué pasó y cuál es la salida.
   */
  it('sin cobertura no manda el pedido y explica la salida en vez de culpar al comprador', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const pedidos = new RepositorioPedidosFalso();

    const { fixture } = await renderConDatos(
      'TRANSFERENCIA_MANUAL',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      new RepositorioPagosFalso(),
      DATOS_ENTREGA_A_DOMICILIO,
      new RepositorioEnviosFalso(null),
    );
    await esperarCarritoCargado(fixture);
    expect(await screen.findByText(/No tenemos transporte hasta esta dirección/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(
      await screen.findByText(/no podemos crear este pedido[\s\S]*recoger en nuestro punto/),
    ).toBeTruthy();
    expect(pedidos.llamadasCrear).toBe(0);
  });

  /**
   * Y el cuarto motivo, que caía en el `@else` de la caída y por eso decía "inténtalo de nuevo en
   * unos minutos": el proveedor rechazó los datos del envío y el reintento trae el mismo rechazo.
   * Aquí el texto es el que dice qué hacer, así que una invitación falsa cuesta más que en el
   * resumen.
   */
  it('una cotización rechazada bloquea el pedido sin prometer que reintentar sirve', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const pedidos = new RepositorioPedidosFalso();

    const { fixture } = await renderConDatos(
      'TRANSFERENCIA_MANUAL',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      new RepositorioPagosFalso(),
      DATOS_ENTREGA_A_DOMICILIO,
      new RepositorioEnviosFalso({ tipo: 'COTIZACION_RECHAZADA' }),
    );
    await esperarCarritoCargado(fixture);
    expect(await screen.findByText(/No podemos calcular el envío a domicilio/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(
      await screen.findByText(
        /No podemos enviar este pedido a domicilio[\s\S]*recoger en nuestro punto/,
      ),
    ).toBeTruthy();
    expect(screen.queryByText(/Inténtalo de nuevo en unos minutos/)).toBeNull();
    expect(pedidos.llamadasCrear).toBe(0);
  });

  /**
   * El otro motivo por el que puede faltar la tarifa, y no se dice igual: una caída nuestra no es
   * "no llegamos a esa dirección". Esta pantalla los mezclaba en un solo `@else`.
   */
  it('si la cotización se cae lo dice como falla nuestra, no como falta de cobertura', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const pedidos = new RepositorioPedidosFalso();

    const { fixture } = await renderConDatos(
      'TRANSFERENCIA_MANUAL',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      new RepositorioPagosFalso(),
      DATOS_ENTREGA_A_DOMICILIO,
      new RepositorioEnviosFalso(new Error('la red se cayó')),
    );
    await esperarCarritoCargado(fixture);

    // Timeout explícito: la consulta reintenta una vez antes de darse por vencida (ver
    // `usarCotizacionEnvio`), así que `isError()` llega alrededor de un segundo después del
    // primer fallo — por encima del segundo que Testing Library espera por omisión.
    expect(
      await screen.findByText(/No pudimos calcular el costo de envío/, {}, { timeout: 5_000 }),
    ).toBeTruthy();
    expect(screen.queryByText(/No tenemos transporte hasta esta dirección/)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    await vi.waitFor(() => expect(pedidos.llamadasCrear).toBe(0));
  });

  /**
   * `costoEnvio` cae a cero sin cotización, así que "Total a pagar" mostraba el subtotal: un
   * precio que no es el precio, en la pantalla misma donde se finaliza la transacción. El artículo
   * 50 de la Ley 1480 de 2011 pide ahí el desglose completo, y un total incompleto no lo es.
   */
  it('sin cobertura no pinta un total que no es el total', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderConDatos(
      'TRANSFERENCIA_MANUAL',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(),
      new RepositorioPagosFalso(),
      DATOS_ENTREGA_A_DOMICILIO,
      new RepositorioEnviosFalso(null),
    );
    await esperarCarritoCargado(fixture);

    expect(await screen.findByText('Falta el costo de envío')).toBeTruthy();
  });

  it('un reintento no vuelve a crear el pedido si ya existe uno de este intento', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'CONTRAENTREGA' }));

    const { fixture } = await renderConDatos(
      'CONTRAENTREGA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
    );
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
      contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
      autorizaDatos: true,
    });
    expect(pedidos.llamadasCrear).toBe(1);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));
    await vi.waitFor(() => expect(pedidos.llamadasCrear).toBe(1));
  });

  // Las dos caras de la misma decisión: el carrito se limpia cuando ya no hay vuelta atrás, no
  // cuando se crea el pedido. Ver el comentario en `confirmar()`.
  it('al terminar el pedido, el carrito queda limpio', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'CONTRAENTREGA' }));

    const { fixture } = await renderConDatos(
      'CONTRAENTREGA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
    );
    await screen.findByText('compra@ejemplo.co');
    await esperarCarritoCargado(fixture);
    const carrito = fixture.debugElement.injector.get(CarritoStore);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    await vi.waitFor(() => {
      expect(carrito.carritoId()).toBeNull();
      expect(new CarritoIdLocalStorageAlmacen().leer()).toBeNull();
    });
  });

  it('si el intento de pago falla, el carrito queda intacto para reintentar', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'TARJETA' }));

    const { fixture } = await renderConDatos(
      'TARJETA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      new RepositorioPagosQueFalla(),
    );
    await screen.findByText('compra@ejemplo.co');
    await esperarCarritoCargado(fixture);
    const carrito = fixture.debugElement.injector.get(CarritoStore);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(
      await screen.findByText(
        'No se pudo confirmar el pedido. Revisa tus datos e intenta de nuevo.',
      ),
    ).toBeTruthy();
    expect(carrito.carritoId()).toBe('carrito-1');
    expect(new CarritoIdLocalStorageAlmacen().leer()).toBe('carrito-1');
  });
});
