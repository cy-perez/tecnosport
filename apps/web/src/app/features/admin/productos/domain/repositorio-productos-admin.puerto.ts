import { InjectionToken } from '@angular/core';
import {
  AgregarVarianteAdmin,
  CrearProductoAdmin,
  EditarProductoAdmin,
  FiltroProductosAdmin,
  ProductoAdmin,
  ProductosPaginadosAdmin,
} from './producto-admin.model';

export interface RepositorioProductosAdmin {
  listar(filtro: FiltroProductosAdmin): Promise<ProductosPaginadosAdmin>;

  crear(comando: CrearProductoAdmin): Promise<ProductoAdmin>;

  obtener(id: string): Promise<ProductoAdmin>;

  editar(id: string, comando: EditarProductoAdmin): Promise<ProductoAdmin>;

  agregarVariante(comando: AgregarVarianteAdmin): Promise<void>;
}

export const REPOSITORIO_PRODUCTOS_ADMIN = new InjectionToken<RepositorioProductosAdmin>(
  'RepositorioProductosAdmin',
);
