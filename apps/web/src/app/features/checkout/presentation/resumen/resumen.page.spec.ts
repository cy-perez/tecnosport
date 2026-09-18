import { Component } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCarrito from '../../../../../assets/i18n/scopes/carrito/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { CheckoutStore } from '../../application/checkout.store';
import { Carrito } from '../../../carrito/domain/carrito.model';
import { SnapshotLinea } from '../../../carrito/domain/snapshot-linea.model';
import {
  REPOSITORIO_CARRITO,
  RepositorioCarrito,
} from '../../../carrito/domain/repositorio-carrito.puerto';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { MetodoPago, Pedido, Seguimiento } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import {
  CotizacionEnvio,
  CotizarEnvioComando,
  ResultadoCotizacion,
} from '../../domain/envio.model';
import { REPOSITORIO_ENVIOS, RepositorioEnvios } from '../../domain/repositorio-envios.puerto';
import { ResumenPage } from './resumen.page';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import {
  proveerAlmacenesCarrito,
  sembrarCarritoId,
  sembrarSnapshotLinea,
} from '../../../../../testing/carrito';

class RepositorioPagosFalso implements RepositorioPagos {
  async crearIntento(): Promise<IntentoDePago> {
    throw new Error('no usado en esta prueba');
  }

  async registrarIdTransaccion(): Promise<void> {
    throw new Error('no usado en esta prueba');
  }
}

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

class RepositorioPedidosFalso implements RepositorioPedidos {
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
    throw new Error('no usado en esta prueba');
  }
}

class RepositorioEnviosFalso implements RepositorioEnvios {
  llamadas = 0;

  constructor(
    private readonly respuesta: CotizacionEnvio | ResultadoCotizacion | null | Error = null,
  ) {}

  /**
   * Acepta una tarifa suelta o un resultado entero. Lo primero es azúcar para los casos de
   * siempre —`null` sigue siendo "sin cobertura", como cuando el puerto devolvía eso— y lo
   * segundo es lo que necesita el caso del artículo no asegurable, que lleva datos consigo.
   */
  async cotizar(): Promise<ResultadoCotizacion> {
    this.llamadas += 1;
    if (this.respuesta instanceof Error) {
      throw this.respuesta;
    }
    return resultadoDe(this.respuesta);
  }
}

/**
 * Cotiza cuando la prueba lo diga, no cuando la promesa quiera. Es lo único que reproduce la
 * carrera real: el comprador —o Playwright, que tarda milisegundos— da clic en "Continuar" con la
 * cotización todavía en vuelo. Un doble que responde de inmediato nunca la ve.
 */
class RepositorioEnviosDiferido implements RepositorioEnvios {
  private responder!: (resultado: ResultadoCotizacion) => void;
  private readonly enVuelo = new Promise<ResultadoCotizacion>((resolver) => {
    this.responder = resolver;
  });

  async cotizar(): Promise<ResultadoCotizacion> {
    return this.enVuelo;
  }

  resolverCon(cotizacion: CotizacionEnvio | null): void {
    this.responder(resultadoDe(cotizacion));
  }
}

class RepositorioEnviosPorCiudad implements RepositorioEnvios {
  constructor(private readonly porCiudad: Record<string, CotizacionEnvio | null>) {}

  async cotizar(comando: CotizarEnvioComando): Promise<ResultadoCotizacion> {
    return resultadoDe(this.porCiudad[comando.direccion.codigoDaneCiudad] ?? null);
  }
}

/** `null` sigue queriendo decir "sin cobertura", que es lo que estos dobles decían antes. */
function resultadoDe(respuesta: CotizacionEnvio | ResultadoCotizacion | null): ResultadoCotizacion {
  if (respuesta === null) {
    return { tipo: 'SIN_COBERTURA' };
  }
  return 'tipo' in respuesta ? respuesta : { tipo: 'TARIFA', cotizacion: respuesta };
}

const COTIZACION: CotizacionEnvio = {
  costoEnvio: 9_540,
  moneda: 'COP',
  transportadora: '99 minutes',
  diasEstimados: 2,
  venceEn: '2026-09-12T12:00:00Z',
};

