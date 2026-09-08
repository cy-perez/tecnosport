import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';

// Sin resolver de precarga, a propósito: el id del carrito vive en localStorage, y el servidor
// nunca sabe cuál es "el de este visitante" — no hay nada que precargar en SSR (ver
// application/carrito.store.ts y apps/web/CLAUDE.md). REPOSITORIO_CARRITO se provee en
// app.config.ts, no aquí: el encabezado (fuera de esta ruta) también lo necesita.
export const carritoRoutes: Routes = [
  {
    path: '',
    providers: [provideTranslocoScope('carrito')],
    data: { seo: { clave: 'seo.carrito' } },
    loadComponent: () => import('./carrito.page').then((m) => m.CarritoPage),
  },
];
