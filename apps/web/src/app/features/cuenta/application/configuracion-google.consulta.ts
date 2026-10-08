import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { ConfiguracionGoogle } from '../domain/boton-google.puerto';
import { REPOSITORIO_CUENTA } from '../domain/repositorio-cuenta.puerto';

/**
 * Si este ambiente ofrece entrar con Google. Es configuración del servidor y no cambia mientras
 * alguien está en la pantalla: se pide una vez. Si falla, no hay botón, que es el lado seguro.
 */
export function usarConfiguracionGoogle() {
  const repositorio = inject(REPOSITORIO_CUENTA);
  return injectQuery(() => ({
    queryKey: ['cuenta', 'configuracion-google'] as const,
    queryFn: (): Promise<ConfiguracionGoogle> => repositorio.configuracionGoogle(),
    staleTime: Infinity,
  }));
}
