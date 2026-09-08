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
import { MetodoPago, Pedido } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
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

  async consultarSeguimiento(): Promise<Pedido | null> {
    throw new Error('no usado en esta prueba');
  }
}

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

async function renderResumen(carrito: RepositorioCarrito) {
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
    ],
  });
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
