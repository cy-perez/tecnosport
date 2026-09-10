import { InjectionToken } from '@angular/core';
import { EstadoSolicitudAtencion, SolicitudAtencion, TipoSolicitud } from './atencion.model';

export interface RepositorioAtencion {
  /** Sin estado trae lo abierto, que es lo que la bandeja muestra por omision. */
  listar(estado: EstadoSolicitudAtencion | null): Promise<readonly SolicitudAtencion[]>;

  radicar(entrada: {
    tipo: TipoSolicitud;
    correo: string;
    pedidoId: string | null;
    recibidaEn: string | null;
    asunto: string;
  }): Promise<SolicitudAtencion>;

  responder(solicitudId: string, resumen: string): Promise<SolicitudAtencion>;

  prorrogar(solicitudId: string, motivo: string): Promise<SolicitudAtencion>;
}

export const REPOSITORIO_ATENCION = new InjectionToken<RepositorioAtencion>('RepositorioAtencion');
