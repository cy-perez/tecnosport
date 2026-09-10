package co.tecnosport.api.application.reversion;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.reversion.SolicitudReversion;
import java.util.Objects;

/**
 * Deja escrito qué se hizo para facilitar el trámite ante el emisor del medio de pago.
 *
 * <p>Existe porque los términos publicados prometen exactamente eso —"nosotros facilitamos el
 * trámite"— y una promesa de conducta sin rastro es indemostrable. No basta con no obstaculizarla:
 * hay un deber de facilitar, y facilitarlo implica que exista un camino y quede escrito.
 */
public final class RegistrarGestionReversion {

  private final RepositorioSolicitudesReversion repositorio;
  private final Reloj reloj;

  public RegistrarGestionReversion(RepositorioSolicitudesReversion repositorio, Reloj reloj) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public SolicitudReversion ejecutar(RegistrarGestionReversionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    SolicitudReversion reversion =
        repositorio
            .buscarPorId(comando.reversionId())
            .orElseThrow(() -> new SolicitudReversionNoEncontradaException(comando.reversionId()));
    reversion.registrarGestion(comando.gestion(), reloj.ahora(), comando.actor());
    repositorio.guardar(reversion);
    return reversion;
  }
}
