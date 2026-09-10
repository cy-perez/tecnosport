import { InjectionToken } from '@angular/core';
import { MedioReembolso, SolicitudRetracto } from './retracto.model';

export interface RepositorioRetractos {
  listarDePedido(pedidoId: string): Promise<readonly SolicitudRetracto[]>;

  radicar(pedidoId: string, motivo: string | null): Promise<SolicitudRetracto>;

  recibirProducto(solicitudId: string): Promise<SolicitudRetracto>;

  registrarReembolso(
    solicitudId: string,
    monto: number,
    medio: MedioReembolso,
    comprobante: string | null,
  ): Promise<SolicitudRetracto>;
}

export const REPOSITORIO_RETRACTOS = new InjectionToken<RepositorioRetractos>(
  'RepositorioRetractos',
);
