import { inject } from '@angular/core';
import { QueryClient, injectMutation } from '@tanstack/angular-query-experimental';
import { MedidasDeReferencia } from '../domain/referencias-envio.model';
import { REPOSITORIO_REFERENCIAS_ENVIO } from '../domain/repositorio-referencias-envio.puerto';
import { claveReferenciasEnvio } from './referencias-envio.consulta';

/** Las tres escrituras invalidan la misma consulta: la pantalla entera es una sola lectura. */
export function usarAccionesReferenciasEnvio() {
  const repositorio = inject(REPOSITORIO_REFERENCIAS_ENVIO);
  const queryClient = inject(QueryClient);

  const invalidar = () => queryClient.invalidateQueries({ queryKey: claveReferenciasEnvio() });

  const fijarMedidas = injectMutation(() => ({
    mutationFn: (medidas: MedidasDeReferencia): Promise<MedidasDeReferencia> =>
      repositorio.fijarMedidas(medidas),
    onSuccess: invalidar,
  }));

  const fijarPeso = injectMutation(() => ({
    mutationFn: (variables: { categoriaId: string; pesoGramos: number }): Promise<void> =>
      repositorio.fijarPeso(variables.categoriaId, variables.pesoGramos),
    onSuccess: invalidar,
  }));

  const quitarPeso = injectMutation(() => ({
    mutationFn: (categoriaId: string): Promise<void> => repositorio.quitarPeso(categoriaId),
    onSuccess: invalidar,
  }));

  return { fijarMedidas, fijarPeso, quitarPeso };
}
