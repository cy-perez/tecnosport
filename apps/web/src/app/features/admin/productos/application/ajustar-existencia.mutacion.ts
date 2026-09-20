import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { AjustarExistenciaAdmin, ExistenciaAjustada } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';
import { CLAVE_EXISTENCIAS } from './listar-existencias.consulta';

export function usarAjustarExistencia() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: AjustarExistenciaAdmin): Promise<ExistenciaAjustada> =>
      repositorio.ajustarExistencia(comando),
    // Dos llaves, no una. La de esta pantalla, y la del catálogo público: el ajuste escribe
    // también `variante.existencia` (`ADR-0049`), así que la ficha y la rejilla tienen dentro el
    // número viejo hasta que su entrada caduque. Es el mismo descuido que costó que una marca
    // recién creada no saliera en su desplegable.
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CLAVE_EXISTENCIAS });
      void queryClient.invalidateQueries({ queryKey: ['catalogo'] });
    },
  }));
}
