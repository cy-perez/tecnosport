import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { Seguimiento } from '../domain/pedido.model';
import { REPOSITORIO_PEDIDOS } from '../domain/repositorio-pedidos.puerto';

export interface CriteriosSeguimiento {
  readonly pedidoId: string;
  readonly correo: string;
}

/**
 * Solo se usa cuando `CheckoutStore.pedido` no está poblado — un pedido
 * confirmado sin salir del sitio (contraentrega, transferencia) no necesita
 * esta consulta, lo tiene ya en memoria (`estado.page.ts`). `criterios()` en
 * `null` deshabilita la consulta.
 */
export function usarSeguimientoPedido(criterios: () => CriteriosSeguimiento | null) {
  const repositorio = inject(REPOSITORIO_PEDIDOS);

  return injectQuery(() => {
    const valor = criterios();
    return {
      queryKey: ['checkout', 'seguimiento', valor] as const,
      queryFn: (): Promise<Seguimiento | null> => {
        const criterios = valor as CriteriosSeguimiento;
        return repositorio.consultarSeguimiento(criterios.pedidoId, criterios.correo);
      },
      enabled: valor !== null,
      staleTime: 15_000,
    };
  });
}
