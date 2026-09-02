import { Routes } from '@angular/router';
import { idiomaGuard } from './core/idioma/idioma.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'es' },
  {
    path: ':lang',
    canActivate: [idiomaGuard],
    children: [
      // Sin portada todavía: la raíz de cada idioma cae en el catálogo.
      { path: '', pathMatch: 'full', redirectTo: 'productos' },
      {
        path: '',
        loadChildren: () => import('./features/catalogo/catalogo.routes').then((m) => m.catalogoRoutes),
      },
    ],
  },
  { path: '**', redirectTo: 'es' },
];
