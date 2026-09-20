import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { Categoria } from '../../../catalogo/domain/producto.model';
import { RepositorioCategorias } from '../../../catalogo/domain/repositorio-categorias.puerto';
import { aCategoria } from '../../../catalogo/infrastructure/mapeador-productos';
import { baseUrl } from '../../../../core/http/base-url';
import { desempaquetar } from '../../../../core/http/respuesta-http';

/**
 * Mismo motivo que `MarcasAdminHttpRepositorio`, y aquí pesa más: la migración `V38` dejó la línea de
 * tecnología con once categorías y casi todas siguen sin un solo producto. Por el endpoint público
 * no sale ninguna de ellas, que es lo correcto para la vitrina y lo inservible para el panel.
 */
@Injectable()
export class CategoriasAdminHttpRepositorio implements RepositorioCategorias {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarTodas(): Promise<Categoria[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/categorias');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las categorías');

    return (datos.items ?? []).map(aCategoria);
  }
}
