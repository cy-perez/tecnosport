import { provideHttpClient, withFetch } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideBrowserGlobalErrorListeners,
  provideEnvironmentInitializer,
} from '@angular/core';
import { provideClientHydration } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideTransloco } from '@jsverse/transloco';
import { routes } from './app.routes';
import { REPOSITORIO_SESION } from './core/autenticacion/repositorio-sesion.puerto';
import { SesionHttpRepositorio } from './core/autenticacion/sesion-http.repositorio';
import { ALMACEN_CARRITO_ID } from './features/carrito/domain/almacen-carrito-id.puerto';
import { ALMACEN_SNAPSHOT_LINEAS } from './features/carrito/domain/almacen-snapshot-lineas.puerto';
import { REPOSITORIO_CARRITO } from './features/carrito/domain/repositorio-carrito.puerto';
import { CarritoIdLocalStorageAlmacen } from './features/carrito/infrastructure/carrito-id.almacen';
import { SnapshotLineasLocalStorageAlmacen } from './features/carrito/infrastructure/snapshot-lineas.almacen';
import { CarritoHttpRepositorio } from './features/carrito/infrastructure/carrito-http.repositorio';
import { REPOSITORIO_PAGOS } from './features/checkout/domain/repositorio-pagos.puerto';
import { REPOSITORIO_PEDIDOS } from './features/checkout/domain/repositorio-pedidos.puerto';
import { PagoHttpRepositorio } from './features/checkout/infrastructure/pago-http.repositorio';
import { PedidoHttpRepositorio } from './features/checkout/infrastructure/pedido-http.repositorio';
import { MetadatosSeo } from './core/seo/metadatos.servicio';
import { TranslocoHttpLoader } from './transloco-loader';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Un inicializador de entorno y no el constructor de `App`: corre antes de
    // la primera navegación, y suscribirse después de ella dejaría sin
    // metadatos justo a la pantalla que sirve el SSR — la única que ve un
    // rastreador que no ejecuta JavaScript.
    provideEnvironmentInitializer(() => inject(MetadatosSeo).escuchar()),
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
    // Los dos almacenes van aquí y no en carrito.routes.ts por el mismo motivo: los usa
    // CarritoStore, que es `providedIn: 'root'`, y el encabezado lo consulta en todas las
    // pantallas. Darle al InjectionToken una `factory` por defecto sería más corto y volvería
    // a invertir la dependencia, ahora escondida dentro de `domain`.
    { provide: ALMACEN_CARRITO_ID, useClass: CarritoIdLocalStorageAlmacen },
    { provide: ALMACEN_SNAPSHOT_LINEAS, useClass: SnapshotLineasLocalStorageAlmacen },
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
