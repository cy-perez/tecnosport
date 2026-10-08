import { Component, inject } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import { proveerAlmacenesCarrito, sembrarCarritoId } from '../../../../../testing/carrito';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { Carrito, CarritoCotizado } from '../../../carrito/domain/carrito.model';
import {
  REPOSITORIO_CARRITO,
  RepositorioCarrito,
} from '../../../carrito/domain/repositorio-carrito.puerto';
import { CheckoutStore } from '../../application/checkout.store';
import {
  CotizacionEnvio,
  ModalidadesDeEntrega,
  ResultadoCotizacion,
} from '../../domain/envio.model';
import { REPOSITORIO_ENVIOS, RepositorioEnvios } from '../../domain/repositorio-envios.puerto';
import { DatosEntrega, MetodosDePagoDisponiblesComando } from '../../domain/pedido.comandos';
import { MetodoPago } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS } from '../../domain/repositorio-pedidos.puerto';
import { TransportadoraPage } from './transportadora.page';

class RepositorioCarritoFalso implements RepositorioCarrito {
  constructor(private carrito: Carrito | null) {}

  async cotizar(carritoId: string): Promise<CarritoCotizado | null> {
    const carrito = await this.ver(carritoId);
    if (!carrito) {
      return null;
    }
    const lineas = carrito.lineas.map((linea) => ({
      lineaId: linea.id,
      varianteId: linea.varianteId,
      cantidad: linea.cantidad,
      precioUnitario: 150_000,
      subtotal: 150_000 * linea.cantidad,
    }));
    return { lineas, subtotal: lineas.reduce((suma, linea) => suma + linea.subtotal, 0) };
  }

  async ver(carritoId: string): Promise<Carrito | null> {
    return this.carrito && this.carrito.id === carritoId ? this.carrito : null;
  }

  async crear(): Promise<Carrito> {
    throw new Error('no usado en esta prueba');
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

class RepositorioEnviosFalso implements RepositorioEnvios {
  constructor(private readonly respuesta: ResultadoCotizacion) {}

  async cotizar(): Promise<ResultadoCotizacion> {
    return this.respuesta;
  }

  async modalidades(): Promise<ModalidadesDeEntrega> {
    return { envioADomicilio: true, retiroEnPunto: false };
  }
}

const COTIZACION: CotizacionEnvio = {
  costoEnvio: 9_540,
  moneda: 'COP',
  transportadora: '99 minutes',
  diasEstimados: 2,
  venceEn: '2026-10-09T12:00:00Z',
  opciones: [
    { transportadora: '99 minutes', costoEnvio: 9_540, moneda: 'COP', diasEstimados: 2 },
    { transportadora: 'Servientrega', costoEnvio: 12_300, moneda: 'COP', diasEstimados: 1 },
  ],
};

const CARRITO_CON_LINEAS: Carrito = {
  id: 'carrito-1',
  usuarioId: null,
  creadoEn: '2026-01-01T00:00:00Z',
  lineas: [{ id: 'linea-1', varianteId: 'variante-1', cantidad: 1 }],
};

const A_DOMICILIO: DatosEntrega = {
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

/** Los datos se siembran antes de que la página exista: su guardia corre al construirse. */
let datosSembrados: DatosEntrega | null = A_DOMICILIO;

@Component({
  selector: 'app-anfitrion-de-prueba',
  imports: [TransportadoraPage],
  template: `<app-transportadora />`,
})
class AnfitrionDePrueba {
  constructor() {
    if (datosSembrados) {
      inject(CheckoutStore).guardarDatosEntrega(datosSembrados);
    }
  }
}

@Component({ selector: 'app-ruta-muda', template: '' })
class RutaMuda {}

/** Solo lo que esta página usa: la precarga de los métodos de pago. */
class PedidosQueCuentan {
  readonly consultas: MetodosDePagoDisponiblesComando[] = [];

  async metodosDePagoDisponibles(comando: MetodosDePagoDisponiblesComando): Promise<MetodoPago[]> {
    this.consultas.push(comando);
    return ['WOMPI'];
  }
}

async function renderPagina(
  envios: RepositorioEnvios = new RepositorioEnviosFalso({
    tipo: 'TARIFA',
    cotizacion: COTIZACION,
  }),
  pedidos: PedidosQueCuentan = new PedidosQueCuentan(),
) {
  return render(AnfitrionDePrueba, {
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
        { path: 'resumen', component: RutaMuda },
        { path: 'metodo-pago', component: RutaMuda },
      ]),
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_CARRITO, useValue: new RepositorioCarritoFalso(CARRITO_CON_LINEAS) },
      { provide: REPOSITORIO_ENVIOS, useValue: envios },
      { provide: REPOSITORIO_PEDIDOS, useValue: pedidos },
      // `CheckoutStore` lo pide al construirse; esta página no cobra.
      { provide: REPOSITORIO_PAGOS, useValue: {} },
    ],
  });
}

