import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { ModalidadesDeEntrega } from '../domain/envio.model';
import { REPOSITORIO_ENVIOS } from '../domain/repositorio-envios.puerto';

/**
 * Qué formas de entrega ofrece hoy el negocio. Es configuración del servidor y no cambia mientras
 * alguien compra, así que se pide una vez por sesión de la pestaña.
 *
 * Mientras no llega, o si falla, la pantalla se comporta como si la recogida estuviera apagada: es
 * el lado seguro, porque el servidor la rechaza con un 409 si lo está, y ofrecerla para que luego
 * falle es peor que no ofrecerla un instante.
 */
export function usarModalidadesDeEntrega() {
  const repositorio = inject(REPOSITORIO_ENVIOS);

  return injectQuery(() => ({
    queryKey: ['checkout', 'modalidades-de-entrega'] as const,
    queryFn: (): Promise<ModalidadesDeEntrega> => repositorio.modalidades(),
    staleTime: Infinity,
  }));
}
