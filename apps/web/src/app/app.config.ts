import { provideHttpClient, withFetch } from '@angular/common/http';
import { ApplicationConfig, isDevMode, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideClientHydration } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideTransloco } from '@jsverse/transloco';
import { routes } from './app.routes';
import { REPOSITORIO_SESION } from './core/autenticacion/repositorio-sesion.puerto';
import { SesionHttpRepositorio } from './core/autenticacion/sesion-http.repositorio';
import { REPOSITORIO_CARRITO } from './features/carrito/domain/repositorio-carrito.puerto';
import { CarritoHttpRepositorio } from './features/carrito/infrastructure/carrito-http.repositorio';
import { REPOSITORIO_PAGOS } from './features/checkout/domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS } from './features/checkout/domain/repositorio-pedidos.puerto';
import { PagoHttpRepositorio } from './features/checkout/infrastructure/pago-http.repositorio';
import { PedidoHttpRepositorio } from './features/checkout/infrastructure/pedido-http.repositorio';
import { TranslocoHttpLoader } from './transloco-loader';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideClientHydration(),
    provideHttpClient(withFetch()),
    // Sin hidratación SSR del estado de la consulta todavía: la rejilla
    // funciona igual, solo repite el fetch una vez al hidratar. Optimización
    // pendiente que no toca componentes cuando se agregue.
    provideTanStackQuery(new QueryClient()),
    // A diferencia de los puertos de catalogo (provistos por catalogo.routes.ts, solo dentro de
    // esa ruta): el carrito lo necesita el encabezado, que se renderiza siempre, no solo dentro
    // de /carrito — ver application/carrito.store.ts.
    { provide: REPOSITORIO_CARRITO, useClass: CarritoHttpRepositorio },
    // Mismo criterio que REPOSITORIO_CARRITO: SesionStore es compartido
    // (`core/autenticacion/`, no atado a ninguna funcionalidad), lo va a
    // necesitar tanto el guardia de rutas de admin como, más adelante,
    // el login de cliente en features/cuenta.
    { provide: REPOSITORIO_SESION, useClass: SesionHttpRepositorio },
    // Mismo motivo, encontrado al recorrer las rutas contra `ng serve` real:
    // `CheckoutStore` es `providedIn: 'root'` (lo comparten resumen, retorno de
    // Wompi, transferencia y estado, sin relación padre-hijo), y un servicio de
    // raíz NO ve los proveedores de una ruta. Con estos dos puertos declarados
    // solo en `checkout.routes.ts`, cada pantalla del checkout moría con
    // NG0201 antes de pintar nada — el SSR respondía 404 y en el navegador la
    // navegación quedaba muerta. Un servicio de raíz exige puertos de raíz.
    { provide: REPOSITORIO_PEDIDOS, useClass: PedidoHttpRepositorio },
    { provide: REPOSITORIO_PAGOS, useClass: PagoHttpRepositorio },
    provideTransloco({
      config: {
        availableLangs: ['es', 'en'],
        defaultLang: 'es',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
