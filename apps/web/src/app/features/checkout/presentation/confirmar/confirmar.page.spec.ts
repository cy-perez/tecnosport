import { Component, inject } from '@angular/core';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { Carrito, CarritoCotizado } from '../../../carrito/domain/carrito.model';
import {
  REPOSITORIO_CARRITO,
  RepositorioCarrito,
} from '../../../carrito/domain/repositorio-carrito.puerto';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { CheckoutStore } from '../../application/checkout.store';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import { DocumentoComprador, IntentoSistecredito } from '../../domain/intento-sistecredito.model';
import {
  CrearPedidoComando,
  DatosEntrega,
  MetodosDePagoDisponiblesComando,
} from '../../domain/pedido.comandos';
import { MetodoDePagoConPrecio, MetodoPago, Pedido, Seguimiento } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import {
  CotizacionEnvio,
  ResultadoCotizacion,
  ModalidadesDeEntrega,
} from '../../domain/envio.model';
import { REPOSITORIO_ENVIOS, RepositorioEnvios } from '../../domain/repositorio-envios.puerto';
import { ConfirmarPage } from './confirmar.page';
import { CarritoIdLocalStorageAlmacen } from '../../../carrito/infrastructure/carrito-id.almacen';
import {
  proveerAlmacenesCarrito,
  sembrarCarritoId,
  sembrarSnapshotLinea,
} from '../../../../../testing/carrito';
import {
  ALMACEN_CORREO_DE_PEDIDO,
  AlmacenCorreoDePedido,
} from '../../domain/almacen-correo-de-pedido.puerto';
import {
  ALMACEN_CARRITO_POR_PAGAR,
  AlmacenCarritoPorPagar,
} from '../../domain/almacen-carrito-por-pagar.puerto';

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

/** Qué carrito salió a pagarse en la pasarela, en memoria. */
class CarritosPorPagarEnMemoria implements AlmacenCarritoPorPagar {
  readonly anotados = new Map<string, string>();
  anotar(pedidoId: string, carritoId: string): void {
    this.anotados.set(pedidoId, carritoId);
  }
  carritoDe(pedidoId: string): string | null {
    return this.anotados.get(pedidoId) ?? null;
  }
  olvidar(pedidoId: string): void {
    this.anotados.delete(pedidoId);
  }
}

let carritosPorPagar = new CarritosPorPagarEnMemoria();

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
      precioContado: PRECIO_DE_HOY,
      subtotalContado: PRECIO_DE_HOY * linea.cantidad,
    }));
    return {
      lineas,
      subtotal: lineas.reduce((suma, linea) => suma + linea.subtotal, 0),
      subtotalContado: lineas.reduce((suma, linea) => suma + linea.subtotalContado, 0),
    };
  }
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
    metodoPago: 'WOMPI',
    estado: 'PAGO_PENDIENTE',
    subtotal: { valor: 150_000, moneda: 'COP' },
    descuento: { valor: 0, moneda: 'COP' },
    costoEnvio: { valor: 0, moneda: 'COP' },
    total: { valor: 150_000, moneda: 'COP' },
    creadoEn: '2026-01-01T00:00:00Z',
    contacto: null,
    datosTransferencia: null,
    transportadora: null,
    ...overrides,
  };
}

class RepositorioPedidosFalso implements RepositorioPedidos {
  llamadasCrear = 0;
  ultimoComando: CrearPedidoComando | null = null;

  constructor(private pedido: Pedido = pedidoDePrueba()) {}

  async crear(_comando: CrearPedidoComando): Promise<Pedido> {
    this.llamadasCrear++;
    this.ultimoComando = _comando;
    return { ...this.pedido, metodoPago: _comando.metodoPago };
  }

  async metodosDePagoConPrecio(): Promise<MetodoDePagoConPrecio[]> {
    throw new Error('no usado en esta prueba');
  }

