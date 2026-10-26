import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { ModalidadesDeEntrega } from '../domain/envio.model';
import { REPOSITORIO_ENVIOS } from '../domain/repositorio-envios.puerto';

/**
 * Qué formas de entrega tiene el carrito con estas variantes. Desde el 26 de octubre de 2026 la
 * recogida depende de él —solo si todo viene de proveedores locales (`ADR-0093`)—, así que la
 * clave lleva las variantes, ordenadas: el mismo carrito en otro orden es la misma pregunta. Lo
 * que la decide no cambia mientras alguien compra, así que cada carrito se pregunta una vez.
 *
 * Mientras no llega, si falla o si aún no se sabe qué lleva el carrito, la pantalla se comporta
 * como si la recogida estuviera apagada: es el lado seguro, porque el servidor la rechaza con un
 * 409, y ofrecerla para que luego falle es peor que no ofrecerla un instante.
 */
export function usarModalidadesDeEntrega(varianteIds: () => readonly string[]) {
  const repositorio = inject(REPOSITORIO_ENVIOS);

  return injectQuery(() => {
    const ids = [...new Set(varianteIds())].sort();
    return {
      queryKey: ['checkout', 'modalidades-de-entrega', ids] as const,
      queryFn: (): Promise<ModalidadesDeEntrega> => repositorio.modalidades(ids),
      enabled: ids.length > 0,
      staleTime: Infinity,
    };
  });
}
