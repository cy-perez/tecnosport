import { ActivatedRouteSnapshot, Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { precargarScopeI18n } from '../../core/i18n/precargar-scope';
import { precargarFichaProducto } from './application/buscar-ficha-producto.consulta';
import { precargarProductos } from './application/buscar-productos.consulta';
import { precargarOpcionesFiltro } from './application/listar-opciones-filtro.consulta';
import { FILTRO_NOVEDADES } from './domain/filtro-productos.model';
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
        path: '',
        pathMatch: 'full',
        resolve: {
          _precarga: () =>
            Promise.all([precargarProductos(FILTRO_NOVEDADES), precargarScopeI18n('catalogo')]),
        },
        loadComponent: () =>
          import('./presentation/portada/portada.page').then((m) => m.PortadaPage),
      },
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
                  // El scope de i18n va aquí por la misma razón que las
                  // consultas: si llega después del primer render, toda
                  // etiqueta que no pase por el pipe sale en blanco o con la
                  // clave cruda.
                  precargarScopeI18n('catalogo'),
                ]),
            },
            loadComponent: () =>
              import('./presentation/rejilla/rejilla.page').then((m) => m.RejillaPage),
          },
          {
            path: ':slug',
            // provideTranslocoScope('carrito') aquí, no arriba con el resto de catalogo: la
            // ficha es la única página de catalogo que usa el botón "agregar al carrito".
            providers: [provideTranslocoScope('carrito')],
            resolve: {
              _precarga: (route: ActivatedRouteSnapshot) =>
                Promise.all([
                  precargarFichaProducto(route.paramMap.get('slug') ?? ''),
                  precargarScopeI18n('catalogo'),
                  precargarScopeI18n('carrito'),
                ]),
            },
            loadComponent: () => import('./presentation/ficha/ficha.page').then((m) => m.FichaPage),
          },
        ],
      },
    ],
  },
];
