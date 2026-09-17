import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { RepositorioRevisionEnvios } from '../domain/repositorio-revision-envios.puerto';
import { AcuseDeRevision, BandejaDeRevision } from '../domain/revision-envio.model';
import { aAcuseDeRevision, aBandejaDeRevision } from './mapeador-revision-envio';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer`. */
@Injectable()
export class RevisionEnviosHttpRepositorio implements RepositorioRevisionEnvios {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(): Promise<BandejaDeRevision> {
    const respuesta = await this.cliente.GET('/api/v1/admin/envios/revision', {});
    return aBandejaDeRevision(
      desempaquetar(respuesta, 'no se pudo cargar la bandeja de revision de envios'),
    );
  }

  async acusarGuia(numeroGuia: string, nota: string | null): Promise<AcuseDeRevision> {
    const respuesta = await this.cliente.POST(
      '/api/v1/admin/envios/revision/guias/{numeroGuia}/acuse',
      {
        params: { path: { numeroGuia } },
        body: { nota: nota ?? undefined },
      },
    );
    return aAcuseDeRevision(desempaquetar(respuesta, 'no se pudo marcar la guia como revisada'));
  }

  async acusarEmision(emisionId: string, nota: string | null): Promise<AcuseDeRevision> {
    const respuesta = await this.cliente.POST(
      '/api/v1/admin/envios/revision/emisiones/{emisionId}/acuse',
      {
        params: { path: { emisionId } },
        body: { nota: nota ?? undefined },
      },
    );
    return aAcuseDeRevision(desempaquetar(respuesta, 'no se pudo marcar la emision como revisada'));
  }
}
