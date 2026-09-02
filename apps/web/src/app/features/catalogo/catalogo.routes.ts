import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { precargarProductos } from './application/buscar-productos.consulta';
import { REPOSITORIO_PRODUCTOS } from './domain/repositorio-productos.puerto';
import { ProductosHttpRepositorio } from './infrastructure/productos-http.repositorio';

// El binding puerto -> implementación vive aquí, no en presentation/: "el
// proveedor de la ruta decide la implementación" (apps/web/CLAUDE.md). Este
// archivo no cae bajo ningún patrón de capa del ESLint de límites, es el
// punto de composición de la funcionalidad — igual que bootstrap en el backend.
export const catalogoRoutes: Routes = [
  {
    path: '',
    providers: [
      { provide: REPOSITORIO_PRODUCTOS, useClass: ProductosHttpRepositorio },
      provideTranslocoScope('catalogo'),
    ],
    children: [
      {
        path: 'productos',
        // resolve, no solo loadComponent: calienta la caché de TanStack Query
        // antes de crear el componente, para que el SSR sea determinista.
        resolve: { _precarga: () => precargarProductos() },
        loadComponent: () => import('./presentation/rejilla/rejilla.page').then((m) => m.RejillaPage),
      },
    ],
  },
];
