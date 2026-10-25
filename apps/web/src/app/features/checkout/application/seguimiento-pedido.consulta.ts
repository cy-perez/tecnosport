import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { Seguimiento } from '../domain/pedido.model';
import { REPOSITORIO_PEDIDOS } from '../domain/repositorio-pedidos.puerto';

/**
 * Por dónde se entra al seguimiento, que desde el 25 de octubre de 2026 es uno solo: el enlace de
 * los correos, con `pedidoId` en la consulta y el correo en el fragmento. La pasarela devuelve al
 * sitio por el mismo camino.
 *
 * <p>Hubo un segundo, `NUMERO`, que consultaba con el número legible del comprobante. Se quitó
 * porque el número es secuencial: quien conociera el correo de alguien podía recorrerlo hasta dar
 * con un pedido suyo. El formulario ahora pide que el enlace llegue al correo (ADR-0088,
 * `solicitar-enlace-de-seguimiento.mutacion.ts`).
 */
export interface CriteriosSeguimiento {
  readonly pedidoId: string;
  readonly correo: string;
}

/**
 * Solo se usa cuando `CheckoutStore.pedido` no está poblado — un pedido
 * confirmado sin salir del sitio (contraentrega, transferencia) no necesita
 * esta consulta, lo tiene ya en memoria (`estado.page.ts`). `criterios()` en
 * `null` deshabilita la consulta, que es lo que mantiene la pantalla quieta
 * mientras el formulario está sin enviar.
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
      // Sin reintento automático: si el pedido del enlace no está, no va a estar.
      retry: false,
      // **Y `retry: false` no bastaba.** El `QueryClient` del proyecto se construye sin
      // `defaultOptions` (`core/consultas/transferencia-estado-consultas.ts`), así que seguían en
      // `true` los tres reenganches por omisión de TanStack. Con `staleTime` de quince segundos,
      // quien deja abierta la pantalla de estado —que es exactamente lo que hace quien espera un
      // pedido— gastaba una consulta por cada vuelta a la pestaña. Lo levantó la revisión, cuando
      // esta consulta también entraba por número y ese camino tenía límite de intentos.
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
    };
  });
}
