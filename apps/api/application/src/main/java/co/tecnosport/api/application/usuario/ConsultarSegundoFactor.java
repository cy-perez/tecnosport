package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.PoliticaDeSegundoFactor;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la pantalla de seguridad del panel necesita saber (ADR-0096): si el factor está activo,
 * desde cuándo, cuántos códigos de respaldo quedan y si el panel lo exige —y entonces no se ofrece
 * desactivarlo—. Una activación pendiente cuenta como inactiva.
 */
public final class ConsultarSegundoFactor {

  /** {@code activadoEn} nulo si no está activo. */
  public record Estado(
      boolean activo, Instant activadoEn, int codigosDeRespaldoRestantes, boolean obligatorio) {}

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSegundosFactores repositorioSegundosFactores;
  private final PoliticaDeSegundoFactor politicaDeSegundoFactor;

  public ConsultarSegundoFactor(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSegundosFactores repositorioSegundosFactores,
      PoliticaDeSegundoFactor politicaDeSegundoFactor) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSegundosFactores = Objects.requireNonNull(repositorioSegundosFactores);
    this.politicaDeSegundoFactor = Objects.requireNonNull(politicaDeSegundoFactor);
  }

  public Estado ejecutar(UUID usuarioId) {
    Objects.requireNonNull(usuarioId, "El id del usuario no puede ser nulo.");
    Usuario usuario =
        repositorioUsuarios
            .buscarPorId(usuarioId)
            .orElseThrow(SesionDeRefrescoInvalidaException::new);
    Optional<SegundoFactor> activo =
        repositorioSegundosFactores.buscarParaActualizar(usuarioId).filter(SegundoFactor::activo);
    return new Estado(
        activo.isPresent(),
        activo.flatMap(SegundoFactor::activadoEn).orElse(null),
        activo.map(SegundoFactor::restantes).orElse(0),
        politicaDeSegundoFactor.obligatorioPara(usuario));
  }
}
