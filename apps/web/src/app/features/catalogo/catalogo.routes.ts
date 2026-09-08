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
        // La portada es la única pantalla cuyos metadatos viven en el paquete
        // raíz de i18n y no en el scope `catalogo`: es la que sirve `/es` y
        // `/en`, la primera que ve un rastreador, y su título no puede depender
        // de que un scope perezoso haya llegado a tiempo.
        data: { seo: { clave: 'seo.portada', indexable: true } },
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
            data: { seo: { clave: 'catalogo.seo.rejilla', indexable: true } },
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
            // Título genérico, no el que lleva `{{nombre}}`: este es el respaldo
            // para cuando no hay producto con qué rellenarlo —el slug no existe,
            // o la consulta falló— y una pestaña que dijera "{{nombre}} · Tecno
            // Sport" sería peor que una que dice "Producto". El título de verdad
            // lo pone la ficha con `usarMetadatos()` en cuanto llega el producto.
            //
            // Y **sin indexar**, aunque la ficha con producto sí se indexe. Un
            // slug inventado no da 404: el router lo acepta, la consulta falla y
            // la página responde 200 diciendo "No encontramos este producto".
            // Eso es un *soft 404*, y marcarlo indexable aquí invitaría a Google
            // a guardar una URL basura por cada enlace roto que alguien publique.
            // La ficha que sí cargó su producto declara `indexable: true` por su
            // cuenta y pisa este valor, que es exactamente el orden correcto:
            // se indexa lo que existe, no lo que la ruta admite.
            data: { seo: { clave: 'catalogo.seo.ficha' } },
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
