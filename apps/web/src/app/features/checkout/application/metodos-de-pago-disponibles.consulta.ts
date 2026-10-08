import { inject } from '@angular/core';
import { injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import {
  DatosEntrega,
  LineaComando,
  MetodosDePagoDisponiblesComando,
} from '../domain/pedido.comandos';
import { MetodoPago } from '../domain/pedido.model';
import { REPOSITORIO_PEDIDOS, RepositorioPedidos } from '../domain/repositorio-pedidos.puerto';

/**
 * El comando, armado en un solo sitio. La página de transportadoras lo precarga y la de métodos de
 * pago lo consulta, y la precarga solo sirve si las dos claves son **la misma**: un campo de más o
 * de menos en un lado y la página de métodos de pago vuelve a esperar los ~7 s de la cotización.
 */
export function comandoMetodosDePago(
  datos: DatosEntrega,
  lineas: readonly LineaComando[],
): MetodosDePagoDisponiblesComando {
  return {
    correo: datos.correo,
    lineas: lineas.map((linea) => ({ varianteId: linea.varianteId, cantidad: linea.cantidad })),
    tipoEntrega: datos.tipoEntrega,
    direccion: datos.direccion,
    transportadora: datos.transportadora,
  };
}

function opcionesMetodosDePago(
  repositorio: RepositorioPedidos,
  valor: MetodosDePagoDisponiblesComando | null,
) {
  return {
    queryKey: ['checkout', 'metodos-de-pago-disponibles', valor] as const,
    queryFn: (): Promise<MetodoPago[]> =>
      repositorio.metodosDePagoDisponibles(valor as MetodosDePagoDisponiblesComando),
    // Depende de existencia y de reglas de cobertura de contraentrega, que sí
    // pueden cambiar en minutos (`docs/11-pagos-y-envios.md`) — más corto que
    // el catálogo (60s), pero no cero: evita un refetch en cada tecla si el
    // componente recalcula `criterios()` con un debounce más fino que este.
    staleTime: 15_000,
  };
}

/**
 * `POST` en el backend, pero de solo lectura en la práctica: "el checkout
 * consulta antes de mostrar las opciones" (`docs/09-plan-de-arranque.md`).
 * Se modela como consulta, no como mutación — reactiva a como cambian
 * dirección y tipo de entrega mientras el comprador llena el formulario,
 * mismo patrón que `usarBusquedaProductos`. `criterios()` en `null`
 * (formulario todavía incompleto) deshabilita la consulta.
 */
export function usarMetodosDePagoDisponibles(
  criterios: () => MetodosDePagoDisponiblesComando | null,
) {
  const repositorio = inject(REPOSITORIO_PEDIDOS);

  return injectQuery(() => {
    const valor = criterios();
    return { ...opcionesMetodosDePago(repositorio, valor), enabled: valor !== null };
  });
}

/**
 * Pide los métodos de pago antes de llegar a su página. El servidor los decide cotizando con
 * recaudo (la contraentrega depende de si la elegida cobra en la puerta), y eso son unos segundos
 * contra la plataforma de envíos: pedidos al elegir la transportadora, corren mientras el comprador
 * pulsa «Continuar», y la página de métodos de pago se suma a esa misma petición si sigue en vuelo.
 *
 * <p>No espera ni lanza: es una ventaja, no un paso. Si falla, la página vuelve a pedirlos.
 */
export function usarPrecargarMetodosDePago(): (comando: MetodosDePagoDisponiblesComando) => void {
  const repositorio = inject(REPOSITORIO_PEDIDOS);
  const queryClient = inject(QueryClient);
  return (comando) => {
    void queryClient.prefetchQuery(opcionesMetodosDePago(repositorio, comando));
  };
}
