import { Component } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { RetornoSistecreditoPage } from './retorno-sistecredito.page';

@Component({ selector: 'app-ruta-muda', template: '' })
class RutaMuda {}

async function renderConRuta(params: Record<string, string>) {
  return render(RetornoSistecreditoPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'checkout/es': esCheckout } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([{ path: 'estado', component: RutaMuda }]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap(params) } },
      },
    ],
  });
}

describe('RetornoSistecreditoPage', () => {
  /**
   * <b>La prueba que faltaba y que cubre a todo comprador.</b> La pantalla de estado exige
   * `pedidoId` y `correo` para consultar el seguimiento y no tiene forma de pedirlos: sin ellos
   * enseña "No encontramos este pedido". Sistecrédito solo devuelve lo suyo, así que estos dos
   * datos los pone el backend en la propia URL de respuesta, como segmentos de ruta.
   *
   * <p>Sin esto, cada persona que pagara con Sistecrédito aterrizaba en una pantalla de error
   * roja justo después de haber pedido su crédito.
   */
  it('lleva a la pantalla de estado con el pedido y el correo que trae la ruta', async () => {
    // La navegación sale del constructor, no de un effect(): se espía el prototipo desde antes,
    // mismo motivo que en `retorno-wompi.page.spec.ts`.
    const navegar = vi.spyOn(Router.prototype, 'navigate');

    await renderConRuta({ pedidoId: 'pedido-1', correo: 'cliente@tecnosport.co' });

    await vi.waitFor(() => {
      expect(navegar).toHaveBeenCalledWith(
        ['../../../estado'],
        expect.objectContaining({
          queryParams: { pedidoId: 'pedido-1', correo: 'cliente@tecnosport.co' },
        }),
      );
    });
    navegar.mockRestore();
  });

  /**
   * Los parámetros que sí devuelve la pasarela —`paymentRef`, `transactionId`, `orderId`— no
   * hacen falta para nada: no deciden un pago, y el backend ya guardó el id de la transacción al
   * crear el intento. Que falten no puede impedir que el comprador vea su pedido.
   */
  it('no necesita los parámetros que concatena la pasarela', async () => {
    const navegar = vi.spyOn(Router.prototype, 'navigate');

    await renderConRuta({ pedidoId: 'pedido-1', correo: 'cliente@tecnosport.co' });

    await vi.waitFor(() => expect(navegar).toHaveBeenCalled());
    expect(screen.queryByText('No pudimos identificar tu compra')).toBeFalsy();
    navegar.mockRestore();
  });

  it('sin pedido ni correo en la ruta, lo dice en vez de navegar a ciegas', async () => {
    const navegar = vi.spyOn(Router.prototype, 'navigate');

    await renderConRuta({});

    expect(await screen.findByText('No pudimos identificar tu compra')).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
    navegar.mockRestore();
  });
});
