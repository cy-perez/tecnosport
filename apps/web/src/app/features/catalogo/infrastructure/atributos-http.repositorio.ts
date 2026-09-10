import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { Atributo } from '../domain/producto.model';
import { RepositorioAtributos } from '../domain/repositorio-atributos.puerto';
import { aAtributo } from './mapeador-productos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';

@Injectable()
export class AtributosHttpRepositorio implements RepositorioAtributos {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarTodas(): Promise<Atributo[]> {
    const respuesta = await this.cliente.GET('/api/v1/atributos');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar los atributos');

    return (datos.items ?? []).map(aAtributo);
  }
}
