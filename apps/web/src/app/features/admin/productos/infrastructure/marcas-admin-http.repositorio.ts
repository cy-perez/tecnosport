import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { Marca } from '../../../catalogo/domain/producto.model';
import { RepositorioMarcas } from '../../../catalogo/domain/repositorio-marcas.puerto';
import { aMarca } from '../../../catalogo/infrastructure/mapeador-productos';
import { baseUrl } from '../../../../core/http/base-url';
import { desempaquetar } from '../../../../core/http/respuesta-http';

/**
 * Las marcas del panel salen de `/api/v1/admin/marcas` y no del endpoint público, que desde el
 * 19 de septiembre de 2026 solo devuelve las que tienen algún producto publicado — es el filtro de
 * la vitrina.
 *
 * Si el panel usara aquel, la marca recién creada no aparecería en el desplegable y no habría forma
 * de cargarle su primer producto. Cumple el mismo puerto, así que las pantallas no se enteran.
 */
@Injectable()
export class MarcasAdminHttpRepositorio implements RepositorioMarcas {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarTodas(): Promise<Marca[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/marcas');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las marcas');

    return (datos.items ?? []).map(aMarca);
  }
}
