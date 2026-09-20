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
import {
  REPOSITORIO_CARRITO,
  RepositorioCarrito,
} from '../../../carrito/domain/repositorio-carrito.puerto';
import { CheckoutStore } from '../../application/checkout.store';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { IntentoSistecredito } from '../../domain/intento-sistecredito.model';
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

  async crearIntentoSistecredito(): Promise<IntentoSistecredito> {
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
  contacto: { nombre: 'Ana Pérez', telefono: '3138816711' },
  autorizaDatos: true,
};

/** Ya con `CheckoutStore.datosEntrega` poblado antes de que `MetodoPagoPage`
 * se construya — su constructor corre el guardia de redirección de
 * inmediato, así que hay que sembrar el dato antes de que exista la página,
 * no después. El constructor del anfitrión corre primero que el de su hijo
 * en el mismo ciclo de creación. */
@Component({
  selector: 'app-anfitrion-de-prueba',
  imports: [MetodoPagoPage],
  template: `<app-metodo-pago />`,
})
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

  /**
   * El aviso se muestra cuando la opción se ofrece, no cuando se elige: informar es previo a
   * decidir. Dice las dos cosas que sorprenden al recibir el paquete — que se cobra el total con
   * el envío incluido, y que la transportadora solo recibe efectivo (docs/12-legales-de-envio.md).
   */
  it('cuando se ofrece contraentrega, avisa que se cobra el total y solo en efectivo', async () => {
    sembrarCarritoId('carrito-1');

    await renderConDatosEntrega(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(['TARJETA', 'CONTRAENTREGA']),
    );

    expect(
      await screen.findByText('Contra entrega: pagas el total, envío incluido, y solo en efectivo'),
    ).toBeTruthy();
  });

  /** Sin la opción no hay nada que advertir, y un aviso que no aplica es ruido. */
  it('sin contraentrega entre las opciones, el aviso no aparece', async () => {
    sembrarCarritoId('carrito-1');

    await renderConDatosEntrega(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(['TARJETA', 'PSE']),
    );

    await screen.findByRole('button', { name: 'Tarjeta de crédito o débito' });
    expect(screen.queryByText(/solo en efectivo/)).toBeFalsy();
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

  /**
   * El documento se pide aquí y no en la pantalla siguiente: es parte de elegir este método, y
   * quien lo elige tiene que saber antes de seguir que le van a pedir su cédula y por qué.
   */
  it('al elegir Sistecrédito aparece el campo del documento, con su explicación', async () => {
    sembrarCarritoId('carrito-1');

    await renderConDatosEntrega(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(['TARJETA', 'SISTECREDITO']),
    );
    await screen.findByRole('button', { name: 'Tarjeta de crédito o débito' });

    expect(screen.queryByLabelText('Número de documento')).toBeFalsy();

    fireEvent.click(screen.getByRole('button', { name: 'Sistecrédito (paga a cuotas)' }));

    expect(await screen.findByLabelText('Número de documento')).toBeTruthy();
    expect(screen.getByLabelText('Tipo de documento')).toBeTruthy();
    expect(screen.getByText(/no lo guardamos/)).toBeTruthy();
  });

  /**
   * El botón no se deshabilita: un botón apagado no dice qué le falta. Pulsarlo marca el campo y
   * enseña el mensaje, que es lo que el comprador necesita para arreglarlo.
   */
  it('sin un documento válido no se continúa, y el error lo dice', async () => {
    sembrarCarritoId('carrito-1');

    const { fixture } = await renderConDatosEntrega(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(['SISTECREDITO']),
    );
    const checkout = fixture.debugElement.injector.get(CheckoutStore);
    fireEvent.click(await screen.findByRole('button', { name: 'Sistecrédito (paga a cuotas)' }));
    await screen.findByLabelText('Número de documento');

    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText(/Escribe tu número de documento/)).toBeTruthy();
    expect(checkout.documentoComprador()).toBeNull();
  });

  /** Con el documento puesto, se anota para que la pantalla de confirmar lo mande a la pasarela. */
  it('con un documento válido queda anotado en el store', async () => {
    sembrarCarritoId('carrito-1');

    const { fixture } = await renderConDatosEntrega(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(['SISTECREDITO']),
    );
    const checkout = fixture.debugElement.injector.get(CheckoutStore);
    fireEvent.click(await screen.findByRole('button', { name: 'Sistecrédito (paga a cuotas)' }));
    const campo = await screen.findByLabelText('Número de documento');

    fireEvent.input(campo, { target: { value: '1017254896' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(checkout.documentoComprador()).toEqual({
      tipoDocumento: 'CC',
      documento: '1017254896',
    });
  });

  /** Cambiar de método suelta el documento: es un dato personal que ya no hace falta. */
  it('elegir otro método borra el documento anotado', async () => {
    sembrarCarritoId('carrito-1');

    const { fixture } = await renderConDatosEntrega(
      new RepositorioCarritoFalso(CARRITO_CON_LINEAS),
      new RepositorioPedidosFalso(['TARJETA', 'SISTECREDITO']),
    );
    const checkout = fixture.debugElement.injector.get(CheckoutStore);
    checkout.anotarDocumentoComprador({ tipoDocumento: 'CC', documento: '1017254896' });

    fireEvent.click(await screen.findByRole('button', { name: 'Tarjeta de crédito o débito' }));

    expect(checkout.documentoComprador()).toBeNull();
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
