import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { Marca } from '../domain/producto.model';
import { RepositorioMarcas } from '../domain/repositorio-marcas.puerto';
import { baseUrl } from './base-url';
import { aMarca } from './mapeador-productos';

@Injectable()
export class MarcasHttpRepositorio implements RepositorioMarcas {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarTodas(): Promise<Marca[]> {
    const { data, error } = await this.cliente.GET('/api/v1/marcas');

    if (error) {
      throw new Error('No se pudieron cargar las marcas.');
    }

    return (data.items ?? []).map(aMarca);
  }
}
