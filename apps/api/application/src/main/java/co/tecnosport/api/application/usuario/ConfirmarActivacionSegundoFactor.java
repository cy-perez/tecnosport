package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.usuario.ActivacionSegundoFactorNoIniciadaException;
import co.tecnosport.api.domain.usuario.CodigoDeRespaldo;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * El segundo paso de la activación (ADR-0096): el primer código de la aplicación demuestra que la
 * clave quedó bien copiada, y solo entonces el factor se activa. Devuelve los diez códigos de
 * respaldo en claro —la única vez que existen así— y una sesión nueva.
 *
 * <p>Revoca <b>todas</b> las sesiones del usuario antes de abrir la nueva, en el orden de {@link
 * CambiarClave}: una sesión abierta antes de tener el factor no lo pasó, y quien activa el factor
 * porque sospecha de su clave quiere fuera a quien la tenga.
 *
 * <p>El código son seis dígitos: sin límite, un millón de intentos lo adivinan. El tope es el del
 * cambio de clave, por cuenta.
 */
public final class ConfirmarActivacionSegundoFactor {

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSegundosFactores repositorioSegundosFactores;
  private final RepositorioSesiones repositorioSesiones;
  private final GeneradorDeTokens generadorDeTokens;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final Duration vigenciaRefresco;
  private final Duration vigenciaActivacionPendiente;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentos;

  public ConfirmarActivacionSegundoFactor(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSegundosFactores repositorioSegundosFactores,
      RepositorioSesiones repositorioSesiones,
      GeneradorDeTokens generadorDeTokens,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      Duration vigenciaRefresco,
      Duration vigenciaActivacionPendiente,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentos) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSegundosFactores = Objects.requireNonNull(repositorioSegundosFactores);
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.generadorDeTokens = Objects.requireNonNull(generadorDeTokens);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaRefresco = Objects.requireNonNull(vigenciaRefresco);
    this.vigenciaActivacionPendiente = Objects.requireNonNull(vigenciaActivacionPendiente);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentos = Objects.requireNonNull(ventanaIntentos);
  }

  public ActivacionConfirmada ejecutar(ConfirmarActivacionSegundoFactorComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Instant ahora = reloj.ahora();

    String llave = "cuenta:confirmar-segundo-factor:" + comando.usuarioId();
    if (!limitadorDeIntentos.permitir(llave, maximoIntentosPorCuenta, ventanaIntentos, ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }
    Usuario usuario =
        repositorioUsuarios
            .buscarPorId(comando.usuarioId())
            .orElseThrow(SesionDeRefrescoInvalidaException::new);
    SegundoFactor factor =
        repositorioSegundosFactores
            .buscarParaActualizar(usuario.id())
            .orElseThrow(ActivacionSegundoFactorNoIniciadaException::new);

    List<CodigoDeRespaldo> codigos =
        factor.confirmar(comando.codigo(), ahora, vigenciaActivacionPendiente);
    limitadorDeIntentos.olvidar(llave);
    repositorioSegundosFactores.guardar(factor);

    // Antes de abrir la nueva, no después: ver CambiarClave.
    repositorioSesiones.revocarTodasDeUsuario(usuario.id(), ahora);
    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), GeneradorIdentificador.nuevo(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(sesion);
    String accessToken = generadorDeTokens.generarAcceso(usuario, ahora);

    enviadorDeCorreo.enviar(
        usuario.correo(),
        textos.texto(TextoDeCorreo.USUARIO_SEGUNDO_FACTOR_ACTIVADO_ASUNTO),
        textos.texto(TextoDeCorreo.USUARIO_SEGUNDO_FACTOR_ACTIVADO_CUERPO));

    return new ActivacionConfirmada(
        new TokensDeSesion(usuario.id(), usuario.rol(), accessToken, sesion.id()),
        codigos.stream().map(CodigoDeRespaldo::mostrado).toList());
  }
}
