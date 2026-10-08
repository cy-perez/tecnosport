import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { ReferenciasDeEnvio } from '../domain/referencias-envio.model';
import { REPOSITORIO_REFERENCIAS_ENVIO } from '../domain/repositorio-referencias-envio.puerto';

export function claveReferenciasEnvio() {
  return ['admin', 'envios', 'referencias'] as const;
}

/**
 * `staleTime` largo a propósito, al revés que la bandeja de revisión: esto es configuración del
 * negocio, cambia cuando alguien la cambia en esta misma pantalla, y cada guardado invalida la
 * consulta.
 */
export function usarReferenciasEnvio() {
  const repositorio = inject(REPOSITORIO_REFERENCIAS_ENVIO);

  return injectQuery(() => ({
    queryKey: claveReferenciasEnvio(),
    queryFn: (): Promise<ReferenciasDeEnvio> => repositorio.consultar(),
    staleTime: 5 * 60_000,
  }));
}
