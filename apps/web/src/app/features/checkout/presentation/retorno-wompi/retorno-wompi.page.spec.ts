import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Component } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { IntentoDePago } from '../../domain/intento-pago.model';
import { MetodoPago, Pedido } from '../../domain/pedido.model';
import { REPOSITORIO_PAGOS, RepositorioPagos } from '../../domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../../domain/repositorio-pedidos.puerto';
import { RetornoWompiPage } from './retorno-wompi.page';

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

class RepositorioPagosFalso implements RepositorioPagos {
  llamadas: { referencia: string; idTransaccionWompi: string }[] = [];
  fallar = false;

  async crearIntento(): Promise<IntentoDePago> {
    throw new Error('no usado en esta prueba');
  }

  async registrarIdTransaccion(referencia: string, idTransaccionWompi: string): Promise<void> {
    this.llamadas.push({ referencia, idTransaccionWompi });
    if (this.fallar) {
      throw new Error('el servidor rechazó el registro');
    }
  }
}

@Component({ selector: 'app-ruta-muda', template: '' })
class RutaMuda {}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function renderConQuery(query: Record<string, string>, pagos: RepositorioPagos) {
  return render(RetornoWompiPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'checkout/es': esCheckout } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([{ path: 'estado', component: RutaMuda }]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_PEDIDOS, useValue: new RepositorioPedidosFalso() },
      { provide: REPOSITORIO_PAGOS, useValue: pagos },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
      },
    ],
  });
}

describe('RetornoWompiPage', () => {
  it('sin id, referencia o pedidoId en la URL, muestra el error y no llama al servidor', async () => {
    const pagos = new RepositorioPagosFalso();

    await renderConQuery({}, pagos);
    await esperar(20);

    expect(screen.getByText("No encontramos los datos de este pago.")).toBeTruthy();
    expect(pagos.llamadas).toEqual([]);
  });

  it('con los tres parámetros, registra el id de transacción y navega al estado del pedido', async () => {
    const pagos = new RepositorioPagosFalso();
    // La navegación sale de una cadena de promesas arrancada en el
    // constructor, no de un effect() de Angular — puede resolver dentro de
    // los propios `await` internos de `render()`. Se espía el prototipo
    // desde antes, mismo motivo que en `metodo-pago.page.spec.ts`.
    const navegar = vi.spyOn(Router.prototype, 'navigate');

    await renderConQuery(
      { id: '01-1531231271-19365', referencia: 'TS-2026-000001-1', pedidoId: 'pedido-1', correo: 'cliente@tecnosport.co' },
      pagos,
    );
    await esperar(20);

    expect(pagos.llamadas).toEqual([{ referencia: 'TS-2026-000001-1', idTransaccionWompi: '01-1531231271-19365' }]);
    expect(navegar).toHaveBeenCalledWith(
      ['../estado'],
      expect.objectContaining({ queryParams: { pedidoId: 'pedido-1', correo: 'cliente@tecnosport.co' } }),
    );
    navegar.mockRestore();
  });

  it('si registrar el id falla, igual navega al estado del pedido (best-effort)', async () => {
    const pagos = new RepositorioPagosFalso();
    pagos.fallar = true;
    const navegar = vi.spyOn(Router.prototype, 'navigate');

    await renderConQuery(
      { id: '01-1531231271-19365', referencia: 'TS-2026-000001-1', pedidoId: 'pedido-1', correo: 'cliente@tecnosport.co' },
      pagos,
    );
    await esperar(20);

    expect(navegar).toHaveBeenCalledWith(
      ['../estado'],
      expect.objectContaining({ queryParams: { pedidoId: 'pedido-1', correo: 'cliente@tecnosport.co' } }),
    );
    navegar.mockRestore();
  });
});
