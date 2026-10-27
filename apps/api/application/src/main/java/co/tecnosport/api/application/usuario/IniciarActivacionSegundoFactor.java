package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SegundoFactorYaActivoException;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * El primer paso de la activación (ADR-0096): pide la clave actual, genera una clave TOTP nueva y
 * la deja <b>pendiente</b>. Nada cambia para la cuenta hasta que se confirme con el primer código
 * de la aplicación ({@code ConfirmarActivacionSegundoFactor}).
 *
 * <p>Pide la clave aunque quien llama ya tenga sesión: un token de acceso robado no tiene que
 * bastar para atar la cuenta a un teléfono ajeno. Volver a empezar una activación pendiente la
 * reemplaza —la clave anterior pudo quedarse a medio copiar— pero una activa no se toca: para
 * cambiar de teléfono, primero se desactiva.
 */
public final class IniciarActivacionSegundoFactor {

  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSegundosFactores repositorioSegundosFactores;
  private final CodificadorDeClaves codificadorDeClaves;
  private final Reloj reloj;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCuenta;
  private final Duration ventanaIntentos;
  private final String emisor;

  public IniciarActivacionSegundoFactor(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSegundosFactores repositorioSegundosFactores,
      CodificadorDeClaves codificadorDeClaves,
      Reloj reloj,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCuenta,
      Duration ventanaIntentos,
      String emisor) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSegundosFactores = Objects.requireNonNull(repositorioSegundosFactores);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.reloj = Objects.requireNonNull(reloj);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCuenta = maximoIntentosPorCuenta;
    this.ventanaIntentos = Objects.requireNonNull(ventanaIntentos);
    this.emisor = Objects.requireNonNull(emisor);
  }

  public ActivacionIniciada ejecutar(IniciarActivacionSegundoFactorComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Instant ahora = reloj.ahora();

    String llave = "cuenta:activar-segundo-factor:" + comando.usuarioId();
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
    limitadorDeIntentos.olvidar(llave);

    Optional<SegundoFactor> existente =
        repositorioSegundosFactores.buscarParaActualizar(usuario.id());
    if (existente.map(SegundoFactor::activo).orElse(false)) {
      throw new SegundoFactorYaActivoException();
    }
    SegundoFactor pendiente = SegundoFactor.iniciar(usuario.id(), ahora);
    repositorioSegundosFactores.guardar(pendiente);

    String clave = pendiente.clave().enBase32();
    return new ActivacionIniciada(
        clave,
        uriOtpauth(emisor, usuario.correo().valor(), clave),
        Totp.DIGITOS,
        Totp.PERIODO_SEGUNDOS);
  }

  /**
   * El formato de facto de Google Authenticator ({@code otpauth://totp/EMISOR:CUENTA?...}), que
   * leen todas. El emisor va dos veces, en la etiqueta y en {@code issuer}, porque las aplicaciones
   * viejas solo leen la primera y las nuevas prefieren la segunda. {@link URLEncoder} codifica para
   * formularios —el espacio como {@code +}—, y aquí hace falta {@code %20}.
   */
  static String uriOtpauth(String emisor, String cuenta, String claveBase32) {
    String e = codificar(emisor);
    return "otpauth://totp/"
        + e
        + ":"
        + codificar(cuenta)
        + "?secret="
        + claveBase32
        + "&issuer="
        + e
        + "&algorithm=SHA1&digits="
        + Totp.DIGITOS
        + "&period="
        + Totp.PERIODO_SEGUNDOS;
  }

  private static String codificar(String texto) {
    return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
