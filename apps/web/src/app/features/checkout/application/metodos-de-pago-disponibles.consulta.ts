import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { MetodosDePagoDisponiblesComando } from '../domain/pedido.comandos';
import { MetodoPago } from '../domain/pedido.model';
import { REPOSITORIO_PEDIDOS } from '../domain/repositorio-pedidos.puerto';

/**
 * `POST` en el backend, pero de solo lectura en la práctica: "el checkout
 * consulta antes de mostrar las opciones" (`docs/09-plan-de-arranque.md`).
 * Se modela como consulta, no como mutación — reactiva a como cambian
 * dirección y tipo de entrega mientras el comprador llena el formulario,
 * mismo patrón que `usarBusquedaProductos`. `criterios()` en `null`
 * (formulario todavía incompleto) deshabilita la consulta.
 */
export function usarMetodosDePagoDisponibles(criterios: () => MetodosDePagoDisponiblesComando | null) {
  const repositorio = inject(REPOSITORIO_PEDIDOS);

  return injectQuery(() => {
    const valor = criterios();
    return {
      queryKey: ['checkout', 'metodos-de-pago-disponibles', valor] as const,
      queryFn: (): Promise<MetodoPago[]> =>
        repositorio.metodosDePagoDisponibles(valor as MetodosDePagoDisponiblesComando),
      enabled: valor !== null,
      // Depende de existencia y de reglas de cobertura de contraentrega, que sí
      // pueden cambiar en minutos (`docs/11-pagos-y-envios.md`) — más corto que
      // el catálogo (60s), pero no cero: evita un refetch en cada tecla si el
      // componente recalcula `criterios()` con un debounce más fino que este.
      staleTime: 15_000,
    };
  });
}
