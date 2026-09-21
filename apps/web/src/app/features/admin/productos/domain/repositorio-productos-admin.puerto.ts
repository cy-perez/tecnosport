import { InjectionToken } from '@angular/core';
import {
  AgregarVarianteAdmin,
  CrearProductoAdmin,
  EditarProductoAdmin,
  FiltroProductosAdmin,
  ImagenAdmin,
  AjustarExistenciaAdmin,
  ExistenciaAjustada,
  ExistenciasDelCatalogo,
  InventarioSinMedir,
  MedidasDelCatalogo,
  MedirVarianteAdmin,
  ProductoAdmin,
  ProductosPaginadosAdmin,
  SubirImagenPrincipalAdmin,
  VarianteMedida,
} from './producto-admin.model';

export interface RepositorioProductosAdmin {
  listar(filtro: FiltroProductosAdmin): Promise<ProductosPaginadosAdmin>;

  crear(comando: CrearProductoAdmin): Promise<ProductoAdmin>;

  obtener(id: string): Promise<ProductoAdmin>;

  editar(id: string, comando: EditarProductoAdmin): Promise<ProductoAdmin>;

  agregarVariante(comando: AgregarVarianteAdmin): Promise<void>;

  /** Lo que falta por medir, entero y sin paginar: es una lista de tareas que tiene que llegar a
   * cero, no un listado del catálogo. */
  listarSinMedir(): Promise<InventarioSinMedir>;

  /** Todas las activas con su medida, tengan o no: la lista de la pantalla que corrige. */
  listarMedidas(): Promise<MedidasDelCatalogo>;

  medirVariante(comando: MedirVarianteAdmin): Promise<VarianteMedida>;

  listarExistencias(): Promise<ExistenciasDelCatalogo>;

  ajustarExistencia(comando: AjustarExistenciaAdmin): Promise<ExistenciaAjustada>;

  /** Encadena los tres pasos (URL firmada, PUT directo a Cloud Storage, confirmación) — ver
   * docs/07-infra-gcp.md. El PUT no pasa por el backend propio, pero sigue siendo infraestructura. */
  subirImagenPrincipal(comando: SubirImagenPrincipalAdmin): Promise<ImagenAdmin>;
}

export const REPOSITORIO_PRODUCTOS_ADMIN = new InjectionToken<RepositorioProductosAdmin>(
  'RepositorioProductosAdmin',
);
