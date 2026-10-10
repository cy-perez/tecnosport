import { inject } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_PRODUCTOS_NO_PUBLICADOS } from '../domain/productos-no-publicados.puerto';
import { CLAVE_EXISTENCIAS } from './listar-existencias.consulta';
import { CLAVE_MEDIDAS } from './listar-medidas.consulta';
import { CLAVE_VARIANTES_SIN_MEDIR } from './listar-variantes-sin-medir.consulta';

/**
 * Bajo el prefijo de productos del panel: publicar, retirar y borrar uno ya lo invalidan, y la
 * cuenta tiene que moverse con ellos.
 */
export const CLAVE_PRODUCTOS_NO_PUBLICADOS = ['admin', 'productos', 'no-publicados'] as const;

export function usarContarProductosNoPublicados() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_NO_PUBLICADOS);

  return injectQuery(() => ({
    queryKey: CLAVE_PRODUCTOS_NO_PUBLICADOS,
    queryFn: (): Promise<number> => repositorio.contar(),
    staleTime: 15_000,
  }));
}

export interface ResultadoDeLimpieza {
  readonly eliminados: number;
  /** Por ventas o por existencias: se dicen juntos. */
  readonly conservados: number;
}

export interface BorrarNoPublicadosComando {
  /** Después de cada tanda, para que la pantalla diga cuánto lleva. */
  readonly alAvanzar?: (eliminados: number) => void;
}

/**
 * Pide tandas siguiendo el cursor hasta que el servidor no devuelve uno. Invalida al terminar,
 * también si falla a mitad —lo borrado ya no está—, y las mismas llaves que borrar un producto:
 * desaparecen filas de las pantallas de inventario, medidas y sin medir.
 */
export function usarBorrarProductosNoPublicados() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_NO_PUBLICADOS);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: async (comando: BorrarNoPublicadosComando = {}): Promise<ResultadoDeLimpieza> => {
      let eliminados = 0;
      let conservados = 0;
      let desde: string | null = null;
      let hasta: string | null = null;
      do {
        const tanda = await repositorio.eliminarTanda(desde, hasta);
        eliminados += tanda.eliminados;
        conservados += tanda.conservadosPorVentas + tanda.conservadosPorExistencias;
        comando.alAvanzar?.(eliminados);
        desde = tanda.siguiente;
        hasta = tanda.hasta;
      } while (desde !== null);
      return { eliminados, conservados };
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] });
      await queryClient.invalidateQueries({ queryKey: CLAVE_EXISTENCIAS });
      await queryClient.invalidateQueries({ queryKey: CLAVE_MEDIDAS });
      await queryClient.invalidateQueries({ queryKey: CLAVE_VARIANTES_SIN_MEDIR });
      await queryClient.invalidateQueries({ queryKey: ['catalogo'] });
    },
  }));
}
