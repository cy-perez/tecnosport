package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.pedido.Direccion;
import java.util.List;
import java.util.Objects;

/**
 * Lo que hay que saber para cotizar: a dónde va, qué se manda y cuánto vale.
 *
 * <p>{@code conRecaudo} pide la cotización <em>con contraentrega</em>, y no es un detalle de
 * formato: cambia qué transportadoras responden. Verificado contra el sandbox el 11 de septiembre
 * de 2026 — pedida sin recaudo, ninguna tarifa se queja; pedida con recaudo, las que no lo admiten
 * se caen con restricciones propias del recaudo y solo sobreviven las que sí. Esa es la única señal
 * de cobertura que da Skydropx: no existe un campo por tarifa que la declare.
 *
 * <p>El origen **no** viaja aquí. Es la dirección de despacho del negocio, la misma para todas las
 * cotizaciones, y vive en la configuración del adaptador ({@code ORIGEN_*} de
 * docs/07-infra-gcp.md). Pasarlo en cada llamada sería repetir un dato fijo y abrir la puerta a que
 * dos sitios del código discrepen sobre desde dónde se despacha.
 */
public record CotizacionEnvio(Direccion destino, List<Bulto> bultos, boolean conRecaudo) {

  /** Sin recaudo, que es el caso normal: el comprador paga antes de que salga el paquete. */
  public CotizacionEnvio(Direccion destino, List<Bulto> bultos) {
    this(destino, bultos, false);
  }

  public CotizacionEnvio {
    Objects.requireNonNull(destino, "El destino de la cotización no puede ser nulo.");
    Objects.requireNonNull(bultos, "Los bultos de la cotización no pueden ser nulos.");
    bultos = List.copyOf(bultos);
    if (bultos.isEmpty()) {
      throw new IllegalArgumentException("Una cotización necesita al menos un bulto.");
    }
  }
}
