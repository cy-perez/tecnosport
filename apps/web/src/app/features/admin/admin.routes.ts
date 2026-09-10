import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { REPOSITORIO_ATRIBUTOS } from '../catalogo/domain/repositorio-atributos.puerto';
import { REPOSITORIO_CATEGORIAS } from '../catalogo/domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS } from '../catalogo/domain/repositorio-marcas.puerto';
import { AtributosHttpRepositorio } from '../catalogo/infrastructure/atributos-http.repositorio';
import { CategoriasHttpRepositorio } from '../catalogo/infrastructure/categorias-http.repositorio';
import { MarcasHttpRepositorio } from '../catalogo/infrastructure/marcas-http.repositorio';
import { adminGuard } from './admin.guard';
import { CapturaStore } from '../captura360/application/captura.store';
import { ALMACEN_LOCAL_DE_CAPTURAS } from '../captura360/domain/almacen-local-capturas.puerto';
import { PROCESADOR_DE_FOTOGRAMAS } from '../captura360/domain/procesador-fotogramas.puerto';
import { REPOSITORIO_SETS_ROTACION } from '../captura360/domain/repositorio-sets-rotacion.puerto';
import { AlmacenLocalIndexedDb } from '../captura360/infrastructure/almacen-local-indexeddb';
import { ProcesadorCanvas } from '../captura360/infrastructure/procesador-canvas';
import { SetsRotacionHttpRepositorio } from '../captura360/infrastructure/sets-rotacion-http.repositorio';
import { CAMARA } from '../captura360/domain/camara.puerto';
import { PANTALLA_DESPIERTA } from '../captura360/domain/pantalla-despierta.puerto';
import { SENSOR_ORIENTACION } from '../captura360/domain/sensor-orientacion.puerto';
import { CamaraNavegador } from '../captura360/infrastructure/camara-navegador';
import { PantallaDespiertaNavegador } from '../captura360/infrastructure/pantalla-despierta-navegador';
import { SensorOrientacionNavegador } from '../captura360/infrastructure/sensor-orientacion-navegador';
import { REPOSITORIO_PEDIDOS_ADMIN } from './pedidos/domain/repositorio-pedidos-admin.puerto';
import { PedidosAdminHttpRepositorio } from './pedidos/infrastructure/pedidos-admin-http.repositorio';
import { REPOSITORIO_RETRACTOS } from './retractos/domain/repositorio-retractos.puerto';
import { RetractosHttpRepositorio } from './retractos/infrastructure/retractos-http.repositorio';
import { REPOSITORIO_PRODUCTOS_ADMIN } from './productos/domain/repositorio-productos-admin.puerto';
import { ProductosAdminHttpRepositorio } from './productos/infrastructure/productos-admin-http.repositorio';
import { precargarScopeI18n } from '../../core/i18n/precargar-scope';

// Sin proveedor de puerto aquí: REPOSITORIO_SESION es compartido y se
// provee en app.config.ts (SesionStore lo va a necesitar también
// features/cuenta más adelante) — mismo criterio que REPOSITORIO_CARRITO.
export const adminRoutes: Routes = [
  {
    path: '',
    providers: [provideTranslocoScope('admin')],
    // Declarado una sola vez arriba y heredado por las siete pantallas del
    // panel: ninguna se indexa, y ninguna necesita un título propio porque solo
    // las ve quien ya entró. `MetadatosSeo` se queda con la declaración más
    // profunda de la rama, así que una pantalla puede afinarlo —la captura 360
    // lo hace— sin que las demás repitan nada.
    data: { seo: { clave: 'seo.admin' } },
    // El scope de i18n se precarga como cualquier otro dato de la primera
    // pantalla (ADR-0011): si llega después del primer render, toda etiqueta
    // que no pase por el pipe sale en blanco o con la clave cruda.
    resolve: { _i18n: () => Promise.all([precargarScopeI18n('admin')]) },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'panel' },
      {
        path: 'iniciar-sesion',
        loadComponent: () =>
          import('./iniciar-sesion/iniciar-sesion-admin.page').then(
            (m) => m.IniciarSesionAdminPage,
          ),
      },
      {
        path: 'panel',
        canActivate: [adminGuard],
        loadComponent: () => import('./panel/panel-admin.page').then((m) => m.PanelAdminPage),
      },
      {
        path: 'pedidos',
        canActivate: [adminGuard],
        // El panel de retracto vive dentro de la fila expandida de esta lista, así que su puerto
        // se provee en la misma ruta y no en una propia.
        providers: [
          { provide: REPOSITORIO_PEDIDOS_ADMIN, useClass: PedidosAdminHttpRepositorio },
          { provide: REPOSITORIO_RETRACTOS, useClass: RetractosHttpRepositorio },
        ],
        loadComponent: () =>
          import('./pedidos/presentation/lista/lista-pedidos-admin.page').then(
            (m) => m.ListaPedidosAdminPage,
          ),
      },
      {
        path: 'productos',
        canActivate: [adminGuard],
        providers: [
          { provide: REPOSITORIO_PRODUCTOS_ADMIN, useClass: ProductosAdminHttpRepositorio },
        ],
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
          {
            path: ':id/editar',
            providers: [
              { provide: REPOSITORIO_CATEGORIAS, useClass: CategoriasHttpRepositorio },
              { provide: REPOSITORIO_MARCAS, useClass: MarcasHttpRepositorio },
            ],
            loadComponent: () =>
              import('./productos/presentation/editar/editar-producto-admin.page').then(
                (m) => m.EditarProductoAdminPage,
              ),
          },
          {
            path: ':productoId/captura-360',
            // Scope propio y no una sección más de `admin`: el asistente es su propio bundle y
            // sus textos no le hacen falta a nadie más del panel.
            providers: [
              provideTranslocoScope('captura360'),
              { provide: ALMACEN_LOCAL_DE_CAPTURAS, useClass: AlmacenLocalIndexedDb },
              { provide: CAMARA, useClass: CamaraNavegador },
              { provide: PROCESADOR_DE_FOTOGRAMAS, useClass: ProcesadorCanvas },
              { provide: REPOSITORIO_SETS_ROTACION, useClass: SetsRotacionHttpRepositorio },
              { provide: SENSOR_ORIENTACION, useClass: SensorOrientacionNavegador },
              { provide: PANTALLA_DESPIERTA, useClass: PantallaDespiertaNavegador },
              CapturaStore,
            ],
            data: { seo: { clave: 'seo.captura360' } },
            resolve: { _i18nCaptura: () => Promise.all([precargarScopeI18n('captura360')]) },
            loadComponent: () =>
              import('../captura360/presentation/captura-360.page').then((m) => m.Captura360Page),
          },
          {
            path: ':productoId/variantes/crear',
            providers: [{ provide: REPOSITORIO_ATRIBUTOS, useClass: AtributosHttpRepositorio }],
            loadComponent: () =>
              import('./productos/presentation/variantes/agregar-variante-admin.page').then(
                (m) => m.AgregarVarianteAdminPage,
              ),
          },
        ],
      },
    ],
  },
];
