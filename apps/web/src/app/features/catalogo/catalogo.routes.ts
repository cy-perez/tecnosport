import { ActivatedRouteSnapshot, Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { precargarFichaProducto } from './application/buscar-ficha-producto.consulta';
import { precargarProductos } from './application/buscar-productos.consulta';
import { precargarOpcionesFiltro } from './application/listar-opciones-filtro.consulta';
import { filtroDesdeQueryParams } from './domain/query-params-filtro';
import { REPOSITORIO_CATEGORIAS } from './domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS } from './domain/repositorio-marcas.puerto';
import { REPOSITORIO_PRODUCTOS } from './domain/repositorio-productos.puerto';
import { CategoriasHttpRepositorio } from './infrastructure/categorias-http.repositorio';
import { MarcasHttpRepositorio } from './infrastructure/marcas-http.repositorio';
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
      { provide: REPOSITORIO_CATEGORIAS, useClass: CategoriasHttpRepositorio },
      { provide: REPOSITORIO_MARCAS, useClass: MarcasHttpRepositorio },
      provideTranslocoScope('catalogo'),
    ],
    children: [
      {
        path: 'productos',
        children: [
          {
            path: '',
            // resolve, no solo loadComponent: calienta la caché de TanStack
            // Query antes de crear el componente, para que el SSR sea
            // determinista — con los mismos query params que va a leer la
            // página, si no, vuelve el bug de skeletons pero solo para URLs
            // con filtros.
            resolve: {
              _precarga: (route: ActivatedRouteSnapshot) =>
                Promise.all([
                  precargarProductos(filtroDesdeQueryParams(route.queryParams)),
                  precargarOpcionesFiltro(),
                ]),
            },
            loadComponent: () => import('./presentation/rejilla/rejilla.page').then((m) => m.RejillaPage),
          },
          {
            path: ':slug',
            resolve: {
              _precarga: (route: ActivatedRouteSnapshot) =>
                precargarFichaProducto(route.paramMap.get('slug') ?? ''),
            },
            loadComponent: () => import('./presentation/ficha/ficha.page').then((m) => m.FichaPage),
          },
        ],
      },
    ],
  },
];
