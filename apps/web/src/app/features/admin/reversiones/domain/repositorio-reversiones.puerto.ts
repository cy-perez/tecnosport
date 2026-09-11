import { InjectionToken } from '@angular/core';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import { CausalReversion, DesenlaceReversion, SolicitudReversion } from './reversion.model';

export interface RepositorioReversiones {
  listarDePedido(pedidoId: string): Promise<readonly SolicitudReversion[]>;

  radicar(entrada: {
    pedidoId: string;
    causal: CausalReversion;
    fechaDeNoticia: string;
    descripcion: string;
  }): Promise<SolicitudReversion>;

  registrarGestion(reversionId: string, gestion: string): Promise<SolicitudReversion>;

  /** `monto`, `medio` y `comprobante` solo viajan con `REINTEGRADO_DIRECTAMENTE`. */
  resolver(entrada: {
    reversionId: string;
    desenlace: DesenlaceReversion;
    resumenParaElComprador: string;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<SolicitudReversion>;
}

export const REPOSITORIO_REVERSIONES = new InjectionToken<RepositorioReversiones>(
  'RepositorioReversiones',
);
