import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import {
  AprobarBorradorTecnologia,
  BorradorTecnologia,
  EleccionDeConfiguracion,
  EstadoBorradorTecnologia,
} from '../domain/borrador-tecnologia.model';
import { RepositorioBorradoresTecnologia } from '../domain/repositorio-borradores-tecnologia.puerto';
import { aBorradorTecnologia, aElegirPeticion } from './mapeador-borrador-tecnologia';

@Injectable()
export class BorradoresTecnologiaHttpRepositorio implements RepositorioBorradoresTecnologia {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(estado: EstadoBorradorTecnologia): Promise<BorradorTecnologia[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/borradores-tecnologia', {
      params: { query: { estado } },
    });
    return desempaquetar(respuesta, 'no se pudieron cargar los borradores de tecnología').map(
      aBorradorTecnologia,
    );
  }

  async obtener(id: string): Promise<BorradorTecnologia> {
    const respuesta = await this.cliente.GET('/api/v1/admin/borradores-tecnologia/{id}', {
      params: { path: { id } },
    });
    return aBorradorTecnologia(desempaquetar(respuesta, 'no se pudo cargar el borrador'));
  }

  async elegir(
    id: string,
    elecciones: readonly EleccionDeConfiguracion[],
  ): Promise<BorradorTecnologia> {
    const respuesta = await this.cliente.PATCH('/api/v1/admin/borradores-tecnologia/{id}', {
      params: { path: { id } },
      body: aElegirPeticion(elecciones),
    });
    return aBorradorTecnologia(desempaquetar(respuesta, 'no se pudo guardar la elección'));
  }

  async aprobar(id: string, aprobacion: AprobarBorradorTecnologia): Promise<string> {
    const respuesta = await this.cliente.POST('/api/v1/admin/borradores-tecnologia/{id}/aprobar', {
      params: { path: { id } },
      body: {
        ...(aprobacion.marcaId ? { marcaId: aprobacion.marcaId } : {}),
        ...(aprobacion.categoriaId ? { categoriaId: aprobacion.categoriaId } : {}),
      },
    });
    const producto = desempaquetar(respuesta, 'no se pudo aprobar el borrador');
    return producto.id ?? '';
  }

  async rechazar(id: string, motivo: string): Promise<BorradorTecnologia> {
    const respuesta = await this.cliente.POST('/api/v1/admin/borradores-tecnologia/{id}/rechazar', {
      params: { path: { id } },
      body: { motivo },
    });
    return aBorradorTecnologia(desempaquetar(respuesta, 'no se pudo rechazar el borrador'));
  }
}
