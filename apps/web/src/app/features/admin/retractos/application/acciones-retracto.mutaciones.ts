import { inject } from '@angular/core';
import { QueryClient, injectMutation } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_RETRACTOS } from '../domain/repositorio-retractos.puerto';
import { MedioReembolso, SolicitudRetracto } from '../domain/retracto.model';
import { claveRetractosDePedido } from './retractos-de-pedido.consulta';

/**
 * Cada acción invalida dos cosas, y las dos hacen falta: la lista de retractos del pedido, y el
 * prefijo `['admin', 'pedidos']`. Lo segundo no es por gusto — recibir el producto mueve el pedido
 * a `DEVUELTO`, así que la fila de la lista queda mostrando un estado viejo si no se refresca.
 */
export function usarAccionesRetracto() {
  const repositorio = inject(REPOSITORIO_RETRACTOS);
  const queryClient = inject(QueryClient);

  const invalidar = async (pedidoId: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: claveRetractosDePedido(pedidoId) }),
      queryClient.invalidateQueries({ queryKey: ['admin', 'pedidos'] }),
    ]);
  };

  const radicar = injectMutation(() => ({
    mutationFn: (variables: {
      pedidoId: string;
      motivo: string | null;
    }): Promise<SolicitudRetracto> => repositorio.radicar(variables.pedidoId, variables.motivo),
    onSuccess: (_datos, variables) => invalidar(variables.pedidoId),
  }));

  const recibirProducto = injectMutation(() => ({
    mutationFn: (variables: {
      pedidoId: string;
      solicitudId: string;
    }): Promise<SolicitudRetracto> => repositorio.recibirProducto(variables.solicitudId),
    onSuccess: (_datos, variables) => invalidar(variables.pedidoId),
  }));

  const registrarReembolso = injectMutation(() => ({
    mutationFn: (variables: {
      pedidoId: string;
      solicitudId: string;
      monto: number;
      medio: MedioReembolso;
      comprobante: string | null;
    }): Promise<SolicitudRetracto> =>
      repositorio.registrarReembolso(
        variables.solicitudId,
        variables.monto,
        variables.medio,
        variables.comprobante,
      ),
    onSuccess: (_datos, variables) => invalidar(variables.pedidoId),
  }));

  return { radicar, recibirProducto, registrarReembolso };
}