function snapshotDePrueba(varianteId: string): SnapshotLinea {
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

/** Mismo hallazgo que `carrito.page.spec.ts`: el registro de `PendingTasks` de TanStack Query
 * ocurre dentro de un `effect()` async, `fixture.whenStable()` no alcanza a esperarlo. */

// Una ruta muda basta para que `enviar()` navegue sin que el router
// necesite la página real de `metodo-pago`, fuera del alcance de esta prueba.
@Component({ selector: 'app-metodo-pago-mudo', template: '' })
class MetodoPagoMudo {}

async function renderResumen(
  carrito: RepositorioCarrito,
  envios: RepositorioEnvios = new RepositorioEnviosFalso(null),
) {
  return render(ResumenPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'carrito/es': esCarrito, 'checkout/es': esCheckout } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      ...proveerAlmacenesCarrito(),
      provideRouter([{ path: 'metodo-pago', component: MetodoPagoMudo }]),
      provideTanStackQuery(clienteDePrueba()),
      { provide: REPOSITORIO_CARRITO, useValue: carrito },
      { provide: REPOSITORIO_PEDIDOS, useValue: new RepositorioPedidosFalso() },
      { provide: REPOSITORIO_PAGOS, useValue: new RepositorioPagosFalso() },
      { provide: REPOSITORIO_ENVIOS, useValue: envios },
    ],
  });
}

/**
 * Sin reintentos: TanStack reintenta tres veces con espera creciente por omisión, así que una
 * consulta que falla tarda segundos en llegar a `isError()` y la prueba que comprueba justamente
 * ese estado se agota antes. En el navegador el reintento es deseable; aquí solo alarga.
 */
function clienteDePrueba(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

/** Deja el formulario en el estado que dispara la cotización: ciudad y calle. */
function llenarContacto() {
  fireEvent.input(screen.getByLabelText('Nombre de quien recibe'), {
    target: { value: 'Ana Pérez' },
  });
  fireEvent.input(screen.getByLabelText('Teléfono de contacto'), {
    target: { value: '3138816711' },
  });
}

async function llenarDireccionEnMedellin() {
  fireEvent.change(screen.getByLabelText('Departamento'), { target: { value: '05' } });
  fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: '05001' } });
  fireEvent.input(screen.getByLabelText('Dirección'), { target: { value: 'Circular 4 # 70-20' } });
}

const CARRITO_CON_LINEAS: Carrito = {
  id: 'carrito-1',
  usuarioId: null,
  creadoEn: '2026-01-01T00:00:00Z',
  lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 2 }],
};

