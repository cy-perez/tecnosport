import { inject } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { OrdenDeDifusion, PublicacionEnRed, RedSocial } from '../domain/difusion.model';
import { REPOSITORIO_DIFUSION, ResultadoDifusion } from '../domain/repositorio-difusion.puerto';

/** Una llave por producto: el historial de uno no invalida el de otro. */
export const claveHistorialDifusion = (productoId: string) =>
  ['admin', 'difusion', productoId] as const;

/**
 * El historial de difusiones de un producto.
 *
 * `staleTime` corto a propósito, al revés que en el catálogo: lo único que cambia esta lista es el
 * botón que está justo al lado, así que la caché no está evitando peticiones de nadie más — y
 * enseñar "difundido hace 3 días" cuando alguien acaba de publicarlo desde otra pestaña es
 * exactamente el error que lleva a publicar dos veces.
 */
export function usarHistorialDeDifusion(productoId: () => string) {
  const repositorio = inject(REPOSITORIO_DIFUSION);

  return injectQuery(() => ({
    queryKey: claveHistorialDifusion(productoId()),
    queryFn: (): Promise<PublicacionEnRed[]> => repositorio.historial(productoId()),
    staleTime: 10_000,
    enabled: productoId() !== '',
  }));
}

/**
 * El pie que propone el servidor para una red.
 *
 * Es una consulta y no un cálculo local porque lleva el precio vigente, y el precio lo decide el
 * servidor. Se rearma al cambiar de red: en Facebook el pie lleva el enlace y en Instagram remite
 * a la biografía, así que no son el mismo texto.
 */
export function usarPropuestaDePie(productoId: () => string, red: () => RedSocial) {
  const repositorio = inject(REPOSITORIO_DIFUSION);

  return injectQuery(() => ({
    queryKey: ['admin', 'difusion', productoId(), 'propuesta', red()] as const,
    queryFn: (): Promise<string> => repositorio.proponerPie(productoId(), red()),
    // No se cachea largo: si alguien cambia el precio del producto en otra pestaña, la propuesta
    // tiene que reflejarlo antes de publicarse.
    staleTime: 10_000,
    enabled: productoId() !== '',
  }));
}

export function usarDifundirProducto() {
  const repositorio = inject(REPOSITORIO_DIFUSION);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (orden: OrdenDeDifusion): Promise<ResultadoDifusion> => repositorio.difundir(orden),
    onSuccess: async (resultado, orden) => {
      // Solo el historial de este producto. Difundir no toca el catálogo ni el inventario: no
      // cambia el precio, ni la existencia, ni el estado del producto. Invalidar `['catalogo']`
      // aquí —como sí hace publicar— sería recargar la vitrina entera por un post de Instagram.
      if (resultado.tipo === 'OK') {
        await queryClient.invalidateQueries({
          queryKey: claveHistorialDifusion(orden.productoId),
        });
      }
    },
  }));
}
