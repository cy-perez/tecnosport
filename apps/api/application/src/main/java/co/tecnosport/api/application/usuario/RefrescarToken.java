package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.usuario.AlcanceDeAcceso;
import co.tecnosport.api.domain.usuario.PoliticaDeSegundoFactor;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.SesionRefrescoReutilizadaException;
import co.tecnosport.api.domain.usuario.SesionRefrescoVencidaException;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Rota el token de refresco (docs/08-seguridad-legal.md): consumir la sesión actual y crear el
 * siguiente eslabón de la misma familia. Una sesión ya usada que reaparece revoca toda la familia
 * antes de rechazar la solicitud — la señal de robo del token no se ignora en silencio.
 */
public final class RefrescarToken {

  private final RepositorioSesiones repositorioSesiones;
  private final RepositorioUsuarios repositorioUsuarios;
  private final GeneradorDeTokens generadorDeTokens;
  private final Reloj reloj;
  private final Duration vigenciaRefresco;

  private final RepositorioSegundosFactores repositorioSegundosFactores;
  private final PoliticaDeSegundoFactor politicaDeSegundoFactor;

  public RefrescarToken(
      RepositorioSesiones repositorioSesiones,
      RepositorioUsuarios repositorioUsuarios,
      GeneradorDeTokens generadorDeTokens,
      Reloj reloj,
      Duration vigenciaRefresco,
      RepositorioSegundosFactores repositorioSegundosFactores,
      PoliticaDeSegundoFactor politicaDeSegundoFactor) {
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.generadorDeTokens = Objects.requireNonNull(generadorDeTokens);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaRefresco = Objects.requireNonNull(vigenciaRefresco);
    this.repositorioSegundosFactores = Objects.requireNonNull(repositorioSegundosFactores);
    this.politicaDeSegundoFactor = Objects.requireNonNull(politicaDeSegundoFactor);
  }

  public TokensDeSesion ejecutar(RefrescarTokenComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Instant ahora = reloj.ahora();

    SesionRefresco sesion =
        repositorioSesiones
            .buscarPorId(comando.refreshTokenId())
            .orElseThrow(SesionDeRefrescoInvalidaException::new);

    try {
      sesion.marcarUsado(ahora);
    } catch (SesionRefrescoVencidaException e) {
      throw new SesionDeRefrescoInvalidaException();
    } catch (SesionRefrescoReutilizadaException e) {
      repositorioSesiones.revocarFamilia(sesion.familiaId(), ahora);
      throw new SesionDeRefrescoComprometidaException();
    }
    repositorioSesiones.guardar(sesion);

    Usuario usuario =
        repositorioUsuarios
            .buscarPorId(sesion.usuarioId())
            .orElseThrow(SesionDeRefrescoInvalidaException::new);

    SesionRefresco nuevaSesion =
        SesionRefresco.crear(usuario.id(), sesion.familiaId(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(nuevaSesion);

    // El alcance se recalcula en cada emisión (ADR-0096): encender la bandera alcanza a las
    // sesiones abiertas en cuanto refrescan, y activar el factor las libera.
    AlcanceDeAcceso alcance =
        politicaDeSegundoFactor.alcance(
            usuario, repositorioSegundosFactores.estaActivo(usuario.id()));
    String accessToken = generadorDeTokens.generarAcceso(usuario, alcance, ahora);
    return new TokensDeSesion(
        usuario.id(),
        usuario.rol(),
        accessToken,
        nuevaSesion.id(),
        alcance == AlcanceDeAcceso.SOLO_ACTIVAR_SEGUNDO_FACTOR);
  }
}
