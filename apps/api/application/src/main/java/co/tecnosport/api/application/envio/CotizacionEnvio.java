package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.pedido.Direccion;
import java.util.List;
import java.util.Objects;

/**
 * Lo que hay que saber para cotizar: a dónde va y qué se manda.
 *
 * <p>El origen **no** viaja aquí. Es la dirección de despacho del negocio, la misma para todas las
 * cotizaciones, y vive en la configuración del adaptador ({@code ORIGEN_*} de
 * docs/07-infra-gcp.md). Pasarlo en cada llamada sería repetir un dato fijo y abrir la puerta a que
 * dos sitios del código discrepen sobre desde dónde se despacha.
 */
public record CotizacionEnvio(Direccion destino, List<Paquete> paquetes) {

  public CotizacionEnvio {
    Objects.requireNonNull(destino, "El destino de la cotización no puede ser nulo.");
    Objects.requireNonNull(paquetes, "Los paquetes de la cotización no pueden ser nulos.");
    paquetes = List.copyOf(paquetes);
    if (paquetes.isEmpty()) {
      throw new IllegalArgumentException("Una cotización necesita al menos un paquete.");
    }
  }
}
