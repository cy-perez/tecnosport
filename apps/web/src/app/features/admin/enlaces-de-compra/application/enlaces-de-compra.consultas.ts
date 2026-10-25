import { inject } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { CLAVE_EXISTENCIAS } from '../../productos/application/listar-existencias.consulta';
import { EnlaceDeCompraAdmin } from '../domain/enlace-de-compra-admin.model';
import { REPOSITORIO_ENLACES_DE_COMPRA_ADMIN } from '../domain/repositorio-enlaces-de-compra-admin.puerto';

export const CLAVE_ENLACES_DE_COMPRA = ['admin', 'enlaces-de-compra'] as const;

/** La bandeja: corta, porque quien acaba de generar o anular uno espera verlo. */
export function usarEnlacesDeCompra() {
  const repositorio = inject(REPOSITORIO_ENLACES_DE_COMPRA_ADMIN);
  return injectQuery(() => ({
    queryKey: CLAVE_ENLACES_DE_COMPRA,
    queryFn: () => repositorio.listar(),
    staleTime: 5_000,
  }));
}

/**
 * Generar aparta una unidad, así que además de la bandeja cambia la existencia que ven la ficha y
 * el panel del producto.
 */
export function usarGenerarEnlaceDeCompra() {
  const repositorio = inject(REPOSITORIO_ENLACES_DE_COMPRA_ADMIN);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (varianteId: string): Promise<EnlaceDeCompraAdmin> =>
      repositorio.generar(varianteId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['catalogo'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] });
      void queryClient.invalidateQueries({ queryKey: CLAVE_EXISTENCIAS });
      return queryClient.invalidateQueries({ queryKey: CLAVE_ENLACES_DE_COMPRA });
    },
  }));
}

export function usarAnularEnlaceDeCompra() {
  const repositorio = inject(REPOSITORIO_ENLACES_DE_COMPRA_ADMIN);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (enlaceId: string): Promise<EnlaceDeCompraAdmin> => repositorio.anular(enlaceId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['catalogo'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] });
      void queryClient.invalidateQueries({ queryKey: CLAVE_EXISTENCIAS });
      return queryClient.invalidateQueries({ queryKey: CLAVE_ENLACES_DE_COMPRA });
    },
  }));
}

/**
 * La paleta nueva puede traer variantes nuevas: el detalle del producto y la ficha pública las
 * tienen que ver.
 */
export function usarDefinirPaleta() {
  const repositorio = inject(REPOSITORIO_ENLACES_DE_COMPRA_ADMIN);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (comando: { productoId: string; colores: readonly string[] }): Promise<number> =>
      repositorio.definirPaleta(comando.productoId, comando.colores),
    onSuccess: (_nuevas, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['catalogo'] });
      return queryClient.invalidateQueries({
        queryKey: ['admin', 'productos', variables.productoId],
      });
    },
  }));
}
