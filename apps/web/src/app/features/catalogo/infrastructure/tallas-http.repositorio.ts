import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { OpcionDeTalla, TallasDisponibles } from '../domain/tallas.model';
import { RepositorioTallas } from '../domain/repositorio-tallas.puerto';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';

interface OpcionDeTallaRespuesta {
  valor?: string;
  etiqueta?: string;
}

/** Una opción sin valor no se puede filtrar: se descarta en vez de ofrecer una casilla vacía. */
function aOpciones(opciones: readonly OpcionDeTallaRespuesta[] | undefined): OpcionDeTalla[] {
  return (opciones ?? [])
    .filter((opcion) => !!opcion.valor)
    .map((opcion) => ({ valor: opcion.valor!, etiqueta: opcion.etiqueta || opcion.valor! }));
}

@Injectable()
export class TallasHttpRepositorio implements RepositorioTallas {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarDisponibles(): Promise<TallasDisponibles> {
    const respuesta = await this.cliente.GET('/api/v1/tallas');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las tallas');
    return { ropa: aOpciones(datos.ropa), calzado: aOpciones(datos.calzado) };
  }
}
