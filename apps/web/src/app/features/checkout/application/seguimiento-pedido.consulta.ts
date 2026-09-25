import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { Seguimiento } from '../domain/pedido.model';
import { REPOSITORIO_PEDIDOS } from '../domain/repositorio-pedidos.puerto';

/**
 * Por dónde se entra al seguimiento, que son dos y no una.
 *
 * <p>`ID` es el camino del enlace del correo: la pasarela devuelve al sitio con `pedidoId` y
 * `correo` en la URL y la pantalla los lee de ahí. `NUMERO` es el del formulario, y existe porque
 * el `id` es un UUID que el comprador nunca ve — el único identificador que tiene en la mano es el
 * número legible de su comprobante.
 *
 * <p>Una unión etiquetada y no dos funciones: lo que cambia es de dónde sale el pedido, no qué se
 * hace con él, y la pantalla tiene que poder pasar de un caso al otro sin cambiar de consulta.
 */
export type CriteriosSeguimiento =
  | { readonly tipo: 'ID'; readonly pedidoId: string; readonly correo: string }
  | { readonly tipo: 'NUMERO'; readonly numeroPedido: string; readonly correo: string };

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
        return criterios.tipo === 'ID'
          ? repositorio.consultarSeguimiento(criterios.pedidoId, criterios.correo)
          : repositorio.consultarSeguimientoPorNumero(criterios.numeroPedido, criterios.correo);
      },
      enabled: valor !== null,
      staleTime: 15_000,
      // Sin reintento automático, y esto no es una preferencia: el endpoint por número va detrás de
      // un límite de intentos por IP mucho más estrecho que el resto —diez cada diez minutos—, así
      // que los reintentos por omisión de TanStack gastarían el presupuesto de quien consulta de
      // buena fe. El camino del enlace del correo no tiene ese problema, pero tampoco necesita
      // reintentar: si el pedido no está, no va a estar.
      retry: false,
      // **Y `retry: false` no bastaba.** El `QueryClient` del proyecto se construye sin
      // `defaultOptions` (`core/consultas/transferencia-estado-consultas.ts`), así que seguían en
      // `true` los tres reenganches por omisión de TanStack. Con `staleTime` de quince segundos,
      // quien deja abierta la pantalla de estado —que es exactamente lo que hace quien espera un
      // pedido— gastaba una consulta por cada vuelta a la pestaña y acababa contra el límite. Lo
      // levantó la revisión; los reintentos de error eran solo un tercio del problema.
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
    };
  });
}
