import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import {
  AprobarBorradorTecnologia,
  BorradorTecnologia,
  EleccionDeConfiguracion,
} from '../domain/borrador-tecnologia.model';
import { REPOSITORIO_BORRADORES_TECNOLOGIA } from '../domain/repositorio-borradores-tecnologia.puerto';
import { CLAVE_BORRADORES_TECNOLOGIA } from './borradores-tecnologia.consulta';

/** Lo que el panel ya tiene cacheado de productos, para que el recién aprobado aparezca. */
const CLAVE_PRODUCTOS_ADMIN = ['admin', 'productos'] as const;

export interface ElegirComando {
  readonly id: string;
  readonly elecciones: readonly EleccionDeConfiguracion[];
}

/**
 * Aprobar guarda primero lo que está en pantalla y después aprueba: así el botón de aprobar no
 * depende de que alguien haya pulsado antes «Guardar». Son dos peticiones, y si la segunda falla
 * la elección ya quedó guardada, que es lo que se quiere.
 */
export interface AprobarComando extends ElegirComando {
  readonly aprobacion: AprobarBorradorTecnologia;
}

export interface RechazarComando {
  readonly id: string;
  readonly motivo: string;
}

/**
 * Las decisiones sobre un borrador de tecnología, juntas porque invalidan lo mismo: el prefijo
 * entero, que cubre las listas por estado y el detalle del tocado.
 */
export function usarElegirConfiguraciones() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: ElegirComando): Promise<BorradorTecnologia> =>
      repositorio.elegir(comando.id, comando.elecciones),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_TECNOLOGIA }),
  }));
}

/** Devuelve el id del producto. Invalida también la lista de productos del panel. */
export function usarAprobarBorradorTecnologia() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: async (comando: AprobarComando): Promise<string> => {
      await repositorio.elegir(comando.id, comando.elecciones);
      return repositorio.aprobar(comando.id, comando.aprobacion);
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_TECNOLOGIA });
      void queryClient.invalidateQueries({ queryKey: CLAVE_PRODUCTOS_ADMIN });
    },
  }));
}

export function usarRechazarBorradorTecnologia() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: RechazarComando): Promise<BorradorTecnologia> =>
      repositorio.rechazar(comando.id, comando.motivo),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_TECNOLOGIA }),
  }));
}
