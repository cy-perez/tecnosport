import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import {
  CambioDeCategoria,
  NuevaCategoria,
  REPOSITORIO_CATEGORIAS_ADMIN,
  ResultadoEscritura,
} from '../domain/repositorio-categorias-admin.puerto';
import { CLAVE_CATEGORIAS_ADMIN } from './listar-categorias-admin.consulta';

/**
 * Las tres escrituras invalidan lo mismo, y por eso comparten este ayudante en vez de repetirlo:
 * la lista del panel y la `['catalogo','categorias']` del formulario de producto, que es otra
 * entrada de caché con el mismo dato dentro. Sin lo segundo, la categoría recién creada no sale en
 * el desplegable hasta que aquella caduque, y cargarle un producto era el motivo para crearla.
 *
 * Y una tercera desde que el menú pinta el árbol: `['catalogo','menu']`. Una categoría nueva que
 * no aparece en el menú hasta recargar es una categoría que nadie encuentra.
 */
function invalidarCategorias(queryClient: QueryClient): void {
  void queryClient.invalidateQueries({ queryKey: CLAVE_CATEGORIAS_ADMIN });
  void queryClient.invalidateQueries({ queryKey: ['catalogo', 'categorias'] });
  void queryClient.invalidateQueries({ queryKey: ['catalogo', 'menu'] });
}

export function usarCrearCategoria() {
  const repositorio = inject(REPOSITORIO_CATEGORIAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (nueva: NuevaCategoria): Promise<ResultadoEscritura> => repositorio.crear(nueva),
    onSuccess: (resultado: ResultadoEscritura) => {
      if (resultado.tipo === 'OK') {
        invalidarCategorias(queryClient);
      }
    },
  }));
}

export function usarEditarCategoria() {
  const repositorio = inject(REPOSITORIO_CATEGORIAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (cambio: CambioDeCategoria): Promise<ResultadoEscritura> =>
      repositorio.editar(cambio),
    onSuccess: (resultado: ResultadoEscritura) => {
      if (resultado.tipo === 'OK') {
        invalidarCategorias(queryClient);
      }
    },
  }));
}

export function usarEliminarCategoria() {
  const repositorio = inject(REPOSITORIO_CATEGORIAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<ResultadoEscritura> => repositorio.eliminar(id),
    onSuccess: (resultado: ResultadoEscritura) => {
      if (resultado.tipo === 'OK') {
        invalidarCategorias(queryClient);
      }
    },
  }));
}
