package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.usuario.AlcanceDeAcceso;
import co.tecnosport.api.domain.usuario.CodigoDeRespaldo;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SegundoFactorNoActivoException;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Diez códigos de respaldo nuevos; los anteriores, usados o no, dejan de servir (ADR-0096). Para
 * cuando quedan pocos, o cuando la hoja donde estaban se perdió.
 *
 * <p>Pide la clave <b>y</b> un código: con solo una sesión abierta —un equipo prestado, un token
 * robado— no se puede uno quedar con la puerta de atrás de la cuenta. Revoca todas las sesiones y
 * abre una nueva, como {@link CambiarClave}, y avisa por correo.
 */
public final class RegenerarCodigosDeRespaldo {

  /** La sesión nueva y los códigos, en claro y por única vez. */
  public record CodigosRegenerados(TokensDeSesion sesion, List<String> codigosDeRespaldo) {}

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSegundosFactores repositorioSegundosFactores;
  private final RepositorioSesiones repositorioSesiones;
  private final CodificadorDeClaves codificadorDeClaves;
  private final GeneradorDeTokens generadorDeTokens;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final Duration vigenciaRefresco;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentos;

  public RegenerarCodigosDeRespaldo(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSegundosFactores repositorioSegundosFactores,
      RepositorioSesiones repositorioSesiones,
      CodificadorDeClaves codificadorDeClaves,
      GeneradorDeTokens generadorDeTokens,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      Duration vigenciaRefresco,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentos) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSegundosFactores = Objects.requireNonNull(repositorioSegundosFactores);
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.generadorDeTokens = Objects.requireNonNull(generadorDeTokens);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaRefresco = Objects.requireNonNull(vigenciaRefresco);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentos = Objects.requireNonNull(ventanaIntentos);
  }

  public CodigosRegenerados ejecutar(ClaveYCodigoSegundoFactorComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Instant ahora = reloj.ahora();

    String llave = "cuenta:regenerar-codigos:" + comando.usuarioId();
    if (!limitadorDeIntentos.permitir(llave, maximoIntentosPorCuenta, ventanaIntentos, ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }
    Usuario usuario =
        repositorioUsuarios
            .buscarPorId(comando.usuarioId())
            .filter(
                u ->
                    u.tieneClave()
                        && codificadorDeClaves.verificar(
                            comando.claveActualTextoPlano(), u.claveHash()))
            .orElseThrow(CredencialesInvalidasException::new);
    SegundoFactor factor =
        repositorioSegundosFactores
            .buscarParaActualizar(usuario.id())
            .filter(SegundoFactor::activo)
            .orElseThrow(SegundoFactorNoActivoException::new);
    factor.verificar(comando.codigo(), ahora);
    limitadorDeIntentos.olvidar(llave);

    List<CodigoDeRespaldo> codigos = factor.regenerarCodigos(ahora);
    repositorioSegundosFactores.guardar(factor);

    // Antes de abrir la nueva, no después: ver CambiarClave.
    repositorioSesiones.revocarTodasDeUsuario(usuario.id(), ahora);
    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), GeneradorIdentificador.nuevo(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(sesion);
    // El factor sigue activo: el alcance es completo por definición de la política.
    String accessToken = generadorDeTokens.generarAcceso(usuario, AlcanceDeAcceso.COMPLETO, ahora);

    enviadorDeCorreo.enviar(
        usuario.correo(),
        textos.texto(TextoDeCorreo.USUARIO_SEGUNDO_FACTOR_RESPALDO_REGENERADO_ASUNTO),
        textos.texto(TextoDeCorreo.USUARIO_SEGUNDO_FACTOR_RESPALDO_REGENERADO_CUERPO));

    return new CodigosRegenerados(
        new TokensDeSesion(usuario.id(), usuario.rol(), accessToken, sesion.id(), false),
        codigos.stream().map(CodigoDeRespaldo::mostrado).toList());
  }
}
