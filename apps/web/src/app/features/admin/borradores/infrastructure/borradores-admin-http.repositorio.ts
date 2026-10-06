import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { ErrorHttp, desempaquetar, exigirExito } from '../../../../core/http/respuesta-http';
import {
  AprobarBorrador,
  Borrador,
  BorradorDetalle,
  BorradoresPaginados,
  EditarBorrador,
  FiltroBorradores,
  FotoBorrador,
} from '../domain/borrador.model';
import { RepositorioBorradoresAdmin } from '../domain/repositorio-borradores-admin.puerto';
import {
  aAprobarPeticion,
  aBorrador,
  aBorradorDetalle,
  aBorradoresPaginados,
  aEditarPeticion,
  aFoto,
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

  async descartarFoto(id: string, mensajeId: string): Promise<void> {
    const respuesta = await this.cliente.DELETE('/api/v1/admin/borradores/{id}/fotos/{mensajeId}', {
      params: { path: { id, mensajeId } },
    });
    exigirExito(respuesta, 'no se pudo eliminar la foto del borrador');
  }

  /**
   * El `Content-Type` del `PUT` tiene que ser el mismo con que se firmó la URL, o Cloud Storage
   * responde 403: por eso se manda `archivo.type` en los dos lados.
   */
  async subirFoto(id: string, archivo: File): Promise<FotoBorrador> {
    const respuestaSolicitud = await this.cliente.POST(
      '/api/v1/admin/borradores/{id}/fotos/url-subida',
      {
        params: { path: { id } },
        body: { contentType: archivo.type },
      },
    );
    const solicitud = desempaquetar(respuestaSolicitud, 'no se pudo solicitar la URL de subida');
    if (!solicitud.url || !solicitud.objectKey) {
      throw new ErrorHttp(respuestaSolicitud.response.status, 'la URL de subida llegó incompleta');
    }

    const respuestaSubida = await fetch(solicitud.url, {
      method: 'PUT',
      headers: { 'Content-Type': archivo.type },
      body: archivo,
    });
    if (!respuestaSubida.ok) {
      throw new Error('No se pudo subir la foto a Cloud Storage.');
    }

    const respuesta = await this.cliente.POST('/api/v1/admin/borradores/{id}/fotos', {
      params: { path: { id } },
      body: { objectKey: solicitud.objectKey },
    });
    return aFoto(desempaquetar(respuesta, 'no se pudo confirmar la foto del borrador'));
  }

  async eliminar(id: string): Promise<void> {
    const respuesta = await this.cliente.DELETE('/api/v1/admin/borradores/{id}', {
      params: { path: { id } },
    });
    exigirExito(respuesta, 'no se pudo borrar el borrador');
  }
}
