import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { PedidoAdmin } from '../domain/pedido-admin.model';
import { REPOSITORIO_PEDIDOS_ADMIN } from '../domain/repositorio-pedidos-admin.puerto';

/**
 * Una mutación por acción del panel — todas invalidan el mismo prefijo de clave
 * (`['admin', 'pedidos']`, sin filtro exacto) para que cualquier página o filtro que esté
 * mirando la lista se refresque, no solo la combinación de filtro/página que originó la acción.
 */
export function usarAccionesPedidoAdmin() {
  const repositorio = inject(REPOSITORIO_PEDIDOS_ADMIN);
  const queryClient = inject(QueryClient);

  const invalidarLista = () => queryClient.invalidateQueries({ queryKey: ['admin', 'pedidos'] });

  const conciliarTransferencia = injectMutation(() => ({
    mutationFn: (pedidoId: string): Promise<PedidoAdmin> => repositorio.conciliarTransferencia(pedidoId),
    onSuccess: invalidarLista,
  }));

  const verificarContraentrega = injectMutation(() => ({
    mutationFn: (variables: { pedidoId: string; motivo: string }): Promise<PedidoAdmin> =>
      repositorio.verificarContraentrega(variables.pedidoId, variables.motivo),
    onSuccess: invalidarLista,
  }));

  const despachar = injectMutation(() => ({
    mutationFn: (variables: {
      pedidoId: string;
      transportadora: string;
      guia: string;
      costoEnvio: number;
    }): Promise<PedidoAdmin> =>
      repositorio.despachar(variables.pedidoId, variables.transportadora, variables.guia, variables.costoEnvio),
    onSuccess: invalidarLista,
  }));

  const marcarEntregado = injectMutation(() => ({
    mutationFn: (pedidoId: string): Promise<PedidoAdmin> => repositorio.marcarEntregado(pedidoId),
    onSuccess: invalidarLista,
  }));

  const rechazarEnEntrega = injectMutation(() => ({
    mutationFn: (variables: { pedidoId: string; motivo: string }): Promise<PedidoAdmin> =>
      repositorio.rechazarEnEntrega(variables.pedidoId, variables.motivo),
    onSuccess: invalidarLista,
  }));

  const conciliarRecaudo = injectMutation(() => ({
    mutationFn: (variables: { pedidoId: string; comisionRecaudo: number }): Promise<PedidoAdmin> =>
      repositorio.conciliarRecaudo(variables.pedidoId, variables.comisionRecaudo),
    onSuccess: invalidarLista,
  }));

  return {
    conciliarTransferencia,
    verificarContraentrega,
    despachar,
    marcarEntregado,
    rechazarEnEntrega,
    conciliarRecaudo,
  };
}
