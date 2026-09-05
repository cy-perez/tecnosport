import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { Atributo } from '../domain/producto.model';
import { RepositorioAtributos } from '../domain/repositorio-atributos.puerto';
import { aAtributo } from './mapeador-productos';
import { baseUrl } from '../../../core/http/base-url';

@Injectable()
export class AtributosHttpRepositorio implements RepositorioAtributos {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarTodas(): Promise<Atributo[]> {
    const { data, error } = await this.cliente.GET('/api/v1/atributos');

    if (error) {
      throw new Error('No se pudieron cargar los atributos.');
    }

    return (data.items ?? []).map(aAtributo);
  }
}
