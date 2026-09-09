import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { Categoria } from '../domain/producto.model';
import { RepositorioCategorias } from '../domain/repositorio-categorias.puerto';
import { aCategoria } from './mapeador-productos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';

@Injectable()
export class CategoriasHttpRepositorio implements RepositorioCategorias {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarTodas(): Promise<Categoria[]> {
    const respuesta = await this.cliente.GET('/api/v1/categorias');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las categorías');

    return (datos.items ?? []).map(aCategoria);
  }
}
