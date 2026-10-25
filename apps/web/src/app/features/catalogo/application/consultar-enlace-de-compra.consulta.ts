import { inject } from '@angular/core';
import { injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { EnlaceDeCompra } from '../domain/enlace-de-compra.model';
import {
  REPOSITORIO_ENLACES_DE_COMPRA,
  RepositorioEnlacesDeCompra,
} from '../domain/repositorio-enlaces-de-compra.puerto';
import { PRECARGA_NO_BLOQUEANTE, sinBloquearLaNavegacion } from '../../../core/consultas/precarga';

/**
 * Mismas opciones para la ficha y para el `resolve` de su ruta (ADR-0011). Corto: si vence
 * mientras la página está abierta, quien decide es el pedido, que lo vuelve a comprobar.
 */
function opcionesEnlace(repositorio: RepositorioEnlacesDeCompra, token: string | null) {
  return {
    queryKey: ['catalogo', 'enlace-de-compra', token] as const,
    queryFn: (): Promise<EnlaceDeCompra | null> => repositorio.consultar(token as string),
    enabled: token !== null,
    staleTime: 30_000,
  };
}

/** El enlace de compra de la URL de la ficha (`?enlace=…`, ADR-0086). Sin token no pregunta nada. */
export function usarEnlaceDeCompra(token: () => string | null) {
  const repositorio = inject(REPOSITORIO_ENLACES_DE_COMPRA);

  return injectQuery(() => opcionesEnlace(repositorio, token()));
}

/**
 * Precargado con la ficha: sin esto, el SSR de quien llega por el enlace pintaba «Consultar por
 * WhatsApp» y al terminar la consulta cambiaba a «Agregar al carrito».
 */
export function precargarEnlaceDeCompra(token: string | null): Promise<void> {
  if (token === null) {
    return Promise.resolve();
  }
  const repositorio = inject(REPOSITORIO_ENLACES_DE_COMPRA);
  const queryClient = inject(QueryClient);

  return sinBloquearLaNavegacion(
    queryClient.prefetchQuery({ ...opcionesEnlace(repositorio, token), ...PRECARGA_NO_BLOQUEANTE }),
  );
}
