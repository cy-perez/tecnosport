import { inject } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import { PagoSinPedidoAdmin } from '../domain/pedido-admin.model';
import { REPOSITORIO_PEDIDOS_ADMIN } from '../domain/repositorio-pedidos-admin.puerto';

export const CLAVE_PAGOS_SIN_PEDIDO = ['admin', 'pagos', 'sin-pedido'] as const;

/** La bandeja de pagos que entraron sin un pedido que los esperara. */
export function usarPagosSinPedido() {
  const repositorio = inject(REPOSITORIO_PEDIDOS_ADMIN);
  return injectQuery(() => ({
    queryKey: CLAVE_PAGOS_SIN_PEDIDO,
    queryFn: (): Promise<PagoSinPedidoAdmin[]> => repositorio.listarPagosSinPedido(),
    staleTime: 15_000,
  }));
}

export function usarReintegroDePagoSinPedido() {
  const repositorio = inject(REPOSITORIO_PEDIDOS_ADMIN);
  const queryClient = inject(QueryClient);
  return injectMutation(() => ({
    mutationFn: (variables: {
      pagoId: string;
      medio: MedioReintegro;
      comprobante: string | null;
    }): Promise<void> => repositorio.registrarReintegroDePagoSinPedido(variables),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CLAVE_PAGOS_SIN_PEDIDO }),
  }));
}
