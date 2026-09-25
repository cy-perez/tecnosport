import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { REPOSITORIO_ATRIBUTOS } from '../catalogo/domain/repositorio-atributos.puerto';
import { REPOSITORIO_CATEGORIAS } from '../catalogo/domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS } from '../catalogo/domain/repositorio-marcas.puerto';
import { AtributosHttpRepositorio } from '../catalogo/infrastructure/atributos-http.repositorio';
import { CategoriasAdminHttpRepositorio } from './categorias/infrastructure/categorias-admin-http.repositorio';
import { REPOSITORIO_CATEGORIAS_ADMIN } from './categorias/domain/repositorio-categorias-admin.puerto';
import { MarcasAdminHttpRepositorio } from './marcas/infrastructure/marcas-admin-http.repositorio';
import { REPOSITORIO_MARCAS_ADMIN } from './marcas/domain/repositorio-marcas-admin.puerto';
import { adminGuard } from './admin.guard';
import { REPOSITORIO_ATENCION } from './atencion/domain/repositorio-atencion.puerto';
import { AtencionHttpRepositorio } from './atencion/infrastructure/atencion-http.repositorio';
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
import { REPOSITORIO_REVISION_ENVIOS } from './envios/domain/repositorio-revision-envios.puerto';
import { RevisionEnviosHttpRepositorio } from './envios/infrastructure/revision-envios-http.repositorio';
import { REPOSITORIO_GARANTIAS } from './garantias/domain/repositorio-garantias.puerto';
import { GarantiasHttpRepositorio } from './garantias/infrastructure/garantias-http.repositorio';
import { REPOSITORIO_PEDIDOS_ADMIN } from './pedidos/domain/repositorio-pedidos-admin.puerto';
import { REPOSITORIO_REVERSIONES } from './reversiones/domain/repositorio-reversiones.puerto';
import { ReversionesHttpRepositorio } from './reversiones/infrastructure/reversiones-http.repositorio';
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
      // El marco con la barra de secciones, y con el `adminGuard` una sola vez. Las nueve
      // pantallas de abajo lo declaraban cada una por su cuenta —la misma condición escrita nueve
      // veces— y `iniciar-sesion` se queda fuera a propósito: es hermana de este nodo, no hija,
      // porque ni tiene sesión que proteger ni sitio a donde llevar desde las pestañas.
      {
        path: '',
        canActivate: [adminGuard],
        loadComponent: () => import('./marco/marco-admin.page').then((m) => m.MarcoAdminPage),
        children: [
          {
            path: 'panel',
            // El panel necesita el puerto de productos por el aviso de variantes sin medir. Es la
            // única consulta que hace, y va aquí y no en `productos` porque el aviso vive en esta
            // pantalla: el enlace a la lista es lo que lleva a la otra rama, que trae el suyo.
            providers: [
              { provide: REPOSITORIO_PRODUCTOS_ADMIN, useClass: ProductosAdminHttpRepositorio },
            ],
            loadComponent: () => import('./panel/panel-admin.page').then((m) => m.PanelAdminPage),
          },
          {
            // Sin proveedores propios: lo unico que necesita es `SesionStore`, que es de raiz.
            path: 'clave',
            loadComponent: () =>
              import('./clave/cambiar-clave-admin.page').then((m) => m.CambiarClaveAdminPage),
          },
          {
            path: 'pedidos',
            // Los paneles de retracto, garantía y reversión viven dentro de la fila expandida de esta
            // lista, así que sus puertos se proveen en la misma ruta y no en una propia.
            providers: [
              { provide: REPOSITORIO_PEDIDOS_ADMIN, useClass: PedidosAdminHttpRepositorio },
              { provide: REPOSITORIO_RETRACTOS, useClass: RetractosHttpRepositorio },
              { provide: REPOSITORIO_GARANTIAS, useClass: GarantiasHttpRepositorio },
              { provide: REPOSITORIO_REVERSIONES, useClass: ReversionesHttpRepositorio },
            ],
            loadComponent: () =>
              import('./pedidos/presentation/lista/lista-pedidos-admin.page').then(
                (m) => m.ListaPedidosAdminPage,
              ),
          },
          {
            path: 'atencion',
            providers: [{ provide: REPOSITORIO_ATENCION, useClass: AtencionHttpRepositorio }],
            loadComponent: () =>
              import('./atencion/presentation/bandeja/bandeja-atencion.page').then(
                (m) => m.BandejaAtencionPage,
              ),
          },
          {
            path: 'envios',
            providers: [
              { provide: REPOSITORIO_REVISION_ENVIOS, useClass: RevisionEnviosHttpRepositorio },
            ],
            loadComponent: () =>
              import('./envios/presentation/bandeja/bandeja-revision.page').then(
                (m) => m.BandejaRevisionPage,
              ),
          },
          {
            path: 'categorias',
            // Los dos puertos: esta pantalla lee el árbol entero y lo escribe. El de la vitrina hace
            // falta porque el formulario de la pantalla ofrece las madres posibles, que salen del
            // mismo listado.
            providers: [
              { provide: REPOSITORIO_CATEGORIAS_ADMIN, useClass: CategoriasAdminHttpRepositorio },
            ],
            loadComponent: () =>
              import('./categorias/presentation/categorias-admin.page').then(
                (m) => m.CategoriasAdminPage,
              ),
          },
          {
            path: 'marcas',
            // Solo el puerto de admin: esta pantalla lista y crea. El de la vitrina lo siguen
            // proveyendo las rutas del formulario de producto, que es donde hace falta leer marcas.
            providers: [
              { provide: REPOSITORIO_MARCAS_ADMIN, useClass: MarcasAdminHttpRepositorio },
            ],
            loadComponent: () =>
              import('./marcas/presentation/marcas-admin.page').then((m) => m.MarcasAdminPage),
          },
          {
            path: 'productos',
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
                path: 'sin-medir',
                loadComponent: () =>
                  import('./productos/presentation/sin-medir/variantes-sin-medir-admin.page').then(
                    (m) => m.VariantesSinMedirAdminPage,
                  ),
              },
              {
                path: 'medidas',
                loadComponent: () =>
                  import('./productos/presentation/medidas/medidas-admin.page').then(
                    (m) => m.MedidasAdminPage,
                  ),
              },
              {
                path: 'existencias',
                loadComponent: () =>
                  import('./productos/presentation/existencias/existencias-admin.page').then(
                    (m) => m.ExistenciasAdminPage,
                  ),
              },
              {
                path: 'crear',
                providers: [
                  // Los del panel y no los de la vitrina: el endpoint público solo devuelve
                  // marcas y categorías con productos publicados, así que el desplegable no
                  // ofrecería nunca aquella a la que hay que cargarle el primero.
                  { provide: REPOSITORIO_CATEGORIAS, useClass: CategoriasAdminHttpRepositorio },
                  { provide: REPOSITORIO_MARCAS, useClass: MarcasAdminHttpRepositorio },
                ],
                loadComponent: () =>
                  import('./productos/presentation/crear/crear-producto-admin.page').then(
                    (m) => m.CrearProductoAdminPage,
                  ),
              },
              {
                path: ':id/editar',
                providers: [
                  // Los del panel y no los de la vitrina: el endpoint público solo devuelve
                  // marcas y categorías con productos publicados, así que el desplegable no
                  // ofrecería nunca aquella a la que hay que cargarle el primero.
                  { provide: REPOSITORIO_CATEGORIAS, useClass: CategoriasAdminHttpRepositorio },
                  { provide: REPOSITORIO_MARCAS, useClass: MarcasAdminHttpRepositorio },
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
                  import('../captura360/presentation/captura-360.page').then(
                    (m) => m.Captura360Page,
                  ),
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
    ],
  },
];
