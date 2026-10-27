import { inject } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { AlertaPagosSinTransaccion, AlertaPedidosDetenidos } from '../domain/pedido-admin.model';
import { REPOSITORIO_PEDIDOS_ADMIN } from '../domain/repositorio-pedidos-admin.puerto';

export const CLAVE_PAGOS_SIN_TRANSACCION = ['admin', 'alertas', 'pagos-sin-transaccion'] as const;

/** Los pagos de Wompi que ningún proceso concilia: sin id de transacción y sin webhook. */
export function usarPagosSinTransaccion() {
  const repositorio = inject(REPOSITORIO_PEDIDOS_ADMIN);
  return injectQuery(() => ({
    queryKey: CLAVE_PAGOS_SIN_TRANSACCION,
    queryFn: (): Promise<AlertaPagosSinTransaccion> => repositorio.listarPagosSinTransaccion(),
    staleTime: 60_000,
  }));
}

export const CLAVE_PEDIDOS_DETENIDOS = ['admin', 'alertas', 'pedidos-en-preparacion'] as const;

/** Los pedidos que llevan más del umbral en preparación; su reserva no vence sola. */
export function usarPedidosDetenidosEnPreparacion() {
  const repositorio = inject(REPOSITORIO_PEDIDOS_ADMIN);
  return injectQuery(() => ({
    queryKey: CLAVE_PEDIDOS_DETENIDOS,
    queryFn: (): Promise<AlertaPedidosDetenidos> =>
      repositorio.listarPedidosDetenidosEnPreparacion(),
    staleTime: 60_000,
  }));
}

export function usarRevisionDePagoSinTransaccion() {
  const repositorio = inject(REPOSITORIO_PEDIDOS_ADMIN);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (pagoId: string): Promise<void> =>
      repositorio.marcarPagoSinTransaccionRevisado(pagoId),
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLAVE_PAGOS_SIN_TRANSACCION }),
  }));
}
