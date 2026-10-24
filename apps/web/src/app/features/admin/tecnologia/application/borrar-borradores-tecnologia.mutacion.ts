import { inject } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_BORRADORES_TECNOLOGIA } from '../domain/repositorio-borradores-tecnologia.puerto';
import { CLAVE_BORRADORES_TECNOLOGIA } from './borradores-tecnologia.consulta';

/**
 * Borrar borradores de tecnología (24/10/2026): uno desde su revisión, o todos los sin aprobar desde
 * la bandeja. Es la forma de rehacer una tanda desde cero; rechazar no, porque un rechazado no se
 * vuelve a proponer. Los aprobados no se borran.
 *
 * La cuenta vive bajo el prefijo de los borradores: toda decisión sobre uno ya lo invalida, y la
 * cuenta tiene que moverse con ella.
 */
export const CLAVE_BORRADORES_TECNOLOGIA_SIN_APROBAR = [
  ...CLAVE_BORRADORES_TECNOLOGIA,
  'sin-aprobar',
] as const;

/** Cuántos borraría el botón de la bandeja: se dice antes de confirmar. */
export function usarContarBorradoresTecnologiaSinAprobar() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  return injectQuery(() => ({
    queryKey: CLAVE_BORRADORES_TECNOLOGIA_SIN_APROBAR,
    queryFn: (): Promise<number> => repositorio.contarSinAprobar(),
    staleTime: 15_000,
  }));
}

export interface BorrarSinAprobarComando {
  /** Después de cada tanda, para que la pantalla diga cuánto lleva. */
  readonly alAvanzar?: (borrados: number, quedan: number) => void;
}

/**
 * Pide tandas hasta que no quede ninguno y devuelve cuántos se borraron. Se detiene también si una
 * tanda no borra nada: los que quedan los está decidiendo alguien más, y repetir no terminaría.
 * Invalida al terminar, también si falla a mitad: lo que alcanzó a borrarse ya no está.
 */
export function usarBorrarBorradoresTecnologiaSinAprobar() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: async (comando: BorrarSinAprobarComando = {}): Promise<number> => {
      let borrados = 0;
      for (;;) {
        const tanda = await repositorio.eliminarSinAprobar();
        borrados += tanda.eliminados;
        comando.alAvanzar?.(borrados, tanda.quedan);
        if (tanda.quedan === 0 || tanda.eliminados === 0) {
          return borrados;
        }
      }
    },
    onSettled: () => void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_TECNOLOGIA }),
  }));
}

/**
 * Borra uno. Su detalle sale de la caché en vez de revalidarse: ya no existe, y pedirlo daría un
 * 404 mientras la pantalla navega a la bandeja.
 */
export function usarEliminarBorradorTecnologia() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (id: string): Promise<void> => repositorio.eliminar(id),
    onSuccess: (_: void, id: string) => {
      queryClient.removeQueries({ queryKey: [...CLAVE_BORRADORES_TECNOLOGIA, id], exact: true });
      void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_TECNOLOGIA });
    },
  }));
}
