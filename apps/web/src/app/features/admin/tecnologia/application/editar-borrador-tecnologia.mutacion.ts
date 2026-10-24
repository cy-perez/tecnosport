import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { BorradorTecnologia, FotoNuevaDeBorrador } from '../domain/borrador-tecnologia.model';
import { REPOSITORIO_BORRADORES_TECNOLOGIA } from '../domain/repositorio-borradores-tecnologia.puerto';
import { CLAVE_BORRADORES_TECNOLOGIA } from './borradores-tecnologia.consulta';

/**
 * Lo que quien revisa edita del borrador antes de aprobar (23/10/2026): las fotos y la ficha. Todo
 * invalida el prefijo entero, que cubre el detalle y la bandeja, donde se ve cuántas fotos tiene.
 */

export interface SubirFotoComando {
  readonly id: string;
  readonly foto: FotoNuevaDeBorrador;
}

export interface QuitarFotoComando {
  readonly id: string;
  readonly fotoId: string;
}

export interface ReordenarFotosComando {
  readonly id: string;
  readonly fotoIds: readonly string[];
}

export interface EditarFichaComando {
  readonly id: string;
  /** `null` vuelve a la ficha de la skill. */
  readonly descripcion: string | null;
}

function invalidar(queryClient: QueryClient): void {
  void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_TECNOLOGIA });
}

export function usarSubirFotoBorradorTecnologia() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (comando: SubirFotoComando): Promise<BorradorTecnologia> =>
      repositorio.subirFoto(comando.id, comando.foto),
    onSuccess: () => invalidar(queryClient),
  }));
}

export function usarQuitarFotoBorradorTecnologia() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (comando: QuitarFotoComando): Promise<void> =>
      repositorio.quitarFoto(comando.id, comando.fotoId),
    onSuccess: () => invalidar(queryClient),
  }));
}

export function usarReordenarFotosBorradorTecnologia() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (comando: ReordenarFotosComando): Promise<BorradorTecnologia> =>
      repositorio.reordenarFotos(comando.id, comando.fotoIds),
    onSuccess: () => invalidar(queryClient),
  }));
}

export function usarEditarFichaBorradorTecnologia() {
  const repositorio = inject(REPOSITORIO_BORRADORES_TECNOLOGIA);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (comando: EditarFichaComando): Promise<BorradorTecnologia> =>
      repositorio.editarFicha(comando.id, comando.descripcion),
    onSuccess: () => invalidar(queryClient),
  }));
}