describe('TransportadoraPage (ADR-0073)', () => {
  beforeEach(() => {
    window.localStorage.clear();
    sembrarCarritoId('carrito-1');
    datosSembrados = A_DOMICILIO;
  });

  /**
   * Cada opción es un botón con el nombre, y el costo va debajo y lo describe: el lector lo anuncia
   * con la opción. Elegir guarda el nombre y nunca el costo.
   */
  it('pinta una baldosa por transportadora con su costo, y elegir guarda el nombre', async () => {
    const { fixture } = await renderPagina();
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    const servientrega = await screen.findByRole('button', { name: 'Servientrega' });
    const precio = document.getElementById(servientrega.getAttribute('aria-describedby')!);
    expect(precio?.textContent).toMatch(/12\.300/);
    expect(servientrega.getAttribute('aria-pressed')).toBe('false');

    fireEvent.click(servientrega);

    expect(checkout.datosEntrega()?.transportadora).toBe('Servientrega');
    await vi.waitFor(() => expect(servientrega.getAttribute('aria-pressed')).toBe('true'));
    expect(screen.getByRole('button', { name: '99 minutes' }).getAttribute('aria-pressed')).toBe(
      'false',
    );
  });

  /**
   * ADR-0021, punto 5: elegir ya pide los métodos de pago de la página siguiente, con la
   * transportadora elegida y la misma forma que usa esa página, para que ella encuentre la respuesta
   * en la caché o se sume a la petición en vuelo.
   */
  it('elegir precarga los métodos de pago con la elegida', async () => {
    const pedidos = new PedidosQueCuentan();
    await renderPagina(undefined, pedidos);

    fireEvent.click(await screen.findByRole('button', { name: 'Servientrega' }));

    await vi.waitFor(() => expect(pedidos.consultas).toHaveLength(1));
    expect(pedidos.consultas[0]).toEqual({
      correo: 'compra@ejemplo.co',
      lineas: [{ varianteId: 'variante-1', cantidad: 1 }],
      tipoEntrega: 'ENVIO_A_DOMICILIO',
      direccion: A_DOMICILIO.direccion,
      transportadora: 'Servientrega',
      telefono: '3138816711',
    });
  });

  it('continuar sin elegir lo dice y no sigue', async () => {
    const { fixture } = await renderPagina();
    const router = fixture.debugElement.injector.get(Router);
    await screen.findByRole('button', { name: 'Servientrega' });

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('Elige una transportadora para continuar.')).toBeTruthy();
    expect(router.url).not.toBe('/metodo-pago');
  });

  it('con una elegida, continuar lleva al método de pago', async () => {
    const { fixture } = await renderPagina();
    const router = fixture.debugElement.injector.get(Router);
    fireEvent.click(await screen.findByRole('button', { name: '99 minutes' }));

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    await vi.waitFor(() => expect(router.url).toBe('/metodo-pago'));
  });

  /**
   * La que se eligió antes y ya no cotiza no deja seguir: llegaría marcada a la confirmación y el
   * servidor respondería 409 tres pantallas después.
   */
  it('una elegida que ya no cotiza no deja continuar', async () => {
    datosSembrados = { ...A_DOMICILIO, transportadora: 'Coordinadora' };
    const { fixture } = await renderPagina();
    const router = fixture.debugElement.injector.get(Router);
    await screen.findByRole('button', { name: 'Servientrega' });

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('Elige una transportadora para continuar.')).toBeTruthy();
    expect(router.url).not.toBe('/metodo-pago');
  });

  it('si ya nadie cotiza el envío, lo dice y no ofrece continuar', async () => {
    await renderPagina(new RepositorioEnviosFalso({ tipo: 'SIN_COBERTURA' }));

    expect(
      await screen.findByText(
        'Ninguna transportadora cotiza este envío ahora. Vuelve al resumen y revisa la dirección.',
      ),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Continuar' })).toBeNull();
  });

  it.each<[string, DatosEntrega | null]>([
    ['sin datos de entrega', null],
    [
      'con recogida en el punto',
      { ...A_DOMICILIO, tipoEntrega: 'RETIRO_EN_PUNTO', direccion: null },
    ],
  ])('%s, vuelve al resumen', async (_caso, datos) => {
    datosSembrados = datos;
    // El guardia corre en el primer tick: se espía el prototipo, antes de que exista el router.
    const navegar = vi.spyOn(Router.prototype, 'navigate');

    await renderPagina();

    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith(['../resumen'], expect.anything()));
    navegar.mockRestore();
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina();
    await screen.findByRole('button', { name: 'Servientrega' });

    await esperarSinViolaciones(container);
  });
});
