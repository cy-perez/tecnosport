import { Injectable, inject } from '@angular/core';
import { components } from '@tecnosport/contratos';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar, exigirExito } from '../../../../core/http/respuesta-http';
import { ArchivoDeIngesta, ArchivosDeIngestaPaginados } from '../domain/archivo-de-ingesta.model';
import { RepositorioArchivosDeIngesta } from '../domain/repositorio-archivos-de-ingesta.puerto';

type ArchivoDto = components['schemas']['ArchivoDeIngestaRespuesta'];

const TAMANO_PAGINA = 20;

export function aArchivoDeIngesta(dto: ArchivoDto): ArchivoDeIngesta {
  return {
    id: dto.id ?? '',
    proveedorId: dto.proveedorId ?? '',
    nombreOriginal: dto.nombreOriginal ?? null,
    tamanoBytes: dto.tamanoBytes ?? null,
    subidoEn: dto.subidoEn ?? '',
    borradoEn: dto.borradoEn ?? null,
    lotes: dto.lotes ?? 0,
    enUso: dto.enUso ?? false,
  };
}

@Injectable()
export class ArchivosDeIngestaHttpRepositorio implements RepositorioArchivosDeIngesta {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(pagina: number): Promise<ArchivosDeIngestaPaginados> {
    const respuesta = await this.cliente.GET('/api/v1/admin/ingestas/archivos', {
      params: { query: { pagina, tamano: TAMANO_PAGINA } },
    });
    const dto = desempaquetar(respuesta, 'no se pudo cargar el historial de archivos');
    return {
      items: (dto.items ?? []).map(aArchivoDeIngesta),
      pagina: dto.pagina ?? 0,
      totalPaginas: dto.totalPaginas ?? 0,
      totalArchivos: dto.totalArchivos ?? 0,
    };
  }

  async borrar(id: string): Promise<void> {
    const respuesta = await this.cliente.DELETE('/api/v1/admin/ingestas/archivos/{id}', {
      params: { path: { id } },
    });
    exigirExito(respuesta, 'no se pudo borrar el archivo');
  }
}
