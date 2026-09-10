import { Component, inject } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { esperarSinViolaciones } from '../../../../../testing/axe';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { Carrito } from '../../../carrito/domain/carrito.model';
import { REPOSITORIO_CARRITO, RepositorioCarrito } from '../../../carrito/domain/repositorio-carrito.puerto';
import { CheckoutStore } from '../../application/checkout.store';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { DatosEntrega } from '../../domain/pedido.comandos';
import { MetodoPago, Pedido, Seguimiento } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import { MetodoPagoPage } from './metodo-pago.page';
import { proveerAlmacenesCarrito, sembrarCarritoId } from '../../../../../testing/carrito';

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
  constructor(private disponibles: MetodoPago[] = ['TARJETA', 'CONTRAENTREGA']) {}

  async crear(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async metodosDePagoDisponibles(): Promise<MetodoPago[]> {
    return this.disponibles;
  }

  async reintentarPago(): Promise<Pedido> {
    throw new Error('no usado en esta prueba');
  }

  async consultarSeguimiento(): Promise<Seguimiento | null> {
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
  autorizaDatos: true,
};

/** Ya con `CheckoutStore.datosEntrega` poblado antes de que `MetodoPagoPage`
 * se construya — su constructor corre el guardia de redirección de
 * inmediato, así que hay que sembrar el dato antes de que exista la página,
 * no después. El constructor del anfitrión corre primero que el de su hijo
 * en el mismo ciclo de creación. */
@Component({ selector: 'app-anfitrion-de-prueba', imports: [MetodoPagoPage], template: `<app-metodo-pago />` })
class AnfitrionDePrueba {
  private readonly checkout = inject(CheckoutStore);

  constructor() {
    this.checkout.guardarDatosEntrega(DATOS_ENTREGA);
  }
}

@Component({ selector: 'app-ruta-muda', template: '' })
class RutaMuda {}


async function renderConDatosEntrega(carrito: RepositorioCarrito, pedidos: RepositorioPedidos) {
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
        { path: 'confirmar', component: RutaMuda },
      ]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_CARRITO, useValue: carrito },
      { provide: REPOSITORIO_PEDIDOS, useValue: pedidos },
      { provide: REPOSITORIO_PAGOS, useValue: new RepositorioPagosFalso() },
    ],
  });
}

describe('MetodoPagoPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('sin datos de entrega guardados, vuelve al resumen', async () => {
    // El efecto del guardia corre en el primer tick, antes de que dé tiempo a
    // espiar una instancia ya creada (`fixture.debugElement...get(Router)`
    // resuelve tarde): se espía el prototipo, así el espía existe desde
    // antes de que `render` construya el router real.
    const navegar = vi.spyOn(Router.prototype, 'navigate');

    await render(MetodoPagoPage, {
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { es, en, 'checkout/es': esCheckout } as never,
          translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
          preloadLangs: true,
        }),
      ],
      providers: [
      ...proveerAlmacenesCarrito(),
        provideRouter([{ path: 'resumen', component: RutaMuda }]),
        provideTanStackQuery(new QueryClient()),
        { provide: REPOSITORIO_CARRITO, useValue: new RepositorioCarritoFalso(null) },
        { provide: REPOSITORIO_PEDIDOS, useValue: new RepositorioPedidosFalso() },
        { provide: REPOSITORIO_PAGOS, useValue: new RepositorioPagosFalso() },
      ],
    });
    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith(['../resumen'], expect.anything()));
    navegar.mockRestore();
  });

  it('con datos de entrega y carrito, muestra los métodos disponibles', async () => {
    sembrarCarritoId('carrito-1');

    await renderConDatosEntrega(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(['TARJETA', 'CONTRAENTREGA']),
    );

    expect(await screen.findByRole('button', { name: 'Tarjeta de crédito o débito' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Pago contra entrega' })).toBeTruthy();
  });

  it('el botón continuar arranca deshabilitado hasta elegir un método', async () => {
    sembrarCarritoId('carrito-1');

    const { fixture } = await renderConDatosEntrega(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(['TARJETA', 'CONTRAENTREGA']),
    );
    await screen.findByRole('button', { name: 'Tarjeta de crédito o débito' });
    const checkout = fixture.debugElement.injector.get(CheckoutStore);

    expect(screen.getByRole('button', { name: 'Continuar' }).hasAttribute('disabled')).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Pago contra entrega' }));

    expect(screen.getByRole('button', { name: 'Continuar' }).hasAttribute('disabled')).toBe(false);
    expect(checkout.metodoPago()).toBe('CONTRAENTREGA');
  });

  // Los métodos de pago son botones que se seleccionan: el estado elegido tiene que llegarle a un
  // lector de pantalla, no solo verse.
  it('no tiene violaciones de WCAG 2.2 AA', async () => {
    sembrarCarritoId('carrito-1');

    const { container } = await renderConDatosEntrega(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(['TARJETA', 'CONTRAENTREGA']),
    );
    await screen.findByRole('button', { name: 'Tarjeta de crédito o débito' });

    await esperarSinViolaciones(container);
  });
});
