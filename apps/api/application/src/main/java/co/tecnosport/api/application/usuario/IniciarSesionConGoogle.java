package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Entrar —o crear la cuenta— con Google (ADR-0074). Google solo comprueba quién es la persona; la
 * sesión es la de siempre: el mismo token de acceso y la misma cookie de refresco que {@link
 * IniciarSesion}, así que nada de lo que viene después distingue cómo entró.
 *
 * <p>Tres caminos, en este orden:
 *
 * <ol>
 *   <li>Ya hay una cuenta unida a esa cuenta de Google: entra.
 *   <li>Hay una cuenta con ese correo, creada con clave: se une a Google y entra, si Google manda
 *       sobre ese buzón ({@code @gmail.com} o un dominio suyo). Si la cuenta no estaba verificada,
 *       su clave se descarta: pudo crearla otro con el correo ajeno. Si Google no manda sobre el
 *       buzón, se pide entrar con la contraseña.
 *   <li>No hay cuenta: se crea, ya verificada, si autorizó el tratamiento de datos. Sin ese sí no
 *       hay cuenta (Ley 1581 de 2012) y se le pide pasar por «Crear cuenta».
 * </ol>
 *
 * <p>El panel no entra por aquí: una cuenta {@code ADMIN} recibe el mismo error que una credencial
 * mala. El administrador tiene su propia puerta, con clave.
 */
public final class IniciarSesionConGoogle {

  private final VerificadorDeCredencialGoogle verificador;
  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSesiones repositorioSesiones;
  private final GeneradorDeTokens generadorDeTokens;
  private final RepositorioAutorizaciones repositorioAutorizaciones;
  private final Reloj reloj;
  private final Duration vigenciaRefresco;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentos;
  private final Duration ventanaIntentos;
  private final String versionPolitica;
  private final String clienteId;

  public IniciarSesionConGoogle(
      VerificadorDeCredencialGoogle verificador,
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      GeneradorDeTokens generadorDeTokens,
      RepositorioAutorizaciones repositorioAutorizaciones,
      Reloj reloj,
      Duration vigenciaRefresco,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentos,
      Duration ventanaIntentos,
      String versionPolitica,
      String clienteId) {
    this.verificador = Objects.requireNonNull(verificador);
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.generadorDeTokens = Objects.requireNonNull(generadorDeTokens);
    this.repositorioAutorizaciones = Objects.requireNonNull(repositorioAutorizaciones);
    this.reloj = Objects.requireNonNull(reloj);
    this.vigenciaRefresco = Objects.requireNonNull(vigenciaRefresco);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentos = maximoIntentos;
    this.ventanaIntentos = Objects.requireNonNull(ventanaIntentos);
    this.versionPolitica = Objects.requireNonNull(versionPolitica);
    this.clienteId = clienteId == null ? "" : clienteId.trim();
  }

  /** Si el ambiente tiene un cliente de Google configurado. Sin él, el sitio no ofrece el botón. */
  public boolean habilitado() {
    return !clienteId.isEmpty();
  }

  /**
   * El identificador público del cliente OAuth, el que el botón de Google necesita. No es secreto.
   */
  public String clienteId() {
    return clienteId;
  }

  public TokensDeSesion ejecutar(IniciarSesionConGoogleComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    if (!habilitado()) {
      throw new GoogleNoHabilitadoException();
    }
    Instant ahora = reloj.ahora();
    // Por IP y no por cuenta: antes de verificar la credencial no se sabe de quién es, y una
    // credencial inválida es justo lo que hay que frenar.
    String llave = "ip:iniciar-sesion-google:" + Objects.toString(comando.direccionIp(), "");
    if (!limitadorDeIntentos.permitir(llave, maximoIntentos, ventanaIntentos, ahora)) {
      throw new LimiteDeIntentosExcedidoException();
    }
    if (comando.credencial() == null || comando.credencial().isBlank()) {
      throw new CredencialGoogleInvalidaException();
    }

    IdentidadGoogle identidad = verificador.verificar(comando.credencial());
    if (!identidad.correoVerificado()) {
      // Sin correo verificado no se puede afirmar que es de quien entra, y unirla a una cuenta
      // existente por ese correo sería entregársela a otro.
      throw new CredencialGoogleInvalidaException();
    }
    // Sin `olvidar` al acertar, a diferencia del login con clave: aquí la llave es la IP y no una
    // cuenta, y una IP con varias cuentas de Google podría crear cuentas sin tope si cada acierto
    // borrara el conteo (revisión de arquitectura del ADR-0074).

    Usuario usuario =
        repositorioUsuarios
            .buscarPorGoogleSub(identidad.sub())
            .or(() -> unirPorCorreo(identidad, ahora))
            .orElseGet(() -> crear(identidad, comando, ahora));
    if (usuario.rol() != Rol.CLIENTE) {
      throw new CredencialesInvalidasException();
    }

    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), GeneradorIdentificador.nuevo(), ahora, vigenciaRefresco);
    repositorioSesiones.guardar(sesion);
    String accessToken = generadorDeTokens.generarAcceso(usuario, ahora);
    return new TokensDeSesion(usuario.id(), usuario.rol(), accessToken, sesion.id());
  }

  private Optional<Usuario> unirPorCorreo(IdentidadGoogle identidad, Instant ahora) {
    return repositorioUsuarios
        .buscarPorCorreo(new CorreoElectronico(identidad.correo()))
        .map(
            usuario -> {
              // El correo del administrador no se une a Google ni se toma para una cuenta nueva:
              // sin esto, el `orElseGet` de arriba creaba un CLIENTE con el mismo correo. Lo
              // encontró la prueba del administrador.
              if (usuario.rol() != Rol.CLIENTE) {
                throw new CredencialesInvalidasException();
              }
              if (!identidad.googleEsAutoridad()) {
                throw new CuentaExistenteRequiereClaveException();
              }
              usuario.vincularGoogle(identidad.sub(), ahora);
              // Cualquier sesión abierta antes de unirla se cierra: si la cuenta no era de quien
              // la creó, la clave ya se descartó y ahora tampoco le queda una cookie viva.
              repositorioSesiones.revocarTodasDeUsuario(usuario.id(), ahora);
              repositorioUsuarios.guardar(usuario);
              return usuario;
            });
  }

  private Usuario crear(
      IdentidadGoogle identidad, IniciarSesionConGoogleComando comando, Instant ahora) {
    if (!comando.autorizaDatos()) {
      throw new CuentaGoogleSinRegistroException();
    }
    CorreoElectronico correo = new CorreoElectronico(identidad.correo());
    Usuario usuario = Usuario.crearConGoogle(correo, identidad.sub(), ahora);
    AutorizacionDatos autorizacion =
        AutorizacionDatos.enRegistroConGoogle(
            true, correo, usuario.id(), versionPolitica, comando.direccionIp(), ahora);
    repositorioUsuarios.guardar(usuario);
    repositorioAutorizaciones.guardar(autorizacion);
    return usuario;
  }
}
