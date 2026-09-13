import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { CotizacionEnvio, CotizarEnvioComando } from '../domain/envio.model';
import { REPOSITORIO_ENVIOS } from '../domain/repositorio-envios.puerto';

/**
 * Mismo patrón que `usarMetodosDePagoDisponibles`: `POST` en el backend pero
 * lectura en la práctica, reactiva a la dirección que el comprador va
 * llenando. `criterios()` en `null` —retiro en punto, o dirección todavía
 * incompleta— deshabilita la consulta y no gasta una llamada al proveedor.
 *
 * `staleTime` de un minuto: la tarifa vale 24 horas del lado de Skydropx, así
 * que repetir la llamada mientras el comprador corrige la dirección no
 * cambiaría la respuesta y sí gastaría cuota de un proveedor que solo admite
 * dos peticiones por segundo.
 */
export function usarCotizacionEnvio(criterios: () => CotizarEnvioComando | null) {
  const repositorio = inject(REPOSITORIO_ENVIOS);

  return injectQuery(() => {
    const valor = criterios();
    return {
      // La clave NO incluye la calle a propósito: el flete depende del código
      // DANE de la ciudad y de los bultos, nada más. Metiéndola, cada tecla de
      // "Calle 72 # 10-34" sería una clave nueva y una llamada nueva a un
      // proveedor que admite dos peticiones por segundo.
      queryKey: [
        'checkout',
        'cotizacion-envio',
        valor && { ciudad: valor.direccion.codigoDaneCiudad, lineas: valor.lineas },
      ] as const,
      queryFn: (): Promise<CotizacionEnvio | null> => repositorio.cotizar(valor as CotizarEnvioComando),
      enabled: valor !== null,
      staleTime: 60_000,
      // Un solo reintento, no los tres de la configuración por omisión. Desde que el checkout
      // **espera** esta consulta antes de dejar continuar, sus reintentos son tiempo que el
      // comprador pasa mirando un botón que carga; y del otro lado el backend ya sondea al
      // proveedor hasta diez segundos por llamada, así que tres intentos encadenados son medio
      // minuto largo para llegar a la misma conclusión. Uno cubre el corte de red pasajero.
      retry: 1,
    };
  });
}
