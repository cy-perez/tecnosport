import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { sha256Hex } from '../../../../core/hash/sha256';
import { desempaquetar, ErrorHttp, exigirExito } from '../../../../core/http/respuesta-http';
import {
  AprobarBorradorTecnologia,
  BorradorTecnologia,
  EleccionDeConfiguracion,
  EstadoBorradorTecnologia,
  FotoNuevaDeBorrador,
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

  /** Mismos tres pasos que la galería de un producto: URL firmada, subida directa y registro. */
  async subirFoto(id: string, foto: FotoNuevaDeBorrador): Promise<BorradorTecnologia> {
    const respuestaSolicitud = await this.cliente.POST(
      '/api/v1/admin/borradores-tecnologia/{id}/fotos/url-subida',
      { params: { path: { id } }, body: { contentType: foto.archivo.type } },
    );
    const solicitud = desempaquetar(respuestaSolicitud, 'no se pudo solicitar la URL de subida');
    if (!solicitud.url || !solicitud.objectKey) {
      throw new ErrorHttp(respuestaSolicitud.response.status, 'la URL de subida llegó incompleta');
    }

    const subida = await fetch(solicitud.url, {
      method: 'PUT',
      headers: { 'Content-Type': foto.archivo.type },
      body: foto.archivo,
    });
    if (!subida.ok) {
      throw new Error('No se pudo subir la foto a Cloud Storage.');
    }

    const respuesta = await this.cliente.POST('/api/v1/admin/borradores-tecnologia/{id}/fotos', {
      params: { path: { id } },
      body: {
        // Un solo ancho, el del archivo, y sin vista previa: la escalera la arma la skill.
        variantes: [{ ancho: foto.ancho, objectKey: solicitud.objectKey }],
        alto: foto.alto,
        hash: await sha256Hex(foto.archivo),
        altEs: foto.altEs,
        altEn: foto.altEn,
      },
    });
    return aBorradorTecnologia(desempaquetar(respuesta, 'no se pudo agregar la foto'));
  }

  async quitarFoto(id: string, fotoId: string): Promise<void> {
    const respuesta = await this.cliente.DELETE(
      '/api/v1/admin/borradores-tecnologia/{id}/fotos/{fotoId}',
      { params: { path: { id, fotoId } } },
    );
    exigirExito(respuesta, 'no se pudo quitar la foto');
  }

  async reordenarFotos(id: string, fotoIds: readonly string[]): Promise<BorradorTecnologia> {
    const respuesta = await this.cliente.PUT(
      '/api/v1/admin/borradores-tecnologia/{id}/fotos/orden',
      { params: { path: { id } }, body: { fotoIds: [...fotoIds] } },
    );
    return aBorradorTecnologia(desempaquetar(respuesta, 'no se pudo cambiar el orden'));
  }

  async editarFicha(id: string, descripcion: string | null): Promise<BorradorTecnologia> {
    const respuesta = await this.cliente.PUT(
      '/api/v1/admin/borradores-tecnologia/{id}/descripcion',
      { params: { path: { id } }, body: descripcion === null ? {} : { descripcion } },
    );
    return aBorradorTecnologia(desempaquetar(respuesta, 'no se pudo guardar la ficha'));
  }
}
