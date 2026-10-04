import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { DatosProveedor, Proveedor } from '../domain/proveedor.model';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../domain/repositorio-proveedores-admin.puerto';
import { CLAVE_PROVEEDORES_ADMIN } from './listar-proveedores.consulta';

export function usarCrearProveedor() {
  const repositorio = inject(REPOSITORIO_PROVEEDORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (datos: DatosProveedor): Promise<Proveedor> => repositorio.crear(datos),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CLAVE_PROVEEDORES_ADMIN }),
  }));
}

export interface EditarProveedorComando {
  readonly id: string;
  readonly datos: DatosProveedor;
}

/** Invalida el prefijo entero: la lista y la ficha del editado comparten raíz de llave. */
export function usarEditarProveedor() {
  const repositorio = inject(REPOSITORIO_PROVEEDORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: EditarProveedorComando): Promise<Proveedor> =>
      repositorio.editar(comando.id, comando.datos),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CLAVE_PROVEEDORES_ADMIN }),
  }));
}

/**
 * Lo eliminado ya no tiene ficha: se quita su consulta en vez de invalidarla, porque invalidarla la
 * volvería a pedir con la pantalla todavía abierta y respondería un 404 antes de salir a la lista.
 */
export function usarEliminarProveedor() {
  const repositorio = inject(REPOSITORIO_PROVEEDORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<void> => repositorio.eliminar(id),
    onSuccess: (_: void, id: string) => {
      queryClient.removeQueries({ queryKey: [...CLAVE_PROVEEDORES_ADMIN, id], exact: true });
      void queryClient.invalidateQueries({ queryKey: CLAVE_PROVEEDORES_ADMIN });
    },
  }));
}
