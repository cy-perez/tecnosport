import { inject } from '@angular/core';
import { QueryClient, injectMutation } from '@tanstack/angular-query-experimental';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import { REPOSITORIO_REVERSIONES } from '../domain/repositorio-reversiones.puerto';
import { CausalReversion, DesenlaceReversion, SolicitudReversion } from '../domain/reversion.model';
import { claveReversionesDePedido } from './reversiones-de-pedido.consulta';

/**
 * Invalida tambien `['admin', 'atencion']`: radicar una reversion radica una solicitud de atencion
 * y resolverla la responde, asi que la bandeja quedo vieja en las dos direcciones.
 */
export function usarAccionesReversion() {
  const repositorio = inject(REPOSITORIO_REVERSIONES);
  const queryClient = inject(QueryClient);

  const invalidar = async (pedidoId: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: claveReversionesDePedido(pedidoId) }),
      queryClient.invalidateQueries({ queryKey: ['admin', 'atencion'] }),
    ]);
  };

  const radicar = injectMutation(() => ({
    mutationFn: (variables: {
      pedidoId: string;
      causal: CausalReversion;
      fechaDelHecho: string;
      descripcion: string;
    }): Promise<SolicitudReversion> => repositorio.radicar(variables),
    onSuccess: (_datos, variables) => invalidar(variables.pedidoId),
  }));

  const gestionar = injectMutation(() => ({
    mutationFn: (variables: {
      pedidoId: string;
      reversionId: string;
      gestion: string;
    }): Promise<SolicitudReversion> =>
      repositorio.registrarGestion(variables.reversionId, variables.gestion),
    onSuccess: (_datos, variables) => invalidar(variables.pedidoId),
  }));

  const resolver = injectMutation(() => ({
    mutationFn: (variables: {
      pedidoId: string;
      reversionId: string;
      desenlace: DesenlaceReversion;
      resumenParaElComprador: string;
      monto: number | null;
      medio: MedioReintegro | null;
      comprobante: string | null;
    }): Promise<SolicitudReversion> => repositorio.resolver(variables),
    onSuccess: (_datos, variables) => invalidar(variables.pedidoId),
  }));

  return { radicar, gestionar, resolver };
}
