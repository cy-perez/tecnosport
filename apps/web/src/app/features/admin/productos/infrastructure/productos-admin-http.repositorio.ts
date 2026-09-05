import { Injectable, inject } from '@angular/core';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { FiltroProductosAdmin, ProductosPaginadosAdmin } from '../domain/producto-admin.model';
import { RepositorioProductosAdmin } from '../domain/repositorio-productos-admin.puerto';
import { aProductosPaginadosAdmin } from './mapeador-producto-admin';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer` — mismo criterio que
 * `admin/pedidos/infrastructure/pedidos-admin-http.repositorio.ts`. */
@Injectable()
export class ProductosAdminHttpRepositorio implements RepositorioProductosAdmin {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(filtro: FiltroProductosAdmin): Promise<ProductosPaginadosAdmin> {
    const { data, error } = await this.cliente.GET('/api/v1/admin/productos', {
      params: { query: { pagina: filtro.pagina, tamano: filtro.tamano } },
    });
    if (error) {
      throw new Error('No se pudo listar los productos.');
    }
    return aProductosPaginadosAdmin(data);
  }
}
