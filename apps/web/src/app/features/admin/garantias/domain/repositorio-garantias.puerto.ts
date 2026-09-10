import { InjectionToken } from '@angular/core';
import { DesenlaceGarantia, ReclamacionGarantia } from './garantia.model';
import { MedioReintegro } from '../../retractos/domain/retracto.model';

export interface RepositorioGarantias {
  listarDePedido(pedidoId: string): Promise<readonly ReclamacionGarantia[]>;

  radicar(
    pedidoId: string,
    varianteId: string,
    descripcionDelFallo: string,
  ): Promise<ReclamacionGarantia>;

  /** `monto`, `medio` y `comprobante` solo viajan con desenlace `REINTEGRO`. */
  resolver(entrada: {
    reclamacionId: string;
    desenlace: DesenlaceGarantia;
    resumenParaElComprador: string;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<ReclamacionGarantia>;
}

export const REPOSITORIO_GARANTIAS = new InjectionToken<RepositorioGarantias>(
  'RepositorioGarantias',
);
