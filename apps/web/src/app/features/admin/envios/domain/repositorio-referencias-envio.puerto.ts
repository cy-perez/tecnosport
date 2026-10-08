import { InjectionToken } from '@angular/core';
import { MedidasDeReferencia, ReferenciasDeEnvio } from './referencias-envio.model';

export interface RepositorioReferenciasEnvio {
  /** Las medidas de la bolsa y cada categoría hoja de ropa, calzado y bolsos, con o sin peso. */
  consultar(): Promise<ReferenciasDeEnvio>;

  fijarMedidas(medidas: MedidasDeReferencia): Promise<MedidasDeReferencia>;

  fijarPeso(categoriaId: string, pesoGramos: number): Promise<void>;

  /** Sus productos sin medir vuelven a venderse solo con recogida. */
  quitarPeso(categoriaId: string): Promise<void>;
}

export const REPOSITORIO_REFERENCIAS_ENVIO = new InjectionToken<RepositorioReferenciasEnvio>(
  'RepositorioReferenciasEnvio',
);
