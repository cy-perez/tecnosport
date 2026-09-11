import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import { CausalReversion, DesenlaceReversion, SolicitudReversion } from '../domain/reversion.model';
import { RepositorioReversiones } from '../domain/repositorio-reversiones.puerto';
import { aSolicitudReversion } from './mapeador-reversion';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer`. */
@Injectable()
export class ReversionesHttpRepositorio implements RepositorioReversiones {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listarDePedido(pedidoId: string): Promise<readonly SolicitudReversion[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/pedidos/{pedidoId}/reversiones', {
      params: { path: { pedidoId } },
    });
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las reversiones del pedido');
    return datos.map(aSolicitudReversion);
  }

  async radicar(entrada: {
    pedidoId: string;
    causal: CausalReversion;
    fechaDeNoticia: string;
    descripcion: string;
  }): Promise<SolicitudReversion> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{pedidoId}/reversiones', {
      params: { path: { pedidoId: entrada.pedidoId } },
      body: {
        causal: entrada.causal,
        fechaDeNoticia: entrada.fechaDeNoticia,
        descripcion: entrada.descripcion,
      },
    });
    return aSolicitudReversion(desempaquetar(respuesta, 'no se pudo radicar la reversion'));
  }

  async registrarGestion(reversionId: string, gestion: string): Promise<SolicitudReversion> {
    const respuesta = await this.cliente.POST('/api/v1/admin/reversiones/{id}/gestion', {
      params: { path: { id: reversionId } },
      body: { gestion },
    });
    return aSolicitudReversion(desempaquetar(respuesta, 'no se pudo registrar la gestion'));
  }

  async resolver(entrada: {
    reversionId: string;
    desenlace: DesenlaceReversion;
    resumenParaElComprador: string;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<SolicitudReversion> {
    const respuesta = await this.cliente.POST('/api/v1/admin/reversiones/{id}/resolucion', {
      params: { path: { id: entrada.reversionId } },
      body: {
        desenlace: entrada.desenlace,
        resumenParaElComprador: entrada.resumenParaElComprador,
        monto: entrada.monto ?? undefined,
        medio: entrada.medio ?? undefined,
        comprobante: entrada.comprobante ?? undefined,
      },
    });
    return aSolicitudReversion(desempaquetar(respuesta, 'no se pudo resolver la reversion'));
  }
}
