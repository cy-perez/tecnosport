import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCheckout from '../../../../../assets/i18n/scopes/checkout/es.json';
import { RetornoSistecreditoPage } from './retorno-sistecredito.page';
import {
  ALMACEN_CORREO_DE_PEDIDO,
  AlmacenCorreoDePedido,
} from '../../domain/almacen-correo-de-pedido.puerto';

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

@Component({ selector: 'app-ruta-muda', template: '' })
class RutaMuda {}

const RUTA_RETORNO = 'sistecredito/retorno/:pedidoId';
/** La de antes, para los pagos que salieron cuando el backend todavía ponía el correo. */
const RUTA_RETORNO_VIEJA = 'sistecredito/retorno/:pedidoId/:correo';

let almacen = almacenDesde();

/**
 * <b>El árbol de rutas de verdad, y no una ruta suelta.</b> La prueba anterior espiaba
 * `Router.navigate` y afirmaba el argumento —`['../../../estado']`—, que es copiar la
 * implementación: pasaba en verde mientras todo comprador que pagaba con Sistecrédito aterrizaba
 * en la portada. Lo que hace falta es el prefijo de idioma, el segmento `checkout` y una ruta de
 * retorno con sus cuatro segmentos, porque es de contarlos mal de donde salía el defecto. Aquí se
 * navega de verdad y se mira dónde termina el navegador.
 */
async function navegarAlRetorno(url: string) {
  almacen = almacenDesde();
  TestBed.configureTestingModule({
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'checkout/es': esCheckout } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      { provide: ALMACEN_CORREO_DE_PEDIDO, useValue: almacen },
      provideRouter([
        {
          path: ':idioma',
          children: [
            {
              path: 'checkout',
              // El envoltorio sin segmento que `checkout.routes.ts` usa para sus proveedores y su
              // scope de i18n: está aquí porque cambia de quién es padre esta pantalla.
              children: [
                {
                  path: '',
                  children: [
                    { path: RUTA_RETORNO, component: RetornoSistecreditoPage },
                    { path: RUTA_RETORNO_VIEJA, component: RetornoSistecreditoPage },
                    { path: 'estado', component: RutaMuda },
                  ],
                },
              ],
            },
          ],
        },
        // La comodín del sitio, que es la que convertía el error en un aterrizaje silencioso en
        // la portada en vez de en un 404 visible.
        { path: '**', component: RutaMuda },
      ]),
    ],
  });

  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url);
  return TestBed.inject(Router);
}

describe('RetornoSistecreditoPage', () => {
  /**
   * <b>La prueba que cubre a todo comprador.</b> La pantalla de estado exige `pedidoId` y
   * `correo` para consultar el seguimiento y no tiene forma de pedirlos: sin ellos enseña "No
   * encontramos este pedido". Sistecrédito solo devuelve lo suyo, así que estos dos datos los
   * pone el backend en la propia URL de respuesta, como segmentos de ruta.
   *
   * <p>Se afirma la URL donde termina el navegador, no el argumento con el que se llamó a
   * `navigate`: el 23 de septiembre de 2026 se midió contra el despliegue de dev que esa
   * navegación subía un segmento de menos y dejaba al comprador en `/es`, con la prueba vieja en
   * verde.
   */
  it('lleva a la pantalla de estado con el pedido y sin el correo en la URL', async () => {
    const router = await navegarAlRetorno('/es/checkout/sistecredito/retorno/pedido-1');

    await vi.waitFor(() => {
      const destino = router.parseUrl(router.url);
      expect(destino.toString().split('?')[0]).toBe('/es/checkout/estado');
      expect(destino.queryParams).toEqual({ pedidoId: 'pedido-1' });
    });
  });

  /**
   * Un pago que salió antes de que el backend dejara de poner el correo vuelve por la ruta vieja.
   * Llega igual, el correo se recuerda en el navegador y no pasa a la URL de estado.
   */
  it('por la ruta vieja recuerda el correo y tampoco lo pasa a la URL', async () => {
    const router = await navegarAlRetorno(
      '/es/checkout/sistecredito/retorno/pedido-1/cliente%40tecnosport.co',
    );

    await vi.waitFor(() => {
      const destino = router.parseUrl(router.url);
      expect(destino.toString().split('?')[0]).toBe('/es/checkout/estado');
      expect(destino.queryParams).toEqual({ pedidoId: 'pedido-1' });
    });
    expect(almacen.correoDe('pedido-1')).toBe('cliente@tecnosport.co');
  });

  /** El idioma del comprador sobrevive el viaje: la pantalla de estado vive bajo su prefijo. */
  it('vuelve a la pantalla de estado del idioma en el que se compró', async () => {
    const router = await navegarAlRetorno(
      '/en/checkout/sistecredito/retorno/pedido-1/cliente%40tecnosport.co',
    );

    await vi.waitFor(() => {
      expect(router.parseUrl(router.url).toString().split('?')[0]).toBe('/en/checkout/estado');
    });
  });

  /**
   * Los parámetros que sí devuelve la pasarela —`paymentRef`, `transactionId`, `orderId`— no
   * hacen falta para nada: no deciden un pago, y el backend ya guardó el id de la transacción al
   * crear el intento. Que vengan o falten no puede impedir que el comprador vea su pedido.
   */
  it('no necesita los parámetros que concatena la pasarela', async () => {
    const router = await navegarAlRetorno(
      '/es/checkout/sistecredito/retorno/pedido-1/cliente%40tecnosport.co?paymentRef=6ab40fd77705',
    );

    await vi.waitFor(() => {
      expect(router.parseUrl(router.url).toString().split('?')[0]).toBe('/es/checkout/estado');
    });
    expect(screen.queryByText('No pudimos identificar tu compra')).toBeFalsy();
  });

  /**
   * Esta sí con la ruta fingida, y a propósito: una URL sin los dos segmentos **no casa** con
   * `sistecredito/retorno/:pedidoId/:correo`, así que por el árbol real no hay forma de llegar
   * aquí. La rama existe para el día que la URL de respuesta se genere mal en el backend, y se
   * prueba con el único instrumento que la alcanza.
   */
  it('sin pedido ni correo en la ruta, lo dice en vez de navegar a ciegas', async () => {
    const navegar = vi.spyOn(Router.prototype, 'navigate');

    await render(RetornoSistecreditoPage, {
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { es, en, 'checkout/es': esCheckout } as never,
          translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
          preloadLangs: true,
        }),
      ],
      providers: [
        { provide: ALMACEN_CORREO_DE_PEDIDO, useValue: almacenDesde() },
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({}) }, parent: null },
        },
      ],
    });

    expect(await screen.findByText('No pudimos identificar tu compra')).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
    navegar.mockRestore();
  });
});
