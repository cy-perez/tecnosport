import { InjectionToken } from '@angular/core';
import { CrearProductoAdmin, FiltroProductosAdmin, ProductoAdmin, ProductosPaginadosAdmin } from './producto-admin.model';

export interface RepositorioProductosAdmin {
  listar(filtro: FiltroProductosAdmin): Promise<ProductosPaginadosAdmin>;

  crear(comando: CrearProductoAdmin): Promise<ProductoAdmin>;
}

export const REPOSITORIO_PRODUCTOS_ADMIN = new InjectionToken<RepositorioProductosAdmin>(
  'RepositorioProductosAdmin',
);
