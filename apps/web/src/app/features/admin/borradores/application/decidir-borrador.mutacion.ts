import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { AprobarBorrador, Borrador, EditarBorrador, FotoBorrador } from '../domain/borrador.model';
import { REPOSITORIO_BORRADORES_ADMIN } from '../domain/repositorio-borradores-admin.puerto';
import { CLAVE_BORRADORES_ADMIN } from './listar-borradores.consulta';

/** Lo que el panel ya tiene cacheado de productos, para que el recién aprobado aparezca. */
const CLAVE_PRODUCTOS_ADMIN = ['admin', 'productos'] as const;

export interface EditarBorradorComando {
  readonly id: string;
  readonly cambios: EditarBorrador;
}

export interface AprobarBorradorComando {
  readonly id: string;
  readonly aprobacion: AprobarBorrador;
}

export interface DescartarFotoComando {
  readonly id: string;
  readonly mensajeId: string;
}

export interface SubirFotoComando {
  readonly id: string;
  readonly archivo: File;
}

export interface RechazarBorradorComando {
  readonly id: string;
  readonly motivo: string;
}

/**
 * Las decisiones sobre un borrador, en un solo archivo porque invalidan lo mismo: el prefijo de
 * borradores entero, que cubre la lista con cualquier filtro y el detalle del tocado.
 */
export function usarEditarBorrador() {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: EditarBorradorComando): Promise<Borrador> =>
      repositorio.editar(comando.id, comando.cambios),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_ADMIN }),
  }));
}

/** Aprobar crea un producto: se invalida también la lista de productos del panel. */
export function usarAprobarBorrador() {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: AprobarBorradorComando): Promise<Borrador> =>
      repositorio.aprobar(comando.id, comando.aprobacion),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_ADMIN });
      void queryClient.invalidateQueries({ queryKey: CLAVE_PRODUCTOS_ADMIN });
    },
  }));
}

export function usarRechazarBorrador() {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: RechazarBorradorComando): Promise<Borrador> =>
      repositorio.rechazar(comando.id, comando.motivo),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_ADMIN }),
  }));
}

/** Descartar una foto cambia el detalle: se vuelve a pedir, ya sin ella. */
export function usarDescartarFotoBorrador() {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: DescartarFotoComando): Promise<void> =>
      repositorio.descartarFoto(comando.id, comando.mensajeId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_ADMIN }),
  }));
}

/**
 * Una foto por llamada: la pantalla sube las elegidas una tras otra para decir cuál falló. Cada una
 * invalida el detalle, que vuelve con ella y con las alertas ya sin `SIN_FOTOS`.
 */
export function usarSubirFotoBorrador() {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: SubirFotoComando): Promise<FotoBorrador> =>
      repositorio.subirFoto(comando.id, comando.archivo),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_ADMIN }),
  }));
}

/**
 * Borrar saca el detalle de la caché en vez de invalidarlo: invalidarlo lo volvería a pedir con la
 * pantalla todavía abierta, y la respuesta sería un 404 pintado antes de salir hacia la lista.
 */
export function usarEliminarBorrador() {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<void> => repositorio.eliminar(id),
    onSuccess: (_: void, id: string) => {
      const detalle = [...CLAVE_BORRADORES_ADMIN, id];
      queryClient.removeQueries({ queryKey: detalle, exact: true });
      void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_ADMIN });
    },
  }));
}
