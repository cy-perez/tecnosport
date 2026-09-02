import { InjectionToken } from '@angular/core';
import { FiltroProductos } from './filtro-productos.model';
import { Producto } from './producto.model';
import { ResultadoPaginado } from './resultado-paginado.model';

export interface RepositorioProductos {
  buscar(filtro: FiltroProductos, cursor: string | null): Promise<ResultadoPaginado<Producto>>;
  buscarPorSlug(slug: string): Promise<Producto | null>;
}

export const REPOSITORIO_PRODUCTOS = new InjectionToken<RepositorioProductos>('RepositorioProductos');