describe('ResumenPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  /**
   * El artículo 50 de la Ley 1480 de 2011 exige el desglose antes de pagar: productos, envío
   * aparte y la suma. Hasta la Fase 7 esta pantalla mostraba solo "Subtotal".
   */
  it('con la dirección completa, muestra el costo de envío y el total', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(COTIZACION),
    );
    await screen.findByText('Morral urbano');

    await llenarDireccionEnMedellin();

    // Se espera por el importe y no por la etiqueta: desde el 18 de septiembre de 2026 "Costo de
    // envío" está en pantalla desde el primer momento —con "calculando" en su valor— así que
    // esperarla ya no significa que la cotización llegó.
    expect((await screen.findAllByText(/9\.540/)).length).toBeGreaterThan(0);
    expect(screen.getByText('Costo de envío')).toBeTruthy();
    expect(screen.getByText('Total a pagar')).toBeTruthy();
    expect(screen.getAllByText(/309\.540/).length).toBeGreaterThan(0);
    expect(screen.getByText('Entrega estimada: 2 días')).toBeTruthy();
  });

  /**
   * El artículo que vale más de lo asegurable tampoco deja continuar, y sobre todo: **se nombra**.
   * Es la diferencia con "sin cobertura", donde la salida es corregir la dirección; aquí la salida
   * es quitar ese producto o recogerlo en el punto, y sin el nombre el comprador no sabe cuál de
   * los suyos es (`ADR-0036`).
   */
  it('un artículo no asegurable se nombra y no deja continuar', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso({
        tipo: 'ARTICULO_NO_ASEGURABLE',
        articulos: [{ varianteId: 'variante-1', nombre: 'Portátil para diseño' }],
      }),
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'cliente@tecnosport.co' },
    });
    llenarContacto();
    await llenarDireccionEnMedellin();
    fireEvent.click(screen.getByRole('checkbox'));

    expect(await screen.findByText(/Portátil para diseño/)).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain('supera el máximo');

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(checkout.datosEntrega()).toBeNull();
  });

  /**
   * La cuarta respuesta, y lo que se prueba es lo que el texto **no** dice: nada de "vuelve a
   * intentarlo". El reintento trae el mismo rechazo, porque Skydropx deduplica las cotizaciones por
   * contenido, y prometer lo contrario es dejar al comprador esperando algo que no va a pasar.
   */
  it('una cotización rechazada por el proveedor no invita a reintentar', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso({ tipo: 'COTIZACION_RECHAZADA' }),
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'cliente@tecnosport.co' },
    });
    llenarContacto();
    await llenarDireccionEnMedellin();
    fireEvent.click(screen.getByRole('checkbox'));

    expect(
      await screen.findByText(/No podemos calcular el envío a domicilio de este pedido/),
    ).toBeTruthy();
    expect(screen.queryByText(/Vuelve a intentarlo/)).toBeNull();
    expect(screen.queryByText(/No tenemos transporte hasta esta dirección/)).toBeNull();

    // Y tampoco un total que no es el total, por lo mismo que las otras tres.
    const celdaTotal = screen.getByText('Total a pagar').parentElement;
    expect(celdaTotal?.textContent).toContain('Falta el costo de envío');

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(checkout.datosEntrega()).toBeNull();
  });

  /**
   * Sin cobertura no se puede continuar: el pedido respondería el mismo 409 dos pantallas
   * después. Se dice aquí, con la salida —recoger en el punto— en el mismo texto.
   *
   * El botón **no** se deshabilita, y eso es deliberado: un control deshabilitado sale del orden
   * de tabulación, así que quien navega con teclado llega y no puede enfocarlo para entender por
   * qué. Queda alcanzable y es el envío el que no pasa.
   */
  it('sin cobertura lo explica, deja el botón alcanzable y no deja continuar', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(null),
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'cliente@tecnosport.co' },
    });
    llenarContacto();
    await llenarDireccionEnMedellin();
    fireEvent.click(screen.getByRole('checkbox'));
    expect(await screen.findByText(/No tenemos transporte hasta esta dirección/)).toBeTruthy();

    const continuar = screen.getByRole('button', { name: 'Continuar' });
    expect(continuar.hasAttribute('disabled')).toBe(false);

    fireEvent.click(continuar);

    expect(checkout.datosEntrega()).toBeNull();
  });

  /**
   * La carrera que dejó pasar al recorrido de Playwright en la corrida del 13 de septiembre de
   * 2026: `bloqueadoPorCobertura` mira `isSuccess()`, así que mientras la consulta iba en vuelo
   * valía `false` y el formulario pasaba. Quien llenaba la dirección y daba clic enseguida se
   * saltaba el bloqueo y llegaba a confirmar, donde el pedido respondía 409 y la pantalla le decía
   * "revisa tus datos": no había nada que revisar.
   *
   * La prueba anterior no lo atrapaba porque esperaba a ver el mensaje —y para entonces la
   * cotización ya había vuelto—. Aquí el clic ocurre **antes** de la respuesta, a propósito.
   */
  it('no deja continuar si se hace clic con la cotización todavía en vuelo y no hay cobertura', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const envios = new RepositorioEnviosDiferido();

    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      envios,
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'cliente@tecnosport.co' },
    });
    llenarContacto();
    await llenarDireccionEnMedellin();
    fireEvent.click(screen.getByRole('checkbox'));

    // Sin esperar la cotización: es justo el instante en el que antes se colaba.
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    envios.resolverCon(null);

    await vi.waitFor(() => {
      expect(screen.getByText(/No tenemos transporte hasta esta dirección/)).toBeTruthy();
      expect(checkout.datosEntrega()).toBeNull();
    });
  });

  /**
   * El otro lado, y hace falta: "esperar la cotización" no puede convertirse en "no continuar
   * nunca". Mismo clic prematuro, pero con tarifa, y el paso sí ocurre.
   */
  it('con el clic adelantado, continúa igual cuando la cotización llega con tarifa', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const envios = new RepositorioEnviosDiferido();

    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      envios,
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'cliente@tecnosport.co' },
    });
    llenarContacto();
    await llenarDireccionEnMedellin();
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    envios.resolverCon(COTIZACION);

    await vi.waitFor(() => {
      expect(checkout.datosEntrega()?.correo).toBe('cliente@tecnosport.co');
    });
  });

  /**
   * Una caída de la cotización no es "no llegamos a esa dirección". Se dice distinto —mandar a
   * corregir una dirección que estaba bien es culpar al comprador de lo nuestro— y tampoco deja
   * pasar, porque sin tarifa el pedido responde 409 igual.
   */
  it('si la cotización falla lo dice como falla nuestra, no como falta de cobertura', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(new Error('la red se cayó')),
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'cliente@tecnosport.co' },
    });
    llenarContacto();
    await llenarDireccionEnMedellin();
    fireEvent.click(screen.getByRole('checkbox'));

    // Timeout explícito: la consulta reintenta una vez antes de darse por vencida (ver
    // `usarCotizacionEnvio`), así que `isError()` llega alrededor de un segundo después del
    // primer fallo — por encima del segundo que Testing Library espera por omisión.
    expect(
      await screen.findByText(/No pudimos calcular el costo de envío/, {}, { timeout: 5_000 }),
    ).toBeTruthy();
    expect(screen.queryByText(/No tenemos transporte hasta esta dirección/)).toBeNull();

    // Y tampoco un total falso. Este caso se escapó de la primera versión del arreglo:
    // `totalConocido` comparaba con `!== null`, pero TanStack devuelve `undefined` cuando no hay
    // datos —`null` es aquí un dato— así que con la consulta caída el subtotal seguía pintándose
    // como "Total a pagar". Lo encontró el recorrido en el navegador, no esta prueba.
    const celdaTotal = screen.getByText('Total a pagar').parentElement;
    expect(celdaTotal?.textContent).toContain('Falta el costo de envío');
    expect(celdaTotal?.textContent).not.toMatch(/300\.000/);

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(checkout.datosEntrega()).toBeNull();
  });

  /**
   * Sin flete no hay total. Mostrar el subtotal bajo "Total a pagar" es decir un precio que no es
   * el precio, y el artículo 50 de la Ley 1480 de 2011 pide el desglose completo antes de pagar.
   */
  it('sin cobertura no pinta un total que no es el total', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(null),
    );
    await screen.findByText('Morral urbano');

    await llenarDireccionEnMedellin();

    expect(await screen.findByText(/No tenemos transporte hasta esta dirección/)).toBeTruthy();
    // Se mira la celda del total y no la página entera: 300.000 (2 x 150.000) aparece también
    // como subtotal y en la línea del producto, y contar apariciones sueltas haría que la prueba
    // se rompiera al tocar cualquier otra parte del resumen.
    const celdaTotal = screen.getByText('Total a pagar').parentElement;
    expect(celdaTotal?.textContent).toContain('Falta el costo de envío');
    expect(celdaTotal?.textContent).not.toMatch(/300\.000/);
  });

  it('el retiro en punto no cotiza nada', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const envios = new RepositorioEnviosFalso(COTIZACION);

    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS), envios);
    await screen.findByText('Morral urbano');

    fireEvent.change(screen.getByLabelText('Tipo de entrega'), {
      target: { value: 'RETIRO_EN_PUNTO' },
    });

    expect(envios.llamadas).toBe(0);
  });

  /** El ahorro solo se muestra si de verdad se cotizó: una cifra inventada sería peor que nada. */
  it('al cambiar a retiro en punto dice cuánto se ahorra de envío', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(COTIZACION),
    );
    await screen.findByText('Morral urbano');

    await llenarDireccionEnMedellin();
    await screen.findAllByText(/9\.540/);

    fireEvent.change(screen.getByLabelText('Tipo de entrega'), {
      target: { value: 'RETIRO_EN_PUNTO' },
    });

    expect(await screen.findByText(/Te ahorras .* de envío/)).toBeTruthy();
  });

  /**
   * Lo encontró el recorrido en el navegador. Quien cotiza Medellín, cambia a una ciudad sin
   * transporte y elige recoger, no se ahorra nada: a esa ciudad no había cómo enviarlo. El ahorro
   * de la ciudad anterior no puede sobrevivir al cambio.
   */
  it('no promete ahorro si la última dirección se quedó sin cobertura', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosPorCiudad({ '05001': COTIZACION, '11001': null }),
    );
    await screen.findByText('Morral urbano');

    await llenarDireccionEnMedellin();
    await screen.findByText('Costo de envío');

    fireEvent.change(screen.getByLabelText('Departamento'), { target: { value: '11' } });
    fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: '11001' } });
    await screen.findByText(/No tenemos transporte hasta esta dirección/);

    fireEvent.change(screen.getByLabelText('Tipo de entrega'), {
      target: { value: 'RETIRO_EN_PUNTO' },
    });

    expect(screen.queryByText(/Te ahorras/)).toBeFalsy();
  });

  it('sin carrito guardado, muestra el mensaje de vacío', async () => {
    await renderResumen(new RepositorioCarritoFalso(null));
    expect(await screen.findByText('Tu carrito está vacío.')).toBeTruthy();
  });

  it('con líneas, muestra el producto, el subtotal y el formulario', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS));

    expect(await screen.findByText('Morral urbano')).toBeTruthy();
    expect(screen.getAllByText(/300\.000/).length).toBeGreaterThan(0);
    expect(screen.getByLabelText('Correo electrónico')).toBeTruthy();
    expect(screen.getByLabelText('Dirección')).toBeTruthy();
  });

  it('elegir retiro en punto oculta los campos de dirección', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS));
    await screen.findByText('Morral urbano');

    fireEvent.change(screen.getByLabelText('Tipo de entrega'), {
      target: { value: 'RETIRO_EN_PUNTO' },
    });

    expect(screen.queryByLabelText('Dirección')).toBeFalsy();
  });

  it('enviar el formulario vacío muestra los errores y no guarda nada', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS));
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('El correo es obligatorio.')).toBeTruthy();
    expect(screen.getByText('Elige un departamento.')).toBeTruthy();
    expect(screen.getByText('Elige una ciudad.')).toBeTruthy();
    expect(screen.getByText('La dirección es obligatoria.')).toBeTruthy();
    expect(checkout.datosEntrega()).toBeNull();
  });

  /**
   * Sin quien reciba no hay guía ni mensajero: el pedido nacía sin nombre ni teléfono durante
   * cuatro fases. Se dice en el campo, y no se guarda el borrador.
   */
  it('sin teléfono no guarda el borrador y dice por qué', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(COTIZACION),
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Nombre de quien recibe'), {
      target: { value: 'Ana Pérez' },
    });
    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'compra@ejemplo.co' },
    });
    await llenarDireccionEnMedellin();
    fireEvent.click(screen.getByRole('checkbox'));

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('El teléfono es obligatorio.')).toBeTruthy();
    expect(checkout.datosEntrega()).toBeNull();
  });

  it('un teléfono con letras no pasa', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(COTIZACION),
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    llenarContacto();
    fireEvent.input(screen.getByLabelText('Teléfono de contacto'), {
      target: { value: '313 ABC 6711' },
    });
    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'compra@ejemplo.co' },
    });
    await llenarDireccionEnMedellin();
    fireEvent.click(screen.getByRole('checkbox'));

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('Escribe un teléfono válido, solo números.')).toBeTruthy();
    expect(checkout.datosEntrega()).toBeNull();
  });

  it('con datos válidos, guarda el borrador con la dirección resuelta', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    // Con tarifa, y eso es parte de lo que se prueba: esta prueba pasaba con el cotizador por
    // omisión —que responde "sin cobertura"— porque el clic llegaba antes que la respuesta y el
    // bloqueo no había podido aplicarse todavía. Verde por la carrera, no por el comportamiento.
    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(COTIZACION),
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'compra@ejemplo.co' },
    });
    llenarContacto();
    fireEvent.change(screen.getByLabelText('Departamento'), { target: { value: '05' } });
    fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: '05001' } });
    fireEvent.input(screen.getByLabelText('Dirección'), { target: { value: 'Cra. 26C #38B-31' } });
    fireEvent.click(
      screen.getByLabelText(
        'Autorizo el tratamiento de mis datos personales para procesar y entregar este pedido.',
      ),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await vi.waitFor(() => expect(checkout.datosEntrega()).not.toBeNull());

    expect(checkout.datosEntrega()).toEqual({
      correo: 'compra@ejemplo.co',
      tipoEntrega: 'ENVIO_A_DOMICILIO',
      direccion: {
        codigoDaneDepartamento: '05',
        departamento: 'Antioquia',
        codigoDaneCiudad: '05001',
        ciudad: 'Medellín',
        direccion: 'Cra. 26C #38B-31',
        indicaciones: null,
        barrio: null,
      },
      contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
      autorizaDatos: true,
    });
  });

  /**
   * El barrio es el `area_level3` de la plataforma de envios y su unica puerta es la cotizacion: el
   * cuerpo del envio no declara ese campo y lo descarta sin avisar. Si se quedara en el formulario,
   * la guia se imprimiria sin barrio y nada fallaria.
   */
  it('el barrio escrito viaja con la direccion', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(COTIZACION),
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'compra@ejemplo.co' },
    });
    llenarContacto();
    fireEvent.change(screen.getByLabelText('Departamento'), { target: { value: '05' } });
    fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: '05001' } });
    fireEvent.input(screen.getByLabelText('Dirección'), { target: { value: 'Cra. 26C #38B-31' } });
    fireEvent.input(screen.getByLabelText('Barrio (opcional)'), { target: { value: 'Boston' } });
    fireEvent.click(
      screen.getByLabelText(
        'Autorizo el tratamiento de mis datos personales para procesar y entregar este pedido.',
      ),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await vi.waitFor(() => expect(checkout.datosEntrega()).not.toBeNull());

    expect(checkout.datosEntrega()?.direccion?.barrio).toBe('Boston');
  });

  // `docs/06-testing.md`: axe automatizado en las pantallas clave. Esta es la
  // que más formulario tiene: correo, tipo de entrega y dirección.
  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { container } = await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS));
    await screen.findByLabelText('Correo electrónico');

    await esperarSinViolaciones(container);
  });

  // Sin autorización no hay pedido, y el servidor lo exige igual (Ley 1581 de 2012). Esto es para
  // que el comprador no llegue hasta el 422 después de escribir toda la dirección.
  // Encontrado por el recorrido de Playwright: no guardar el borrador es correcto, pero sin
  // mensaje el comprador pulsa «Continuar» y no pasa nada, sin saber por qué.
  it('sin marcar la autorización, dice por qué no continúa', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS));
    await screen.findByText('Morral urbano');

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'compra@ejemplo.co' },
    });
    llenarContacto();
    fireEvent.change(screen.getByLabelText('Departamento'), { target: { value: '05' } });
    fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: '05001' } });
    fireEvent.input(screen.getByLabelText('Dirección'), { target: { value: 'Cra. 26C #38B-31' } });

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(
      await screen.findByText('Tienes que autorizar el tratamiento de datos para continuar.'),
    ).toBeTruthy();
    // Y atado a la casilla, no solo pintado al lado: quien vuelve a enfocarla tiene que oír por qué
    // está mal. Es una autorización de la Ley 1581 y el mensaje vivía suelto hasta el 18 de
    // septiembre de 2026.
    const casilla = screen.getByLabelText(/Autorizo el tratamiento/);
    expect(casilla.getAttribute('aria-invalid')).toBe('true');
    expect(casilla.getAttribute('aria-describedby')).toBe('resumen-autoriza-datos-error');
  });

  it('sin marcar la autorización de datos, no guarda el borrador', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    // Con tarifa, y eso es parte de lo que se prueba: esta prueba pasaba con el cotizador por
    // omisión —que responde "sin cobertura"— porque el clic llegaba antes que la respuesta y el
    // bloqueo no había podido aplicarse todavía. Verde por la carrera, no por el comportamiento.
    const { fixture } = await renderResumen(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioEnviosFalso(COTIZACION),
    );
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'compra@ejemplo.co' },
    });
    llenarContacto();
    fireEvent.change(screen.getByLabelText('Departamento'), { target: { value: '05' } });
    fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: '05001' } });
    fireEvent.input(screen.getByLabelText('Dirección'), { target: { value: 'Cra. 26C #38B-31' } });

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(checkout.datosEntrega()).toBeNull();
  });

  it('la casilla de autorización nunca arranca marcada', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS));
    await screen.findByText('Morral urbano');

    const casilla = screen.getByLabelText(
      'Autorizo el tratamiento de mis datos personales para procesar y entregar este pedido.',
    ) as HTMLInputElement;

    expect(casilla.checked).toBe(false);
  });

  it('enlaza la política de tratamiento de datos junto a la casilla', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS));
    await screen.findByText('Morral urbano');

    const enlace = screen.getByRole('link', { name: 'Leer la política de tratamiento de datos' });

    expect(enlace.getAttribute('href')).toBe('/es/legales/privacidad');
  });
});
