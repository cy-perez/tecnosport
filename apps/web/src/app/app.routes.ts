import { Routes } from '@angular/router';
import { idiomaGuard } from './core/idioma/idioma.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'es' },
  {
    path: ':lang',
    canActivate: [idiomaGuard],
    children: [
      // La raíz de cada idioma es la portada, servida por catalogo.routes.ts
      // (reutiliza sus puertos y su scope de i18n).
      {
        path: '',
        loadChildren: () => import('./features/catalogo/catalogo.routes').then((m) => m.catalogoRoutes),
      },
      {
        path: 'carrito',
        loadChildren: () => import('./features/carrito/presentation/carrito.routes').then((m) => m.carritoRoutes),
      },
      {
        path: 'checkout',
        loadChildren: () => import('./features/checkout/presentation/checkout.routes').then((m) => m.checkoutRoutes),
      },
      {
        path: 'admin',
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.adminRoutes),
      },
      {
        path: 'cuenta',
        loadChildren: () => import('./features/cuenta/presentation/cuenta.routes').then((m) => m.cuentaRoutes),
      },
    ],
  },
  { path: '**', redirectTo: 'es' },
];
