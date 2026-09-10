import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { EstadoSolicitudAtencion, SolicitudAtencion } from '../domain/atencion.model';
import { REPOSITORIO_ATENCION } from '../domain/repositorio-atencion.puerto';

export function claveBandejaAtencion(estado: EstadoSolicitudAtencion | null) {
  return ['admin', 'atencion', estado ?? 'abiertas'] as const;
}

/**
 * `staleTime` corto a proposito: lo que la bandeja muestra es cuanto falta para que venza un plazo
 * legal, y ese dato envejece mientras alguien mira la pantalla.
 */
export function usarBandejaAtencion(estado: () => EstadoSolicitudAtencion | null) {
  const repositorio = inject(REPOSITORIO_ATENCION);

  return injectQuery(() => ({
    queryKey: claveBandejaAtencion(estado()),
    queryFn: (): Promise<readonly SolicitudAtencion[]> => repositorio.listar(estado()),
    staleTime: 15_000,
  }));
}
