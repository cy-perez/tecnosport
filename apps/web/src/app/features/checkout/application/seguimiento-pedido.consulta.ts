import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { EstadoPedido, Seguimiento } from '../domain/pedido.model';
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
 * Cuánto se insiste al volver de la pasarela con el pago todavía sin entrar. Wompi devuelve al
 * comprador antes de que su evento llegue a la API —medido en dev el 29 de octubre de 2026: la
 * pantalla consultó 1,4 s antes del webhook—, así que una sola consulta deja «Esperando el pago»
 * en pantalla y el carrito lleno aunque el pago ya entró.
 */
export const ESPERA_DEL_PAGO = { intervaloMs: 3_000, topeMs: 120_000 } as const;

/**
 * Cada cuánto volver a preguntar, o `false` para no hacerlo. Solo mientras el pedido siga en
 * `PAGO_PENDIENTE`, **esta pestaña lo haya mandado a pagar** —quien llega por el enlace de un correo
 * consulta una vez, que es por lo que se apagaron los reenganches de abajo— y no haya pasado el
 * tope. Pasado el tope la pantalla se queda como está: recargar sigue funcionando.
 */
export function intervaloDeEsperaDelPago(
  estado: EstadoPedido,
  salioAPagarDesdeAqui: boolean,
  transcurridoMs: number,
): number | false {
  if (estado !== 'PAGO_PENDIENTE' || !salioAPagarDesdeAqui) {
    return false;
  }
  return transcurridoMs < ESPERA_DEL_PAGO.topeMs ? ESPERA_DEL_PAGO.intervaloMs : false;
}

/**
 * Solo se usa cuando `CheckoutStore.pedido` no está poblado — un pedido
 * confirmado sin salir del sitio (contraentrega, transferencia) no necesita
 * esta consulta, lo tiene ya en memoria (`estado.page.ts`). `criterios()` en
 * `null` deshabilita la consulta, que es lo que mantiene la pantalla quieta
 * mientras el formulario está sin enviar.
 */
export function usarSeguimientoPedido(
  criterios: () => CriteriosSeguimiento | null,
  salioAPagarDesdeAqui: (pedidoId: string) => boolean = () => false,
) {
  const repositorio = inject(REPOSITORIO_PEDIDOS);
  // Desde la primera respuesta que encontró el pago pendiente, no desde que se montó la pantalla:
  // el formulario puede pasar un buen rato sin enviarse.
  let esperandoDesde: number | null = null;

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
      refetchInterval: (query) => {
        const seguimiento = query.state.data;
        if (!seguimiento) {
          return false;
        }
        esperandoDesde ??= Date.now();
        return intervaloDeEsperaDelPago(
          seguimiento.estado,
          salioAPagarDesdeAqui(seguimiento.id),
          Date.now() - esperandoDesde,
        );
      },
    };
  });
}
