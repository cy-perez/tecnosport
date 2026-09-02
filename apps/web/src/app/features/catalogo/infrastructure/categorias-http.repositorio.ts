import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { Categoria } from '../domain/producto.model';
import { RepositorioCategorias } from '../domain/repositorio-categorias.puerto';
import { aCategoria } from './mapeador-productos';
import { baseUrl } from './base-url';

@Injectable()
export class CategoriasHttpRepositorio implements RepositorioCategorias {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarTodas(): Promise<Categoria[]> {
    const { data, error } = await this.cliente.GET('/api/v1/categorias');

    if (error) {
      throw new Error('No se pudieron cargar las categorías.');
    }

    return (data.items ?? []).map(aCategoria);
  }
}
