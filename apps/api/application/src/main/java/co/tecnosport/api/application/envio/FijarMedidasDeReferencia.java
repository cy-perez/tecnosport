package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import java.util.Objects;

/**
 * Cambia las medidas transversales de la bolsa ({@code adr/0071}). Valen para la ropa, el calzado y
 * los bolsos a la vez, y desde el siguiente checkout.
 *
 * <p>Ojo con lo que mueven: la transportadora cobra peso volumétrico, y con 40 × 30 × 10 cm el
 * volumen pesa más que cualquier prenda. Bajar el alto baja el flete de casi todos los pedidos de
 * ropa.
 */
public final class FijarMedidasDeReferencia {

  private final RepositorioReferenciasDeEnvio referencias;

  public FijarMedidasDeReferencia(RepositorioReferenciasDeEnvio referencias) {
    this.referencias =
        Objects.requireNonNull(referencias, "El repositorio de referencias no puede ser nulo.");
  }

  public MedidasDeReferencia ejecutar(FijarMedidasDeReferenciaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    MedidasDeReferencia medidas =
        new MedidasDeReferencia(comando.largoCm(), comando.anchoCm(), comando.altoCm());
    referencias.guardarMedidas(medidas);
    return medidas;
  }
}
