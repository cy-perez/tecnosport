import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_REVISION_ENVIOS } from '../domain/repositorio-revision-envios.puerto';
import { BandejaDeRevision } from '../domain/revision-envio.model';

export function claveBandejaRevision() {
  return ['admin', 'envios', 'revision'] as const;
}

/**
 * `staleTime` corto, mismo criterio que la bandeja de atencion: lo que se muestra es un paquete
 * detenido, y la transportadora puede moverlo mientras alguien mira la pantalla.
 */
export function usarBandejaRevision() {
  const repositorio = inject(REPOSITORIO_REVISION_ENVIOS);

  return injectQuery(() => ({
    queryKey: claveBandejaRevision(),
    queryFn: (): Promise<BandejaDeRevision> => repositorio.listar(),
    staleTime: 15_000,
  }));
}
