import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { precargarScopeI18n } from '../../../core/i18n/precargar-scope';

// Sin precarga de datos, a propósito: el id del carrito vive en localStorage, y el servidor
// nunca sabe cuál es "el de este visitante" — no hay nada que precargar en SSR (ver
// application/carrito.store.ts y apps/web/CLAUDE.md). REPOSITORIO_CARRITO se provee en
// app.config.ts, no aquí: el encabezado (fuera de esta ruta) también lo necesita.
//
// Los textos sí se precargan. Sin `resolve`, el servidor serializaba esta página antes de que
// llegara el scope, y `/es/carrito` salía sin "Carrito" ni "Tu carrito está vacío".
export const carritoRoutes: Routes = [
  {
    path: '',
    providers: [provideTranslocoScope('carrito')],
    data: { seo: { clave: 'seo.carrito' } },
    resolve: { _i18n: () => precargarScopeI18n('carrito') },
    loadComponent: () => import('./carrito.page').then((m) => m.CarritoPage),
  },
];
