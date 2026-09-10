import { inject, Injectable } from '@angular/core';
import { SesionStore } from '../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../core/http/cliente-autenticado';
import { desempaquetar, exigirExito } from '../../../core/http/respuesta-http';
import {
  AbrirSetRotacion,
  FotogramaSubido,
  RepositorioSetsRotacion,
  SetRotacionAdmin,
  SubidaDeFotograma,
} from '../domain/repositorio-sets-rotacion.puerto';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer`. */
@Injectable()
export class SetsRotacionHttpRepositorio implements RepositorioSetsRotacion {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async abrir(comando: AbrirSetRotacion): Promise<SetRotacionAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/sets-rotacion', {
      body: {
        productoId: comando.productoId,
        fotogramas: comando.fotogramas,
        dispositivo: comando.dispositivo,
        versionAsistente: comando.versionAsistente,
      },
    });
    return aSetRotacion(desempaquetar(respuesta, 'no se pudo abrir el set de rotación'));
  }

  async urlsDeSubida(setId: string, contentType: string): Promise<SubidaDeFotograma[]> {
    const respuesta = await this.cliente.POST('/api/v1/admin/sets-rotacion/{id}/subidas', {
      params: { path: { id: setId } },
      body: { contentType },
    });
    const datos = desempaquetar(respuesta, 'no se pudieron pedir las URL de subida');
    return datos.map((subida) => ({
      orden: subida.orden ?? 0,
      url: subida.url ?? '',
      objectKey: subida.objectKey ?? '',
    }));
  }

  /**
   * El `PUT` va con `fetch` a pelo y no con el cliente autenticado: la URL es de Cloud Storage y
   * ya lleva su firma en los parámetros. Mandarle la cabecera `Authorization` del panel sería
   * filtrar el token del administrador a un tercero — y además rompería la firma.
   *
   * Mismo camino que ya usa la subida de la imagen principal.
   */
  async subirFotograma(url: string, imagen: Blob): Promise<void> {
    const respuesta = await fetch(url, {
      method: 'PUT',
      headers: { 'Content-Type': imagen.type },
      body: imagen,
    });
    if (!respuesta.ok) {
      throw new Error('No se pudo subir el fotograma a Cloud Storage.');
    }
  }

  async completar(
    setId: string,
    fotogramas: readonly FotogramaSubido[],
  ): Promise<SetRotacionAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/sets-rotacion/{id}/completar', {
      params: { path: { id: setId } },
      body: {
        fotogramas: fotogramas.map((fotograma) => ({
          orden: fotograma.orden,
          objectKey: fotograma.objectKey,
          ancho: fotograma.ancho,
          alto: fotograma.alto,
          hash: fotograma.hash,
        })),
      },
    });
    return aSetRotacion(desempaquetar(respuesta, 'no se pudo completar el set'));
  }

  async publicar(setId: string): Promise<SetRotacionAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/sets-rotacion/{id}/publicar', {
      params: { path: { id: setId } },
    });
    return aSetRotacion(desempaquetar(respuesta, 'no se pudo publicar el set'));
  }

  async eliminar(setId: string): Promise<void> {
    const respuesta = await this.cliente.DELETE('/api/v1/admin/sets-rotacion/{id}', {
      params: { path: { id: setId } },
    });
    exigirExito(respuesta, 'no se pudo borrar el set');
  }
}

interface SetRotacionDto {
  id?: string;
  productoId?: string;
  fotogramasPrometidos?: number;
  estado?: string;
  imagenes?: { orden?: number; urlWebp?: string }[];
}

/** El modelo del front es del front: el DTO generado no sale de infrastructure. */
function aSetRotacion(dto: SetRotacionDto): SetRotacionAdmin {
  return {
    id: dto.id ?? '',
    productoId: dto.productoId ?? '',
    fotogramasPrometidos: dto.fotogramasPrometidos ?? 0,
    estado: (dto.estado ?? 'BORRADOR') as SetRotacionAdmin['estado'],
    imagenes: (dto.imagenes ?? []).map((imagen) => ({
      orden: imagen.orden ?? 0,
      urlWebp: imagen.urlWebp ?? '',
    })),
  };
}
