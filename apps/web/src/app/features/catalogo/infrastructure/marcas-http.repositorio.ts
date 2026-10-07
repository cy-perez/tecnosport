import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { MarcaDeVitrina } from '../domain/producto.model';
import { RepositorioMarcasDeVitrina } from '../domain/repositorio-marcas.puerto';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';
import { aMarcaDeVitrina } from './mapeador-productos';

@Injectable()
export class MarcasHttpRepositorio implements RepositorioMarcasDeVitrina {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarDeVitrina(): Promise<MarcaDeVitrina[]> {
    const respuesta = await this.cliente.GET('/api/v1/marcas');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las marcas');

    return (datos.items ?? []).map(aMarcaDeVitrina);
  }
}
