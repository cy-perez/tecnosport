import { inject } from '@angular/core';
import { injectMutation } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_PEDIDOS } from '../domain/repositorio-pedidos.puerto';

export interface SolicitudDeEnlace {
  readonly numeroPedido: string;
  readonly correo: string;
}

/**
 * El formulario de "Estado del pedido": pide que el enlace de seguimiento llegue al correo del
 * pedido (ADR-0088). El servidor responde lo mismo acierte o no, así que el éxito de esta mutación
 * no dice que el pedido exista — dice que, si existía, el correo va en camino.
 *
 * Sin reintento: el endpoint va detrás del límite de intentos por IP más estrecho de todos, y un
 * reintento automático gastaría el presupuesto de quien lo usa de buena fe.
 */
export function usarSolicitarEnlaceDeSeguimiento() {
  const repositorio = inject(REPOSITORIO_PEDIDOS);

  return injectMutation(() => ({
    mutationFn: (solicitud: SolicitudDeEnlace): Promise<void> =>
      repositorio.solicitarEnlaceDeSeguimiento(solicitud.numeroPedido, solicitud.correo),
    retry: false,
  }));
}
