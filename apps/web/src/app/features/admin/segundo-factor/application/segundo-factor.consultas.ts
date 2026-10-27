import { inject } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Sesion } from '../../../../core/autenticacion/sesion.model';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { REPOSITORIO_SEGUNDO_FACTOR } from '../domain/repositorio-segundo-factor.puerto';
import { SesionConCodigos } from '../domain/segundo-factor.model';

export const CLAVE_SEGUNDO_FACTOR = ['admin', 'segundo-factor'] as const;

/**
 * El estado del segundo factor de la cuenta. `staleTime` cero: es una pantalla que se abre poco y
 * justo para saber cuántos códigos de respaldo quedan, y un número viejo ahí es peor que una
 * petición de más.
 */
export function usarEstadoSegundoFactor() {
  const repositorio = inject(REPOSITORIO_SEGUNDO_FACTOR);
  return injectQuery(() => ({
    queryKey: CLAVE_SEGUNDO_FACTOR,
    queryFn: () => repositorio.consultar(),
    staleTime: 0,
  }));
}

export function usarIniciarActivacion() {
  const repositorio = inject(REPOSITORIO_SEGUNDO_FACTOR);
  return injectMutation(() => ({
    mutationFn: (claveActual: string) => repositorio.iniciarActivacion(claveActual),
  }));
}

/**
 * Las tres escrituras que terminan con una sesión nueva. El servidor revocó todas las anteriores
 * —la de esta pestaña incluida— y abrió otra en el mismo acto, así que se adopta **antes** de
 * devolver el control a la pantalla: con el token viejo, la siguiente petición del panel daría 401.
 * Después de activar, además, el token nuevo deja de ser el restringido.
 */
function alAbrirSesion(sesionStore: SesionStore, queryClient: QueryClient) {
  return (sesion: Sesion) => {
    sesionStore.adoptar(sesion);
    void queryClient.invalidateQueries({ queryKey: CLAVE_SEGUNDO_FACTOR });
  };
}

export function usarConfirmarActivacion() {
  const repositorio = inject(REPOSITORIO_SEGUNDO_FACTOR);
  const adoptar = alAbrirSesion(inject(SesionStore), inject(QueryClient));
  return injectMutation(() => ({
    mutationFn: (codigo: string) => repositorio.confirmarActivacion(codigo),
    onSuccess: (resultado: SesionConCodigos) => adoptar(resultado.sesion),
  }));
}

export function usarRegenerarCodigos() {
  const repositorio = inject(REPOSITORIO_SEGUNDO_FACTOR);
  const adoptar = alAbrirSesion(inject(SesionStore), inject(QueryClient));
  return injectMutation(() => ({
    mutationFn: ({ claveActual, codigo }: { claveActual: string; codigo: string }) =>
      repositorio.regenerarCodigos(claveActual, codigo),
    onSuccess: (resultado: SesionConCodigos) => adoptar(resultado.sesion),
  }));
}

export function usarDesactivar() {
  const repositorio = inject(REPOSITORIO_SEGUNDO_FACTOR);
  const adoptar = alAbrirSesion(inject(SesionStore), inject(QueryClient));
  return injectMutation(() => ({
    mutationFn: ({ claveActual, codigo }: { claveActual: string; codigo: string }) =>
      repositorio.desactivar(claveActual, codigo),
    onSuccess: (sesion: Sesion) => adoptar(sesion),
  }));
}
