import { mergeApplicationConfig, ApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { TRANSLOCO_LOADER } from '@jsverse/transloco';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { TranslocoDiscoLoader } from './transloco-loader.server';

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    // Pisa el cargador de `app.config.ts` solo en el servidor: los mismos archivos, leídos del
    // disco en vez de pedidos por HTTP a sí mismo. Ver `transloco-loader.server.ts`.
    { provide: TRANSLOCO_LOADER, useClass: TranslocoDiscoLoader },
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
