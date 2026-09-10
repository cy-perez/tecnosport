import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { Marca } from '../domain/producto.model';
import { RepositorioMarcas } from '../domain/repositorio-marcas.puerto';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';
import { aMarca } from './mapeador-productos';

@Injectable()
export class MarcasHttpRepositorio implements RepositorioMarcas {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarTodas(): Promise<Marca[]> {
    const respuesta = await this.cliente.GET('/api/v1/marcas');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las marcas');

    return (datos.items ?? []).map(aMarca);
  }
}
