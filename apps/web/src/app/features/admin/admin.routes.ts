import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { REPOSITORIO_ATRIBUTOS } from '../catalogo/domain/repositorio-atributos.puerto';
import { REPOSITORIO_CATEGORIAS } from '../catalogo/domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS } from '../catalogo/domain/repositorio-marcas.puerto';
import { AtributosHttpRepositorio } from '../catalogo/infrastructure/atributos-http.repositorio';
import { REPOSITORIO_DIFUSION } from './difusion/domain/repositorio-difusion.puerto';
import { DifusionHttpRepositorio } from './difusion/infrastructure/difusion-http.repositorio';
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
import { REPOSITORIO_REFERENCIAS_ENVIO } from './envios/domain/repositorio-referencias-envio.puerto';
import { ReferenciasEnvioHttpRepositorio } from './envios/infrastructure/referencias-envio-http.repositorio';
import { REPOSITORIO_PAQUETES_PEDIDO } from './envios/domain/repositorio-paquetes-pedido.puerto';
import { PaquetesPedidoHttpRepositorio } from './envios/infrastructure/paquetes-pedido-http.repositorio';
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
import { REPOSITORIO_PROVEEDORES_ADMIN } from './proveedores/domain/repositorio-proveedores-admin.puerto';
import { ProveedoresAdminHttpRepositorio } from './proveedores/infrastructure/proveedores-admin-http.repositorio';
import { REPOSITORIO_INGESTAS_ADMIN } from './ingestas/domain/repositorio-ingestas-admin.puerto';
import { IngestasAdminHttpRepositorio } from './ingestas/infrastructure/ingestas-admin-http.repositorio';
import { REPOSITORIO_BORRADORES_ADMIN } from './borradores/domain/repositorio-borradores-admin.puerto';
import { BorradoresAdminHttpRepositorio } from './borradores/infrastructure/borradores-admin-http.repositorio';
import { REPOSITORIO_BORRADORES_TECNOLOGIA } from './tecnologia/domain/repositorio-borradores-tecnologia.puerto';
import { BorradoresTecnologiaHttpRepositorio } from './tecnologia/infrastructure/borradores-tecnologia-http.repositorio';

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
              // Los paquetes para crear la guía a mano (ADR-0071), también en la fila expandida.
              { provide: REPOSITORIO_PAQUETES_PEDIDO, useClass: PaquetesPedidoHttpRepositorio },
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
            // Los pesos y las medidas con los que se cotiza lo que no se ha medido (ADR-0071).
            // Ruta propia bajo `envios` y no una pestaña dentro de la bandeja: aquella es lo que pide
            // ojo humano hoy, y esto es configuración que se toca de vez en cuando.
            path: 'envios/referencias',
            providers: [
              {
                provide: REPOSITORIO_REFERENCIAS_ENVIO,
                useClass: ReferenciasEnvioHttpRepositorio,
              },
            ],
            loadComponent: () =>
              import('./envios/presentation/referencias/referencias-envio.page').then(
                (m) => m.ReferenciasEnvioPage,
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
            path: 'proveedores',
            providers: [
              { provide: REPOSITORIO_PROVEEDORES_ADMIN, useClass: ProveedoresAdminHttpRepositorio },
            ],
            children: [
              {
                path: '',
                loadComponent: () =>
                  import('./proveedores/presentation/lista/lista-proveedores-admin.page').then(
                    (m) => m.ListaProveedoresAdminPage,
                  ),
              },
              {
                path: 'crear',
                loadComponent: () =>
                  import('./proveedores/presentation/formulario/formulario-proveedor-admin.page').then(
                    (m) => m.FormularioProveedorAdminPage,
                  ),
              },
              {
                // `:id` y no `:id/editar` como en productos: la ficha del proveedor es su edicion,
                // no hay otra cosa que ver de el. Y `admin.routes.spec.ts` busca la primera ruta
                // `:id/editar` del arbol para comprobar la de productos.
                path: ':id',
                loadComponent: () =>
                  import('./proveedores/presentation/formulario/formulario-proveedor-admin.page').then(
                    (m) => m.FormularioProveedorAdminPage,
                  ),
              },
            ],
          },
          {
            path: 'ingestas',
            // El de proveedores también: el formulario de subida elige a quién y la tabla nombra
            // al dueño de cada lote.
            providers: [
              { provide: REPOSITORIO_INGESTAS_ADMIN, useClass: IngestasAdminHttpRepositorio },
              { provide: REPOSITORIO_PROVEEDORES_ADMIN, useClass: ProveedoresAdminHttpRepositorio },
            ],
            loadComponent: () =>
              import('./ingestas/presentation/lista/lista-ingestas-admin.page').then(
                (m) => m.ListaIngestasAdminPage,
              ),
          },
          {
            path: 'borradores',
            providers: [
              { provide: REPOSITORIO_BORRADORES_ADMIN, useClass: BorradoresAdminHttpRepositorio },
              { provide: REPOSITORIO_PROVEEDORES_ADMIN, useClass: ProveedoresAdminHttpRepositorio },
            ],
            children: [
              {
                path: '',
                loadComponent: () =>
                  import('./borradores/presentation/lista/lista-borradores-admin.page').then(
                    (m) => m.ListaBorradoresAdminPage,
                  ),
              },
              {
                path: ':id',
                // Aprobar crea un producto y pide marca y categoría: los del panel y no los de la
                // vitrina, por lo mismo que en el alta de producto.
                providers: [
                  { provide: REPOSITORIO_CATEGORIAS, useClass: CategoriasAdminHttpRepositorio },
                  { provide: REPOSITORIO_MARCAS, useClass: MarcasAdminHttpRepositorio },
                ],
                loadComponent: () =>
                  import('./borradores/presentation/detalle/detalle-borrador-admin.page').then(
                    (m) => m.DetalleBorradorAdminPage,
                  ),
              },
            ],
          },
          {
            path: 'tecnologia',
            providers: [
              {
                provide: REPOSITORIO_BORRADORES_TECNOLOGIA,
                useClass: BorradoresTecnologiaHttpRepositorio,
              },
              { provide: REPOSITORIO_PROVEEDORES_ADMIN, useClass: ProveedoresAdminHttpRepositorio },
            ],
            children: [
              {
                path: '',
                loadComponent: () =>
                  import('./tecnologia/presentation/lista/lista-tecnologia-admin.page').then(
                    (m) => m.ListaTecnologiaAdminPage,
                  ),
              },
              {
                path: ':id',
                // Aprobar un modelo nuevo pide marca y categoría: las del panel, como en borradores.
                providers: [
                  { provide: REPOSITORIO_CATEGORIAS, useClass: CategoriasAdminHttpRepositorio },
                  { provide: REPOSITORIO_MARCAS, useClass: MarcasAdminHttpRepositorio },
                ],
                loadComponent: () =>
                  import('./tecnologia/presentation/detalle/detalle-tecnologia-admin.page').then(
                    (m) => m.DetalleTecnologiaAdminPage,
                  ),
              },
            ],
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
                  // Solo aquí y no en `crear`: el panel de difusión se monta al final de esta
                  // ficha, y un producto que todavía no existe no se puede difundir.
                  //
                  // Estuvo en `crear` por error entre el 29 y el 30 de septiembre de 2026, y la
                  // ficha reventaba con un NG0201 y la pantalla en blanco. Las dos rutas tienen
                  // bloques de `providers` idénticos, así que un reemplazo pegó en la primera.
                  // Ninguna prueba lo vio porque los specs de página proveen los puertos a mano:
                  // lo cubre `admin.routes.spec.ts`, que mira esta configuración y no un montaje.
                  { provide: REPOSITORIO_DIFUSION, useClass: DifusionHttpRepositorio },
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
                providers: [
                  { provide: REPOSITORIO_ATRIBUTOS, useClass: AtributosHttpRepositorio },
                  // Por la escala de tallas de la categoría del producto: la talla se elige de
                  // ella. Las del panel, como en `editar`, porque son las que ve la revisión.
                  { provide: REPOSITORIO_CATEGORIAS, useClass: CategoriasAdminHttpRepositorio },
                ],
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
