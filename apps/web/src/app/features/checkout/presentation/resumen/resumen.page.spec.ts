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
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../../../carrito/domain/repositorio-carrito.puerto';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { MetodoPago, Pedido, Seguimiento } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import { CotizacionEnvio, CotizarEnvioComando } from '../../domain/envio.model';
import { REPOSITORIO_ENVIOS, RepositorioEnvios } from '../../domain/repositorio-envios.puerto';
import { ResumenPage } from './resumen.page';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import { proveerAlmacenesCarrito, sembrarCarritoId, sembrarSnapshotLinea } from '../../../../../testing/carrito';

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

  constructor(private readonly respuesta: CotizacionEnvio | null | Error = null) {}

  async cotizar(): Promise<CotizacionEnvio | null> {
    this.llamadas += 1;
    if (this.respuesta instanceof Error) {
      throw this.respuesta;
    }
    return this.respuesta;
  }
}

class RepositorioEnviosPorCiudad implements RepositorioEnvios {
  constructor(private readonly porCiudad: Record<string, CotizacionEnvio | null>) {}

  async cotizar(comando: CotizarEnvioComando): Promise<CotizacionEnvio | null> {
    return this.porCiudad[comando.direccion.codigoDaneCiudad] ?? null;
  }
}

const COTIZACION: CotizacionEnvio = {
  costoEnvio: 9_540,
  moneda: 'COP',
  transportadora: '99 minutes',
  diasEstimados: 2,
  venceEn: '2026-09-12T12:00:00Z',
  admiteContraentrega: false,
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

async function renderResumen(carrito: RepositorioCarrito, envios: RepositorioEnvios = new RepositorioEnviosFalso(null)) {
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
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_CARRITO, useValue: carrito },
      { provide: REPOSITORIO_PEDIDOS, useValue: new RepositorioPedidosFalso() },
      { provide: REPOSITORIO_PAGOS, useValue: new RepositorioPagosFalso() },
      { provide: REPOSITORIO_ENVIOS, useValue: envios },
    ],
  });
}

/** Deja el formulario en el estado que dispara la cotización: ciudad y calle. */
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

    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS), new RepositorioEnviosFalso(COTIZACION));
    await screen.findByText('Morral urbano');

    await llenarDireccionEnMedellin();

    expect(await screen.findByText('Costo de envío')).toBeTruthy();
    expect(screen.getAllByText(/9\.540/).length).toBeGreaterThan(0);
    expect(screen.getByText('Total a pagar')).toBeTruthy();
    expect(screen.getAllByText(/309\.540/).length).toBeGreaterThan(0);
    expect(screen.getByText('Entrega estimada: 2 días')).toBeTruthy();
  });

  /**
   * Sin cobertura no se puede continuar: el pedido respondería el mismo 409 dos pantallas
   * después. Se dice aquí, con la salida —recoger en el punto— en el mismo texto.
   */
  it('sin cobertura lo explica y bloquea el botón de continuar', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS), new RepositorioEnviosFalso(null));
    await screen.findByText('Morral urbano');

    await llenarDireccionEnMedellin();

    expect(
      await screen.findByText(/No tenemos transporte hasta esta dirección/),
    ).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Continuar' }).hasAttribute('disabled')).toBe(true);
  });

  it('el retiro en punto no cotiza nada', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const envios = new RepositorioEnviosFalso(COTIZACION);

    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS), envios);
    await screen.findByText('Morral urbano');

    fireEvent.change(screen.getByLabelText('Tipo de entrega'), { target: { value: 'RETIRO_EN_PUNTO' } });

    expect(envios.llamadas).toBe(0);
  });

  /** El ahorro solo se muestra si de verdad se cotizó: una cifra inventada sería peor que nada. */
  it('al cambiar a retiro en punto dice cuánto se ahorra de envío', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS), new RepositorioEnviosFalso(COTIZACION));
    await screen.findByText('Morral urbano');

    await llenarDireccionEnMedellin();
    await screen.findByText('Costo de envío');

    fireEvent.change(screen.getByLabelText('Tipo de entrega'), { target: { value: 'RETIRO_EN_PUNTO' } });

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

    fireEvent.change(screen.getByLabelText('Tipo de entrega'), { target: { value: 'RETIRO_EN_PUNTO' } });

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

    fireEvent.change(screen.getByLabelText('Tipo de entrega'), { target: { value: 'RETIRO_EN_PUNTO' } });

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

  it('con datos válidos, guarda el borrador con la dirección resuelta', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));

    const { fixture } = await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS));
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), { target: { value: 'compra@ejemplo.co' } });
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
      },
      autorizaDatos: true,
    });
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

    fireEvent.input(screen.getByLabelText('Correo electrónico'), { target: { value: 'compra@ejemplo.co' } });
    fireEvent.change(screen.getByLabelText('Departamento'), { target: { value: '05' } });
    fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: '05001' } });
    fireEvent.input(screen.getByLabelText('Dirección'), { target: { value: 'Cra. 26C #38B-31' } });

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(
      await screen.findByText('Tienes que autorizar el tratamiento de datos para continuar.'),
    ).toBeTruthy();
  });

  it('sin marcar la autorización de datos, no guarda el borrador', async () => {
    sembrarCarritoId('carrito-1');
    sembrarSnapshotLinea(snapshotDePrueba('variante-1'));
    const { fixture } = await renderResumen(new RepositorioCarritoFalso(CARRITO_CON_LINEAS));
    await screen.findByText('Morral urbano');
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    fireEvent.input(screen.getByLabelText('Correo electrónico'), { target: { value: 'compra@ejemplo.co' } });
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
