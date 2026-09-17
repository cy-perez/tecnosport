import { InjectionToken } from '@angular/core';
import {
  AcuseDeRevision,
  BandejaDeRevision,
  EmisionResuelta,
  VeredictoDeEmision,
} from './revision-envio.model';

export interface RepositorioRevisionEnvios {
  /** Lo que pide ojo humano ahora mismo: guias quietas y emisiones sin desenredar. */
  listar(): Promise<BandejaDeRevision>;

  /** Por numero y no por id: es el numero el que se teclea y se busca en la transportadora. */
  acusarGuia(numeroGuia: string, nota: string | null): Promise<AcuseDeRevision>;

  acusarEmision(emisionId: string, nota: string | null): Promise<AcuseDeRevision>;

  /**
   * Desbloquea el pedido con lo que alguien vio en el panel de la plataforma. Distinto de
   * acusar: acusar deja constancia, esto cambia el estado de la emision.
   */
  resolverEmision(entrada: {
    emisionId: string;
    veredicto: VeredictoDeEmision;
    enviosEnPlataforma: readonly string[];
    nota: string | null;
  }): Promise<EmisionResuelta>;
}

export const REPOSITORIO_REVISION_ENVIOS = new InjectionToken<RepositorioRevisionEnvios>(
  'RepositorioRevisionEnvios',
);
