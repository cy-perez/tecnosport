import { InjectionToken } from '@angular/core';
import {
  AgregarVarianteAdmin,
  CrearProductoAdmin,
  EditarProductoAdmin,
  FiltroProductosAdmin,
  ImagenAdmin,
  ImagenDeGaleriaAdmin,
  AjustarExistenciaAdmin,
  ExistenciaAjustada,
  ExistenciasDelCatalogo,
  InventarioSinMedir,
  MedidasDelCatalogo,
  MedirVarianteAdmin,
  ProductoAdmin,
  ProductoAdminDetalle,
  ProductosPaginadosAdmin,
  QuitarImagenDeGaleriaAdmin,
  ReordenarGaleriaAdmin,
  SubirImagenDeGaleriaAdmin,
  SubirImagenPrincipalAdmin,
  VarianteMedida,
} from './producto-admin.model';

export interface RepositorioProductosAdmin {
  listar(filtro: FiltroProductosAdmin): Promise<ProductosPaginadosAdmin>;

  crear(comando: CrearProductoAdmin): Promise<ProductoAdmin>;

  /** El detalle trae la galería; la lista no (ver `ProductoAdminDetalle`). */
  obtener(id: string): Promise<ProductoAdminDetalle>;

  editar(id: string, comando: EditarProductoAdmin): Promise<ProductoAdmin>;

  agregarVariante(comando: AgregarVarianteAdmin): Promise<void>;

  /** Lo que falta por medir, entero y sin paginar: es una lista de tareas que tiene que llegar a
   * cero, no un listado del catálogo. */
  listarSinMedir(): Promise<InventarioSinMedir>;

  /** Publica un producto en BORRADOR: desde ese momento existe para quien compra. */
  publicar(id: string): Promise<ProductoAdmin>;

  /** Lo saca de la vitrina y lo devuelve a BORRADOR. Los pedidos en curso no se tocan. */
  despublicar(id: string): Promise<ProductoAdmin>;

  /**
   * Borra el producto entero: sus variantes, su inventario, sus imágenes y sus sets de rotación.
   * No tiene vuelta.
   *
   * <p>El servidor rechaza con 409 si está publicado —hay que retirarlo primero— o si alguna de sus
   * variantes se vendió. Las dos condiciones las decide él y no se adelantan aquí: el panel no ve
   * los pedidos, y un producto puede venderse entre que se pinta la lista y se pulsa el botón.
   */
  eliminar(id: string): Promise<void>;

  /** Todas las activas con su medida, tengan o no: la lista de la pantalla que corrige. */
  listarMedidas(): Promise<MedidasDelCatalogo>;

  medirVariante(comando: MedirVarianteAdmin): Promise<VarianteMedida>;

  listarExistencias(): Promise<ExistenciasDelCatalogo>;

  ajustarExistencia(comando: AjustarExistenciaAdmin): Promise<ExistenciaAjustada>;

  /** Encadena los tres pasos (URL firmada, PUT directo a Cloud Storage, confirmación) — ver
   * docs/07-infra-gcp.md. El PUT no pasa por el backend propio, pero sigue siendo infraestructura. */
  subirImagenPrincipal(comando: SubirImagenPrincipalAdmin): Promise<ImagenAdmin>;

  /** Los mismos tres pasos de la principal, contra el subrecurso `galeria`. */
  subirImagenDeGaleria(comando: SubirImagenDeGaleriaAdmin): Promise<ImagenDeGaleriaAdmin>;

  /** Saca la imagen de la ficha y borra su objeto del bucket. No tiene vuelta. */
  quitarImagenDeGaleria(comando: QuitarImagenDeGaleriaAdmin): Promise<void>;

  /**
   * Deja la galería en el orden pedido. Se manda la lista **entera**, no "sube esta": con dos
   * pestañas abiertas sobre el mismo producto, dos movimientos parciales se pisarían y ganaría el
   * último sin que nadie se entere.
   */
  reordenarGaleria(comando: ReordenarGaleriaAdmin): Promise<void>;
}

export const REPOSITORIO_PRODUCTOS_ADMIN = new InjectionToken<RepositorioProductosAdmin>(
  'RepositorioProductosAdmin',
);
