import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { REPOSITORIO_CATEGORIAS } from '../catalogo/domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS } from '../catalogo/domain/repositorio-marcas.puerto';
import { CategoriasHttpRepositorio } from '../catalogo/infrastructure/categorias-http.repositorio';
import { MarcasHttpRepositorio } from '../catalogo/infrastructure/marcas-http.repositorio';
import { adminGuard } from './admin.guard';
import { REPOSITORIO_PEDIDOS_ADMIN } from './pedidos/domain/repositorio-pedidos-admin.puerto';
import { PedidosAdminHttpRepositorio } from './pedidos/infrastructure/pedidos-admin-http.repositorio';
import { REPOSITORIO_PRODUCTOS_ADMIN } from './productos/domain/repositorio-productos-admin.puerto';
import { ProductosAdminHttpRepositorio } from './productos/infrastructure/productos-admin-http.repositorio';

// Sin proveedor de puerto aquí: REPOSITORIO_SESION es compartido y se
// provee en app.config.ts (SesionStore lo va a necesitar también
// features/cuenta más adelante) — mismo criterio que REPOSITORIO_CARRITO.
export const adminRoutes: Routes = [
  {
    path: '',
    providers: [provideTranslocoScope('admin')],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'panel' },
      {
        path: 'iniciar-sesion',
        loadComponent: () =>
          import('./iniciar-sesion/iniciar-sesion-admin.page').then((m) => m.IniciarSesionAdminPage),
      },
      {
        path: 'panel',
        canActivate: [adminGuard],
        loadComponent: () => import('./panel/panel-admin.page').then((m) => m.PanelAdminPage),
      },
      {
        path: 'pedidos',
        canActivate: [adminGuard],
        providers: [{ provide: REPOSITORIO_PEDIDOS_ADMIN, useClass: PedidosAdminHttpRepositorio }],
        loadComponent: () =>
          import('./pedidos/presentation/lista/lista-pedidos-admin.page').then((m) => m.ListaPedidosAdminPage),
      },
      {
        path: 'productos',
        canActivate: [adminGuard],
        providers: [{ provide: REPOSITORIO_PRODUCTOS_ADMIN, useClass: ProductosAdminHttpRepositorio }],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./productos/presentation/lista/lista-productos-admin.page').then(
                (m) => m.ListaProductosAdminPage,
              ),
          },
          {
            path: 'crear',
            providers: [
              { provide: REPOSITORIO_CATEGORIAS, useClass: CategoriasHttpRepositorio },
              { provide: REPOSITORIO_MARCAS, useClass: MarcasHttpRepositorio },
            ],
            loadComponent: () =>
              import('./productos/presentation/crear/crear-producto-admin.page').then(
                (m) => m.CrearProductoAdminPage,
              ),
          },
        ],
      },
    ],
  },
];
