import { InjectionToken } from '@angular/core';
import { MedioReintegro, SolicitudRetracto } from './retracto.model';

export interface RepositorioRetractos {
  listarDePedido(pedidoId: string): Promise<readonly SolicitudRetracto[]>;

  radicar(pedidoId: string, motivo: string | null): Promise<SolicitudRetracto>;

  recibirProducto(solicitudId: string): Promise<SolicitudRetracto>;

  registrarReintegro(
    solicitudId: string,
    monto: number,
    medio: MedioReintegro,
    comprobante: string | null,
  ): Promise<SolicitudRetracto>;
}

export const REPOSITORIO_RETRACTOS = new InjectionToken<RepositorioRetractos>(
  'RepositorioRetractos',
);
