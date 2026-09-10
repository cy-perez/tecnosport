import { InjectionToken } from '@angular/core';
import { MedioReintegro, SolicitudRetracto } from './retracto.model';

export interface RepositorioRetractos {
  listarDePedido(pedidoId: string): Promise<readonly SolicitudRetracto[]>;

  radicar(
    pedidoId: string,
    motivo: string | null,
    medioPreferido: MedioReintegro | null,
  ): Promise<SolicitudRetracto>;

  recibirProducto(solicitudId: string): Promise<SolicitudRetracto>;

  /**
   * {@code medioPreferido} solo se manda cuando la solicitud no lo traía: el comprador suele
   * decirlo al enviar los datos de la cuenta, o sea después de radicar. Ya anotado, el backend se
   * niega a cambiarlo.
   */
  registrarReintegro(
    solicitudId: string,
    monto: number,
    medio: MedioReintegro,
    medioPreferido: MedioReintegro | null,
    comprobante: string | null,
  ): Promise<SolicitudRetracto>;
}

export const REPOSITORIO_RETRACTOS = new InjectionToken<RepositorioRetractos>(
  'RepositorioRetractos',
);
