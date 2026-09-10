import { inject } from '@angular/core';
import { QueryClient, injectMutation } from '@tanstack/angular-query-experimental';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import { DesenlaceGarantia, ReclamacionGarantia } from '../domain/garantia.model';
import { REPOSITORIO_GARANTIAS } from '../domain/repositorio-garantias.puerto';
import { claveGarantiasDePedido } from './garantias-de-pedido.consulta';

/**
 * Invalida tambien el prefijo `['admin', 'atencion']`: radicar una garantia radica una solicitud de
 * atencion, y resolverla la responde. La bandeja que alguien tenga abierta en otra pestana quedo
 * vieja en las dos direcciones.
 */
export function usarAccionesGarantia() {
  const repositorio = inject(REPOSITORIO_GARANTIAS);
  const queryClient = inject(QueryClient);

  const invalidar = async (pedidoId: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: claveGarantiasDePedido(pedidoId) }),
      queryClient.invalidateQueries({ queryKey: ['admin', 'atencion'] }),
    ]);
  };

  const radicar = injectMutation(() => ({
    mutationFn: (variables: {
      pedidoId: string;
      varianteId: string;
      descripcionDelFallo: string;
    }): Promise<ReclamacionGarantia> =>
      repositorio.radicar(
        variables.pedidoId,
        variables.varianteId,
        variables.descripcionDelFallo,
      ),
    onSuccess: (_datos, variables) => invalidar(variables.pedidoId),
  }));

  const resolver = injectMutation(() => ({
    mutationFn: (variables: {
      pedidoId: string;
      reclamacionId: string;
      desenlace: DesenlaceGarantia;
      resumenParaElComprador: string;
      monto: number | null;
      medio: MedioReintegro | null;
      comprobante: string | null;
    }): Promise<ReclamacionGarantia> => repositorio.resolver(variables),
    onSuccess: (_datos, variables) => invalidar(variables.pedidoId),
  }));

  return { radicar, resolver };
}
