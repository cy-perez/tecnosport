import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import {
  AprobarBorrador,
  Borrador,
  BorradorDetalle,
  BorradoresPaginados,
  EditarBorrador,
  FiltroBorradores,
} from '../domain/borrador.model';
import { RepositorioBorradoresAdmin } from '../domain/repositorio-borradores-admin.puerto';
import {
  aAprobarPeticion,
  aBorrador,
  aBorradorDetalle,
  aBorradoresPaginados,
  aEditarPeticion,
} from './mapeador-borrador';

const TAMANO_PAGINA = 20;

@Injectable()
export class BorradoresAdminHttpRepositorio implements RepositorioBorradoresAdmin {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(filtro: FiltroBorradores): Promise<BorradoresPaginados> {
    const respuesta = await this.cliente.GET('/api/v1/admin/borradores', {
      params: {
        query: {
          ...(filtro.estado ? { estado: filtro.estado } : {}),
          ...(filtro.proveedorId ? { proveedorId: filtro.proveedorId } : {}),
          pagina: filtro.pagina,
          tamano: TAMANO_PAGINA,
        },
      },
    });
    return aBorradoresPaginados(desempaquetar(respuesta, 'no se pudieron cargar los borradores'));
  }

  async obtener(id: string): Promise<BorradorDetalle> {
    const respuesta = await this.cliente.GET('/api/v1/admin/borradores/{id}', {
      params: { path: { id } },
    });
    return aBorradorDetalle(desempaquetar(respuesta, 'no se pudo cargar el borrador'));
  }

  async editar(id: string, cambios: EditarBorrador): Promise<Borrador> {
    const respuesta = await this.cliente.PATCH('/api/v1/admin/borradores/{id}', {
      params: { path: { id } },
      body: aEditarPeticion(cambios),
    });
    return aBorrador(desempaquetar(respuesta, 'no se pudo guardar el borrador'));
  }

  async aprobar(id: string, comando: AprobarBorrador): Promise<Borrador> {
    const respuesta = await this.cliente.POST('/api/v1/admin/borradores/{id}/aprobar', {
      params: { path: { id } },
      body: aAprobarPeticion(comando),
    });
    return aBorrador(desempaquetar(respuesta, 'no se pudo aprobar el borrador'));
  }

  async rechazar(id: string, motivo: string): Promise<Borrador> {
    const respuesta = await this.cliente.POST('/api/v1/admin/borradores/{id}/rechazar', {
      params: { path: { id } },
      body: { motivo },
    });
    return aBorrador(desempaquetar(respuesta, 'no se pudo rechazar el borrador'));
  }
}