  async reintentarPago(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async consultarSeguimiento(): Promise<Seguimiento | null> {
    throw new Error('no usado en esta prueba');
  }

  async solicitarEnlaceDeSeguimiento(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}

/** El servidor ya no acepta la recogida: la pestaña se abrió antes de que se apagara. */
class RepositorioPedidosSinRetiro extends RepositorioPedidosFalso {
  override async crear(): Promise<Pedido> {
    throw new ErrorHttp(409, 'recogida apagada', 'RETIRO_EN_PUNTO_NO_DISPONIBLE');
  }
}

/** El enlace de compra del color de tecnología venció entre el carrito y este botón (ADR-0086). */
class RepositorioPedidosConEnlaceVencido extends RepositorioPedidosFalso {
  override async crear(): Promise<Pedido> {
    throw new ErrorHttp(410, 'enlace vencido', 'ENLACE_DE_COMPRA_NO_VIGENTE');
  }
}

class RepositorioPedidosQueFalla implements RepositorioPedidos {
  async crear(): Promise<Pedido> {
    throw new Error('el servidor rechazó el pedido');
  }

  async metodosDePagoConPrecio(): Promise<MetodoDePagoConPrecio[]> {
    throw new Error('no usado en esta prueba');
  }

  async reintentarPago(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async consultarSeguimiento(): Promise<Seguimiento | null> {
    throw new Error('no usado en esta prueba');
  }

  async solicitarEnlaceDeSeguimiento(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}

class RepositorioPagosQueFalla implements RepositorioPagos {
  async crearIntento(): Promise<IntentoDePago> {
    throw new Error('el proveedor de pagos no respondió');
  }

  async crearIntentoSistecredito(): Promise<IntentoSistecredito> {
    throw new Error('no usado en esta prueba');
  }

  async registrarIdTransaccion(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}

/**
 * La pasarela rechazando el credito, con la forma exacta que manda el backend: un 409 con el
 * `motivoSistecredito` ya clasificado. Es el rechazo con codigo `4` de la prueba contra dev del 23
 * de septiembre de 2026, que caia en el mensaje generico; ahora el servidor lo clasifica.
 */
class RepositorioPagosQueRechazaElCredito implements RepositorioPagos {
  async crearIntento(): Promise<IntentoDePago> {
    throw new Error('no usado en esta prueba');
  }

  async crearIntentoSistecredito(): Promise<IntentoSistecredito> {
    throw new ErrorHttp(409, 'sin url de pago', 'SISTECREDITO_NO_ENTREGO_LA_URL_DE_PAGO', {
      motivoSistecredito: 'CREDITO_NEGADO',
    });
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
      montoEnCentavos: 18_990_000,
    };
  }

  async crearIntentoSistecredito(): Promise<IntentoSistecredito> {
    throw new Error('no usado en esta prueba');
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
  transportadora: null,
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
  transportadora: null,
};

function snapshotDePrueba(varianteId: string) {
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

const TODOS_LOS_MEDIOS: readonly MetodoPago[] = [
  'CONTRAENTREGA',
  'PAGO_EN_PUNTO',
  'SISTECREDITO',
  'TRANSFERENCIA_MANUAL',
  'WOMPI',
];

/**
 * Lo que el servidor contesta a `metodos-de-pago-con-precio` (ADR-0095), que confirmar vuelve a
 * pedir con el carrito de ese momento. Por omisión ofrece todos los medios al precio de hoy, por la
 * cantidad del comando y sin descuento; `precio` cambia un medio y `ofrecidos` quita los demás.
 */
class ServidorDeMetodos {
  readonly comandos: MetodosDePagoDisponiblesComando[] = [];
  ofrecidos: readonly MetodoPago[] = TODOS_LOS_MEDIOS;
  precioUnitario = PRECIO_DE_HOY;
  /** Un medio con su subtotal y descuento fijos, como en las pruebas de descuento. */
  fijo: MetodoDePagoConPrecio | null = null;
  /** Si está, la respuesta espera hasta que se llame a `soltar`. */
  private espera: Promise<void> | null = null;
  private soltarEspera: (() => void) | null = null;

  retener(): void {
    this.espera = new Promise((resolver) => (this.soltarEspera = resolver));
  }

  soltar(): void {
    this.soltarEspera?.();
    this.espera = null;
  }

  async responder(comando: MetodosDePagoDisponiblesComando): Promise<MetodoDePagoConPrecio[]> {
    this.comandos.push(comando);
    if (this.espera) {
      await this.espera;
    }
    const unidades = comando.lineas.reduce((suma, linea) => suma + linea.cantidad, 0);
    return this.ofrecidos.map((metodo) =>
      this.fijo?.metodo === metodo
        ? this.fijo
        : {
            metodo,
            subtotal: { valor: this.precioUnitario * unidades, moneda: 'COP' },
            descuento: { valor: 0, moneda: 'COP' },
          },
    );
  }
}

/** El repositorio de la prueba, con los métodos de pago respondidos por el servidor de arriba. */
function conMetodos(pedidos: RepositorioPedidos, servidor: ServidorDeMetodos): RepositorioPedidos {
  return {
    crear: (comando) => pedidos.crear(comando),
    metodosDePagoConPrecio: (comando) => servidor.responder(comando),
    reintentarPago: (pedidoId, correo) => pedidos.reintentarPago(pedidoId, correo),
    consultarSeguimiento: (pedidoId, correo) => pedidos.consultarSeguimiento(pedidoId, correo),
    solicitarEnlaceDeSeguimiento: (numero, correo) =>
      pedidos.solicitarEnlaceDeSeguimiento(numero, correo),
  };
}

/** Mismo motivo que en `metodo-pago.page.spec.ts`: el guardia corre en el
 * primer `effect()`, así que los datos tienen que existir antes de que
 * `ConfirmarPage` se construya. */
function anfitrionConDatos(
  metodoPago: MetodoPago | MetodoDePagoConPrecio,
  datos: DatosEntrega = DATOS_ENTREGA,
  documento: DocumentoComprador | null = null,
) {
  @Component({
    selector: 'app-anfitrion-de-prueba',
    imports: [ConfirmarPage],
    template: `<app-confirmar />`,
  })
  class AnfitrionDePrueba {
    private readonly checkout = inject(CheckoutStore);

    constructor() {
      this.checkout.guardarDatosEntrega(datos);
      // Solo el medio: el precio lo vuelve a pedir la pantalla (ADR-0095).
      this.checkout.elegirMetodoPago(
        typeof metodoPago === 'string' ? metodoPago : metodoPago.metodo,
      );
      // Sin documento, la pantalla de Sistecredito se va a pedirlo y nunca llama a la pasarela:
      // vive solo en memoria y no sobrevive a una recarga, a proposito.
      this.checkout.anotarDocumentoComprador(documento);
    }
  }
  return AnfitrionDePrueba;
}

@Component({ selector: 'app-ruta-muda', template: '' })
class RutaMuda {}

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
class RepositorioEnviosFalso implements RepositorioEnvios {
  retiroEnPunto = true;

  async modalidades(): Promise<ModalidadesDeEntrega> {
    return { envioADomicilio: true, retiroEnPunto: this.retiroEnPunto };
  }

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
  opciones: [],
};

async function renderConDatos(
  metodoPago: MetodoPago | MetodoDePagoConPrecio,
  carrito: RepositorioCarrito,
  pedidos: RepositorioPedidos,
  pagos: RepositorioPagos = new RepositorioPagosFalso(),
  datos: DatosEntrega = DATOS_ENTREGA,
  envios: RepositorioEnvios = new RepositorioEnviosFalso(),
  documento: DocumentoComprador | null = null,
  servidor: ServidorDeMetodos = new ServidorDeMetodos(),
  queryClient: QueryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  // Un medio con su precio es el que el servidor devuelve así: con su subtotal y su descuento.
  if (typeof metodoPago !== 'string' && servidor.fijo === null) {
    servidor.fijo = metodoPago;
  }
  return render(anfitrionConDatos(metodoPago, datos, documento), {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'checkout/es': esCheckout } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      { provide: ALMACEN_CORREO_DE_PEDIDO, useValue: almacenDesde() },
      { provide: ALMACEN_CARRITO_POR_PAGAR, useValue: carritosPorPagar },
      ...proveerAlmacenesCarrito(),
      provideRouter([
        { path: 'metodo-pago', component: RutaMuda },
        { path: 'transferencia', component: RutaMuda },
        { path: 'estado', component: RutaMuda },
      ]),
      provideTanStackQuery(queryClient),
      { provide: REPOSITORIO_CARRITO, useValue: carrito },
      { provide: REPOSITORIO_PEDIDOS, useValue: conMetodos(pedidos, servidor) },
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
  // Y al precio fresco del medio: mientras se pide, el botón va ocupado y no confirma.
  await screen.findByRole('button', { name: 'Confirmar pedido' });
  await vi.waitFor(() =>
    expect(screen.queryAllByText(esCheckout.confirmar.precios_calculando)).toHaveLength(0),
  );
}

describe('ConfirmarPage', () => {
  let ubicacionOriginal: Location;

  beforeEach(() => {
    window.localStorage.clear();
    carritosPorPagar = new CarritosPorPagarEnMemoria();
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
      'WOMPI',
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

  /**
   * ADR-0095: con transferencia el subtotal va al publicado, el descuento resta en su fila y el
   * total es el que dio el servidor para ese medio más el flete. Ninguna cifra sale de sumar precios
   * aquí: 150.000 publicado, 145.000 con el medio, 9.540 de envío.
   */
  it('con un medio con descuento, muestra subtotal publicado, descuento y el total del medio', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderConDatos(
      {
        metodo: 'TRANSFERENCIA_MANUAL',
        subtotal: { valor: 145_000, moneda: 'COP' },
        descuento: { valor: 5_000, moneda: 'COP' },
      },
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(),
      new RepositorioPagosFalso(),
      DATOS_ENTREGA_A_DOMICILIO,
    );

    expect(await screen.findByText('Descuento por medio de pago')).toBeTruthy();
    expect((await screen.findAllByText(/154\.540/)).length).toBeGreaterThan(0);
    expect(screen.getByText(/150\.000/)).toBeTruthy();
    expect(screen.getByText(/-\s?\$\s?5\.000/)).toBeTruthy();
    // El total del publicado (159.540) no se pinta en ningún sitio.
    expect(screen.queryByText(/159\.540/)).toBeFalsy();
  });

  /**
   * La regresión que esto cierra: confirmar enseñaba el subtotal guardado al elegir el medio, y el
   * pedido se cobra con el carrito vivo. Con dos unidades en el carrito, el total es el de dos,
   * dado por el servidor para ese comando.
   */
  it('con el carrito cambiado después de elegir el medio, enseña el total del carrito de ahora', async () => {
    sembrarCarritoId('carrito-1');
    const servidor = new ServidorDeMetodos();

    await renderConDatos(
      'TRANSFERENCIA_MANUAL',
      new RepositorioCarritoFalso({
        ...CARRITO_CON_LINEAS,
        lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 2 }],
      }),
      new RepositorioPedidosFalso(),
      new RepositorioPagosFalso(),
      DATOS_ENTREGA,
      new RepositorioEnviosFalso(),
      null,
      servidor,
    );

    expect(await screen.findAllByText(/300\.000/)).toHaveLength(2);
    expect(screen.queryByText(/150\.000/)).toBeFalsy();
    expect(servidor.comandos.at(-1)?.lineas).toEqual([{ varianteId: 'variante-1', cantidad: 2 }]);
  });

  /**
   * Un precio que cambió en el servidor después de elegir el medio: la caché de la página anterior
   * todavía tiene el viejo, y confirmar no se fía de ella — vuelve a preguntar al entrar.
   */
  it('vuelve a pedir el precio aunque la caché tenga el de hace un momento', async () => {
    sembrarCarritoId('carrito-1');
    const servidor = new ServidorDeMetodos();
    servidor.precioUnitario = 160_000;
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const comando: MetodosDePagoDisponiblesComando = {
      correo: DATOS_ENTREGA.correo,
      lineas: [{ varianteId: 'variante-1', cantidad: 1 }],
      tipoEntrega: DATOS_ENTREGA.tipoEntrega,
      direccion: DATOS_ENTREGA.direccion,
      transportadora: DATOS_ENTREGA.transportadora,
      telefono: DATOS_ENTREGA.contacto.telefono,
    };
    queryClient.setQueryData(
      ['checkout', 'metodos-de-pago-con-precio', comando],
      [
        {
          metodo: 'CONTRAENTREGA',
          subtotal: { valor: 150_000, moneda: 'COP' },
          descuento: { valor: 0, moneda: 'COP' },
        },
      ],
    );

    await renderConDatos(
      'CONTRAENTREGA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(),
      new RepositorioPagosFalso(),
      DATOS_ENTREGA,
      new RepositorioEnviosFalso(),
      null,
      servidor,
      queryClient,
    );

    expect(await screen.findAllByText(/160\.000/)).toHaveLength(2);
    expect(screen.queryByText(/150\.000/)).toBeFalsy();
    expect(servidor.comandos).toHaveLength(1);
  });

  /** La contraentrega dejó de cubrir entre elegirla y confirmar: se vuelve a elegir, y se dice. */
  it('si el medio elegido ya no se ofrece, devuelve a elegir otro con un aviso', async () => {
    sembrarCarritoId('carrito-1');
    const servidor = new ServidorDeMetodos();
    servidor.ofrecidos = ['WOMPI', 'TRANSFERENCIA_MANUAL'];
    const pedidos = new RepositorioPedidosFalso();

    const { fixture } = await renderConDatos(
      'CONTRAENTREGA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      new RepositorioPagosFalso(),
      DATOS_ENTREGA,
      new RepositorioEnviosFalso(),
      null,
      servidor,
    );
    const checkout = fixture.debugElement.injector.get(CheckoutStore);
    const router = fixture.debugElement.injector.get(Router);

    await vi.waitFor(() => expect(checkout.medioYaNoDisponible()).toBe(true));
    expect(checkout.metodoPago()).toBeNull();
    await vi.waitFor(() => expect(router.url).toBe('/metodo-pago'));
    expect(pedidos.llamadasCrear).toBe(0);
  });

  /** Mientras el precio fresco no ha vuelto no hay total que aceptar, y el botón no manda nada. */
  it('no deja confirmar mientras se piden los precios del medio', async () => {
    sembrarCarritoId('carrito-1');
    const servidor = new ServidorDeMetodos();
    servidor.retener();
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'CONTRAENTREGA' }));

    const { fixture } = await renderConDatos(
      'CONTRAENTREGA',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      new RepositorioPagosFalso(),
      DATOS_ENTREGA,
      new RepositorioEnviosFalso(),
      null,
      servidor,
    );
    const carrito = fixture.debugElement.injector.get(CarritoStore);
    await vi.waitFor(() => expect(carrito.consulta.data()).toBeTruthy());
    await vi.waitFor(() => expect(servidor.comandos).toHaveLength(1));

    const ocupado = await screen.findByRole('button', {
      name: esCheckout.confirmar.precios_calculando,
    });
    fireEvent.click(ocupado);
    expect(screen.queryByText(/150\.000/)).toBeFalsy();
    expect(pedidos.llamadasCrear).toBe(0);

    servidor.soltar();
    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar pedido' }));
    await vi.waitFor(() => expect(pedidos.llamadasCrear).toBe(1));
  });

  it('con un medio sin descuento no pinta la fila de descuento', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderConDatos(
      'WOMPI',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(),
      new RepositorioPagosFalso(),
      DATOS_ENTREGA_A_DOMICILIO,
    );

    expect((await screen.findAllByText(/159\.540/)).length).toBeGreaterThan(0);
    expect(screen.queryByText('Descuento por medio de pago')).toBeFalsy();
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
    expect(screen.getByText('Pago contraentrega')).toBeTruthy();
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
      'WOMPI',
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
        queryParams: { pedidoId: 'pedido-1' },
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
      'WOMPI',
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

    expect(await screen.findByText(esCheckout.confirmar.sin_cobertura)).toBeTruthy();
    expect(screen.getByText(/Puedes recoger tu pedido en nuestro punto/)).toBeTruthy();
    expect(pedidos.llamadasCrear).toBe(0);
  });

  it('con la recogida apagada, sin cobertura remite a WhatsApp y no a un punto que no existe', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const pedidos = new RepositorioPedidosFalso();
    const envios = new RepositorioEnviosFalso(null);
    envios.retiroEnPunto = false;

    const { fixture } = await renderConDatos(
      'TRANSFERENCIA_MANUAL',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      new RepositorioPagosFalso(),
      DATOS_ENTREGA_A_DOMICILIO,
      envios,
    );
    await esperarCarritoCargado(fixture);
    expect(await screen.findByText(/No tenemos transporte hasta esta dirección/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(await screen.findByText(esCheckout.confirmar.sin_cobertura)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'escríbenos por WhatsApp' })).toBeTruthy();
    // Una sola vez: la frase con el enlace está en la fila del envío y no se repite en el aviso.
    expect(screen.getAllByText(/escríbenos por WhatsApp/)).toHaveLength(1);
    expect(screen.queryByText(/recoger/i)).toBeNull();
    expect(pedidos.llamadasCrear).toBe(0);
  });

  describe('con la transportadora elegida (ADR-0073)', () => {
    const CON_OPCIONES = {
      ...COTIZACION,
      opciones: [
        { transportadora: '99 minutes', costoEnvio: 9_540, moneda: 'COP', diasEstimados: 2 },
        { transportadora: 'Servientrega', costoEnvio: 12_300, moneda: 'COP', diasEstimados: 1 },
      ],
    };

    /** Se cobra y se muestra la elegida, no la más barata, y el pedido la lleva por nombre. */
    it('muestra su costo y su nombre, y el pedido la lleva', async () => {
      sembrarCarritoId('carrito-1');
      sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
      const pedidos = new RepositorioPedidosFalso();

      const { fixture } = await renderConDatos(
        'TRANSFERENCIA_MANUAL',
        new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
        pedidos,
        new RepositorioPagosFalso(),
        { ...DATOS_ENTREGA_A_DOMICILIO, transportadora: 'Servientrega' },
        new RepositorioEnviosFalso(CON_OPCIONES),
      );
      await esperarCarritoCargado(fixture);

      expect(await screen.findByText('Con Servientrega')).toBeTruthy();
      expect(screen.getAllByText(/12\.300/).length).toBeGreaterThan(0);

      fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));
      await vi.waitFor(() => expect(pedidos.llamadasCrear).toBe(1));
      expect(pedidos.ultimoComando?.transportadora).toBe('Servientrega');
    });

    /** ADR-0086: la línea de un color confirmado con el proveedor lleva su enlace al pedido. */
    it('el pedido lleva el enlace de compra que guardó la línea del carrito', async () => {
      sembrarCarritoId('carrito-1');
      sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
      const pedidos = new RepositorioPedidosFalso();

      const { fixture } = await renderConDatos(
        'TRANSFERENCIA_MANUAL',
        new RepositorioCarritoFalso({
          ...CARRITO_CON_LINEAS,
          lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 1, enlaceCompra: 'tk' }],
        }),
        pedidos,
        new RepositorioPagosFalso(),
        { ...DATOS_ENTREGA_A_DOMICILIO, transportadora: 'Servientrega' },
        new RepositorioEnviosFalso(CON_OPCIONES),
      );
      await esperarCarritoCargado(fixture);
      await screen.findByText('Con Servientrega');

      fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));
      await vi.waitFor(() => expect(pedidos.llamadasCrear).toBe(1));
      expect(pedidos.ultimoComando?.lineas).toEqual([
        { varianteId: 'variante-1', cantidad: 1, enlaceCompra: 'tk' },
      ]);
    });

    it('si la elegida ya no cotiza, no deja confirmar y pide elegir otra', async () => {
      sembrarCarritoId('carrito-1');
      sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
      const pedidos = new RepositorioPedidosFalso();

      const { fixture } = await renderConDatos(
        'TRANSFERENCIA_MANUAL',
        new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
        pedidos,
        new RepositorioPagosFalso(),
        { ...DATOS_ENTREGA_A_DOMICILIO, transportadora: 'Coordinadora' },
        new RepositorioEnviosFalso(CON_OPCIONES),
      );
      await esperarCarritoCargado(fixture);
      expect(
        await screen.findAllByText(esCheckout.confirmar.transportadora_no_disponible),
      ).not.toHaveLength(0);

      fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

      await vi.waitFor(() =>
        expect(
          screen.getAllByText(esCheckout.confirmar.transportadora_no_disponible).length,
        ).toBeGreaterThan(1),
      );
      expect(pedidos.llamadasCrear).toBe(0);
    });
  });

  /** El motivo que el mensaje de bloqueo no tenía: caía en el de la cotización caída. */
  it('un artículo sin medidas bloquea el pedido y se nombra en el aviso', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const pedidos = new RepositorioPedidosFalso();

    const { fixture } = await renderConDatos(
      'TRANSFERENCIA_MANUAL',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
      new RepositorioPagosFalso(),
      DATOS_ENTREGA_A_DOMICILIO,
      new RepositorioEnviosFalso({
        tipo: 'ARTICULO_SIN_MEDIDAS',
        articulos: [{ varianteId: 'variante-1', nombre: 'Proyector portátil' }],
      }),
    );
    await esperarCarritoCargado(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(
      await screen.findByText(
        'Todavía no podemos calcular el envío a domicilio de Proyector portátil. Vuelve atrás y quítalo del carrito.',
      ),
    ).toBeTruthy();
    expect(pedidos.llamadasCrear).toBe(0);
  });

  it('si el servidor ya no acepta la recogida, lo dice y manda a elegir el envío a domicilio', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderConDatos(
      'WOMPI',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosSinRetiro(),
    );
    await esperarCarritoCargado(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(await screen.findByText(esCheckout.confirmar.retiro_no_disponible)).toBeTruthy();
  });

  it('un enlace de compra vencido dice que se vuelve a escribir por WhatsApp', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderConDatos(
      'WOMPI',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosConEnlaceVencido(),
    );
    await esperarCarritoCargado(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(await screen.findByText(esCheckout.confirmar.disponibilidad_por_confirmar)).toBeTruthy();
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

    expect(await screen.findByText(esCheckout.confirmar.envio_rechazado)).toBeTruthy();
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
      transportadora: null,
    });
    expect(pedidos.llamadasCrear).toBe(1);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));
    await vi.waitFor(() => expect(pedidos.llamadasCrear).toBe(1));
  });

  /**
   * La otra cara del reintento: el pedido que quedó de un intento fallido se creó con dos unidades,
   * el comprador volvió al carrito, dejó una y regresó con «Atrás». Reutilizarlo cobraba las dos y
   * después borraba el carrito de una.
   */
  it('si el carrito cambió desde el pedido creado, crea otro con el carrito de ahora', async () => {
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
    await checkout.crearPedido({
      correo: 'compra@ejemplo.co',
      lineas: [{ varianteId: 'variante-1', cantidad: 2 }],
      tipoEntrega: 'RETIRO_EN_PUNTO',
      direccion: null,
      metodoPago: 'CONTRAENTREGA',
      contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
      autorizaDatos: true,
      transportadora: null,
    });

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    await vi.waitFor(() => expect(pedidos.llamadasCrear).toBe(2));
    expect(pedidos.ultimoComando?.lineas).toEqual([
      { varianteId: 'variante-1', cantidad: 1, enlaceCompra: undefined },
    ]);
  });

  /**
   * Terminada la compra, el checkout no guarda con qué volver a cobrarla: sin esto, una segunda
   * compra idéntica en la misma visita reutilizaba el pedido ya confirmado en vez de crear otro.
   */
  it('con la contraentrega confirmada, suelta los datos de entrega, el medio y el pedido', async () => {
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
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    await vi.waitFor(() => {
      expect(checkout.datosEntrega()).toBeNull();
      expect(checkout.metodoPago()).toBeNull();
    });
    expect(pedidos.ultimoComando).not.toBeNull();
    expect(checkout.pedidoReutilizable(pedidos.ultimoComando!)).toBeNull();
    // Y la pantalla de estado, no el resumen al que mandaría la guarda al ver faltar los datos.
    fixture.detectChanges();
    await fixture.whenStable();
    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith(
      ['../estado'],
      expect.objectContaining({ queryParams: { pedidoId: 'pedido-1' } }),
    );
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

  /**
   * Salir a la pasarela no es haber pagado: quien cancela en Wompi vuelve a un carrito vacío si se
   * vacía aquí. Se anota cuál carrito salió a pagarse, y lo vacía la pantalla de estado.
   */
  it('al salir hacia Wompi, el carrito queda lleno y anotado para vaciarlo al volver pagado', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'WOMPI' }));

    const { fixture } = await renderConDatos(
      'WOMPI',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      pedidos,
    );
    await screen.findByText('compra@ejemplo.co');
    await esperarCarritoCargado(fixture);
    const carrito = fixture.debugElement.injector.get(CarritoStore);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    await vi.waitFor(() => expect(window.location.href).toContain('https://checkout.wompi.co/'));
    expect(carrito.carritoId()).toBe('carrito-1');
    expect(new CarritoIdLocalStorageAlmacen().leer()).toBe('carrito-1');
    expect(carritosPorPagar.carritoDe('pedido-1')).toBe('carrito-1');
  });

  it('si el intento de pago falla, el carrito queda intacto para reintentar', async () => {
    sembrarCarritoId('carrito-1');
    const pedidos = new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'WOMPI' }));

    const { fixture } = await renderConDatos(
      'WOMPI',
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

  /**
   * <b>Lo que ve quien pide un credito y se lo niegan.</b> Medido contra dev el 23 de septiembre
   * de 2026 con el rechazo simulado: la pasarela devolvio `codeResponse = 4` y la pantalla dijo
   * "No se pudo confirmar el pedido. Revisa tus datos e intenta de nuevo". Las dos mitades de ese
   * consejo son falsas —los datos no tienen nada que ver con una decision de credito, y volver a
   * pulsar tampoco sirve porque el pedido queda en PAGO_FALLIDO en cuanto llega la notificacion—.
   */
  it('un credito rechazado se cuenta como lo que es y ofrece la unica salida que hay', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderConDatos(
      'SISTECREDITO',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'SISTECREDITO' })),
      new RepositorioPagosQueRechazaElCredito(),
      DATOS_ENTREGA,
      new RepositorioEnviosFalso(),
      { tipoDocumento: 'CC', documento: '1017254896' },
    );
    await esperarCarritoCargado(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(await screen.findByText(esCheckout.confirmar.sistecredito_rechazado)).toBeTruthy();
    expect(screen.queryByText(esCheckout.confirmar.error)).toBeFalsy();
    expect(
      screen.getByRole('button', { name: esCheckout.confirmar.elegir_otro_metodo }),
    ).toBeTruthy();
  });

  /** Y la salida lleva a donde se elige el medio, por el mismo camino que usa el resto del archivo. */
  it('la salida del rechazo lleva a elegir otro medio de pago', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderConDatos(
      'SISTECREDITO',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(pedidoDePrueba({ metodoPago: 'SISTECREDITO' })),
      new RepositorioPagosQueRechazaElCredito(),
      DATOS_ENTREGA,
      new RepositorioEnviosFalso(),
      { tipoDocumento: 'CC', documento: '1017254896' },
    );
    await esperarCarritoCargado(fixture);
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));
    await screen.findByText(esCheckout.confirmar.sistecredito_rechazado);

    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');
    fireEvent.click(screen.getByRole('button', { name: esCheckout.confirmar.elegir_otro_metodo }));

    await vi.waitFor(() =>
      expect(navegar).toHaveBeenCalledWith(['../metodo-pago'], expect.anything()),
    );
  });

  /** Un fallo que no es un rechazo se queda con el texto de siempre, y sin salida que ofrecer. */
  it('un fallo cualquiera no se disfraza de rechazo de credito', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderConDatos(
      'WOMPI',
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosQueFalla(),
    );
    await esperarCarritoCargado(fixture);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }));

    expect(await screen.findByText(esCheckout.confirmar.error)).toBeTruthy();
    expect(
      screen.queryByRole('button', { name: esCheckout.confirmar.elegir_otro_metodo }),
    ).toBeFalsy();
  });
});
