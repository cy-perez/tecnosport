import { inject } from '@angular/core';
import { QueryClient, injectMutation } from '@tanstack/angular-query-experimental';
import { SolicitudAtencion, TipoSolicitud } from '../domain/atencion.model';
import { REPOSITORIO_ATENCION } from '../domain/repositorio-atencion.puerto';

/**
 * Cada accion invalida el prefijo entero `['admin', 'atencion']` y no solo la clave del filtro
 * activo: responder saca la solicitud de la bandeja de abiertas y la mete en la de respondidas, asi
 * que la lista que no se esta mirando tambien quedo vieja.
 */
export function usarAccionesAtencion() {
  const repositorio = inject(REPOSITORIO_ATENCION);
  const queryClient = inject(QueryClient);

  const invalidar = () => queryClient.invalidateQueries({ queryKey: ['admin', 'atencion'] });

  const radicar = injectMutation(() => ({
    mutationFn: (variables: {
      tipo: TipoSolicitud;
      correo: string;
      pedidoId: string | null;
      recibidaEn: string | null;
      asunto: string;
    }): Promise<SolicitudAtencion> => repositorio.radicar(variables),
    onSuccess: invalidar,
  }));

  const responder = injectMutation(() => ({
    mutationFn: (variables: { solicitudId: string; resumen: string }): Promise<SolicitudAtencion> =>
      repositorio.responder(variables.solicitudId, variables.resumen),
    onSuccess: invalidar,
  }));

  const prorrogar = injectMutation(() => ({
    mutationFn: (variables: { solicitudId: string; motivo: string }): Promise<SolicitudAtencion> =>
      repositorio.prorrogar(variables.solicitudId, variables.motivo),
    onSuccess: invalidar,
  }));

  return { radicar, responder, prorrogar };
}
