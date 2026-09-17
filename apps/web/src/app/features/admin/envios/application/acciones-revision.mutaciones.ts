import { inject } from '@angular/core';
import { QueryClient, injectMutation } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_REVISION_ENVIOS } from '../domain/repositorio-revision-envios.puerto';
import {
  AcuseDeRevision,
  EmisionResuelta,
  VeredictoDeEmision,
} from '../domain/revision-envio.model';

/**
 * Las dos acciones invalidan la bandeja entera: un acuse saca esa fila de la lista, y la lista es
 * una sola consulta.
 */
export function usarAccionesRevision() {
  const repositorio = inject(REPOSITORIO_REVISION_ENVIOS);
  const queryClient = inject(QueryClient);

  const invalidar = () =>
    queryClient.invalidateQueries({ queryKey: ['admin', 'envios', 'revision'] });

  const acusarGuia = injectMutation(() => ({
    mutationFn: (variables: {
      numeroGuia: string;
      nota: string | null;
    }): Promise<AcuseDeRevision> => repositorio.acusarGuia(variables.numeroGuia, variables.nota),
    onSuccess: invalidar,
  }));

  const acusarEmision = injectMutation(() => ({
    mutationFn: (variables: { emisionId: string; nota: string | null }): Promise<AcuseDeRevision> =>
      repositorio.acusarEmision(variables.emisionId, variables.nota),
    onSuccess: invalidar,
  }));

  const resolverEmision = injectMutation(() => ({
    mutationFn: (variables: {
      emisionId: string;
      veredicto: VeredictoDeEmision;
      enviosEnPlataforma: readonly string[];
      nota: string | null;
    }): Promise<EmisionResuelta> => repositorio.resolverEmision(variables),
    onSuccess: invalidar,
  }));

  return { acusarGuia, acusarEmision, resolverEmision };
}
