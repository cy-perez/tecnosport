package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import java.util.Objects;

/**
 * Deja escrito que alguien miró una guía quieta, y con eso la saca de la bandeja.
 *
 * <p><strong>No cambia el envío ni el pedido.</strong> Una guía en excepción sigue en excepción
 * después del acuse: lo que cambia es que ya no está pidiendo atención. Si la transportadora manda
 * un evento nuevo después de este instante, la guía vuelve a la bandeja sola — esa regla vive en
 * {@code ListarEnviosEnRevision}, que es quien la aplica al leer.
 *
 * <p>Se puede acusar una guía cuantas veces haga falta. Cada acuse es una fila nueva y ninguna
 * sobrescribe a la anterior: el día de la reclamación hay que poder decir quién sabía qué, y
 * cuándo.
 */
public final class AcusarRevisionDeGuia {

  private final RepositorioEnvios envios;
  private final RepositorioAcusesDeRevision acuses;
  private final Reloj reloj;

  public AcusarRevisionDeGuia(
      RepositorioEnvios envios, RepositorioAcusesDeRevision acuses, Reloj reloj) {
    this.envios = Objects.requireNonNull(envios);
    this.acuses = Objects.requireNonNull(acuses);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public AcuseDeRevision ejecutar(AcusarRevisionDeGuiaComando comando) {
    Envio envio =
        envios
            .buscarPorGuia(comando.numeroGuia())
            .orElseThrow(() -> new GuiaNoEncontradaException(comando.numeroGuia()));
    GuiaEnvio guia =
        envio
            .guiaDe(comando.numeroGuia())
            .orElseThrow(() -> new GuiaNoEncontradaException(comando.numeroGuia()));

    AcuseDeRevision acuse =
        AcuseDeRevision.deGuia(guia.id(), comando.actor(), comando.nota(), reloj.ahora());
    acuses.guardar(acuse);
    return acuse;
  }
}
