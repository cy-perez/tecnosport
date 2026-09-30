import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { LoteIngesta, SubirExportacion } from '../domain/ingesta.model';
import { REPOSITORIO_INGESTAS_ADMIN } from '../domain/repositorio-ingestas-admin.puerto';
import { CLAVE_INGESTAS_ADMIN } from './listar-ingestas.consulta';

/**
 * Sube la exportación y deja el lote en cola. Al volver, la lista se invalida y arranca el sondeo
 * (ver `usarListarIngestas`): el lote nuevo entra en RECIBIDO y se le ve avanzar.
 */
export function usarSubirExportacion() {
  const repositorio = inject(REPOSITORIO_INGESTAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: SubirExportacion): Promise<LoteIngesta> => repositorio.subir(comando),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CLAVE_INGESTAS_ADMIN }),
  }));
}
