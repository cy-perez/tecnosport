package co.tecnosport.api.domain.compartido;

import java.time.ZoneId;

/**
 * El negocio opera en Medellín y los plazos legales se cuentan con su calendario, no con el del
 * servidor ni con el del navegador de quien mire la pantalla.
 *
 * <p>Vive aquí y no dentro de un plazo concreto porque ya la usan dos: el del retracto y el de
 * respuesta a peticiones, quejas y reclamos. Un huso horario repetido en dos archivos es un huso
 * horario que algún día va a divergir.
 */
public final class ZonaDelNegocio {

  public static final ZoneId ZONA = ZoneId.of("America/Bogota");

  private ZonaDelNegocio() {}
}
