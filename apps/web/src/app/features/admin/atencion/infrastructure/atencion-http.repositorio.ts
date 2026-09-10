import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { EstadoSolicitudAtencion, SolicitudAtencion, TipoSolicitud } from '../domain/atencion.model';
import { RepositorioAtencion } from '../domain/repositorio-atencion.puerto';
import { aSolicitudAtencion } from './mapeador-atencion';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer`. */
@Injectable()
export class AtencionHttpRepositorio implements RepositorioAtencion {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(estado: EstadoSolicitudAtencion | null): Promise<readonly SolicitudAtencion[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/atencion', {
      params: { query: estado ? { estado } : {} },
    });
    const datos = desempaquetar(respuesta, 'no se pudo cargar la bandeja de atencion');
    return datos.map(aSolicitudAtencion);
  }

  async radicar(entrada: {
    tipo: TipoSolicitud;
    correo: string;
    pedidoId: string | null;
    recibidaEn: string | null;
    asunto: string;
  }): Promise<SolicitudAtencion> {
    const respuesta = await this.cliente.POST('/api/v1/admin/atencion', {
      body: {
        tipo: entrada.tipo,
        correo: entrada.correo,
        pedidoId: entrada.pedidoId ?? undefined,
        recibidaEn: entrada.recibidaEn ?? undefined,
        asunto: entrada.asunto,
      },
    });
    return aSolicitudAtencion(desempaquetar(respuesta, 'no se pudo radicar la solicitud'));
  }

  async responder(solicitudId: string, resumen: string): Promise<SolicitudAtencion> {
    const respuesta = await this.cliente.POST('/api/v1/admin/atencion/{id}/respuesta', {
      params: { path: { id: solicitudId } },
      body: { resumen },
    });
    return aSolicitudAtencion(desempaquetar(respuesta, 'no se pudo registrar la respuesta'));
  }

  async prorrogar(solicitudId: string, motivo: string): Promise<SolicitudAtencion> {
    const respuesta = await this.cliente.POST('/api/v1/admin/atencion/{id}/prorroga', {
      params: { path: { id: solicitudId } },
      body: { motivo },
    });
    return aSolicitudAtencion(desempaquetar(respuesta, 'no se pudo prorrogar el plazo'));
  }
}
