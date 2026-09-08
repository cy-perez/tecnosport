package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.CerrarSesion;
import co.tecnosport.api.application.usuario.CerrarSesionComando;
import co.tecnosport.api.application.usuario.ConfirmarRecuperacion;
import co.tecnosport.api.application.usuario.ConfirmarRecuperacionComando;
import co.tecnosport.api.application.usuario.IniciarSesion;
import co.tecnosport.api.application.usuario.IniciarSesionComando;
import co.tecnosport.api.application.usuario.RefrescarToken;
import co.tecnosport.api.application.usuario.RefrescarTokenComando;
import co.tecnosport.api.application.usuario.RegistrarUsuario;
import co.tecnosport.api.application.usuario.RegistrarUsuarioComando;
import co.tecnosport.api.application.usuario.SesionDeRefrescoInvalidaException;
import co.tecnosport.api.application.usuario.SolicitarRecuperacion;
import co.tecnosport.api.application.usuario.SolicitarRecuperacionComando;
import co.tecnosport.api.application.usuario.TokensDeSesion;
import co.tecnosport.api.application.usuario.VerificarCorreo;
import co.tecnosport.api.application.usuario.VerificarCorreoComando;
import co.tecnosport.api.presentation.compartido.IpDelCliente;
import co.tecnosport.api.presentation.usuario.dto.ConfirmarRecuperacionRequest;
import co.tecnosport.api.presentation.usuario.dto.IniciarSesionRequest;
import co.tecnosport.api.presentation.usuario.dto.RegistrarUsuarioRequest;
import co.tecnosport.api.presentation.usuario.dto.SesionRespuesta;
import co.tecnosport.api.presentation.usuario.dto.SolicitarRecuperacionRequest;
import co.tecnosport.api.presentation.usuario.dto.VerificarCorreoRequest;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro, verificación de correo, login, refresco y cierre de sesión. El token de acceso va en el
 * cuerpo — vive en memoria en el cliente, nunca en {@code localStorage}
 * (docs/08-seguridad-legal.md); el de refresco va en una cookie {@code HttpOnly}, {@code Secure},
 * {@code SameSite=Lax}, acotada a {@code /api/v1/auth} para que ningún otro endpoint la reciba sin
 * necesitarla. El registro no abre sesión: la cuenta nace sin verificar y no puede iniciar sesión
 * hasta seguir el enlace del correo.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionControlador {

  private static final String COOKIE_REFRESCO = "refresco";
  private static final Duration VIGENCIA_REFRESCO = Duration.ofDays(30);

  private final RegistrarUsuario registrarUsuario;
  private final VerificarCorreo verificarCorreo;
  private final SolicitarRecuperacion solicitarRecuperacion;
  private final ConfirmarRecuperacion confirmarRecuperacion;
  private final IniciarSesion iniciarSesion;
  private final RefrescarToken refrescarToken;
  private final CerrarSesion cerrarSesion;
  private final TransactionTemplate transaccion;

  public AutenticacionControlador(
      RegistrarUsuario registrarUsuario,
      VerificarCorreo verificarCorreo,
      SolicitarRecuperacion solicitarRecuperacion,
      ConfirmarRecuperacion confirmarRecuperacion,
      IniciarSesion iniciarSesion,
      RefrescarToken refrescarToken,
      CerrarSesion cerrarSesion,
      PlatformTransactionManager transactionManager) {
    this.registrarUsuario = Objects.requireNonNull(registrarUsuario);
    this.verificarCorreo = Objects.requireNonNull(verificarCorreo);
    this.solicitarRecuperacion = Objects.requireNonNull(solicitarRecuperacion);
    this.confirmarRecuperacion = Objects.requireNonNull(confirmarRecuperacion);
    this.iniciarSesion = Objects.requireNonNull(iniciarSesion);
    this.refrescarToken = Objects.requireNonNull(refrescarToken);
    this.cerrarSesion = Objects.requireNonNull(cerrarSesion);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping("/registro")
  public ResponseEntity<Void> registro(
      @RequestBody RegistrarUsuarioRequest cuerpo, HttpServletRequest peticion) {
    String ip = IpDelCliente.de(peticion);
    transaccion.executeWithoutResult(
        estado ->
            registrarUsuario.ejecutar(
                new RegistrarUsuarioComando(
                    cuerpo.correo(), cuerpo.clave(), cuerpo.autorizaDatos(), ip)));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping("/verificacion")
  public ResponseEntity<Void> verificacion(@RequestBody VerificarCorreoRequest cuerpo) {
    transaccion.executeWithoutResult(
        estado -> verificarCorreo.ejecutar(new VerificarCorreoComando(cuerpo.token())));
    return ResponseEntity.noContent().build();
  }

  // Siempre 204, exista o no una cuenta con ese correo — docs/08-seguridad-legal.md, OWASP, mismo
  // criterio que CredencialesInvalidasException en IniciarSesion: no se revela cuál de los dos fue.
  @PostMapping("/recuperacion")
  public ResponseEntity<Void> recuperacion(@RequestBody SolicitarRecuperacionRequest cuerpo) {
    transaccion.executeWithoutResult(
        estado ->
            solicitarRecuperacion.ejecutar(new SolicitarRecuperacionComando(cuerpo.correo())));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/recuperacion/confirmar")
  public ResponseEntity<Void> confirmarRecuperacion(
      @RequestBody ConfirmarRecuperacionRequest cuerpo) {
    transaccion.executeWithoutResult(
        estado ->
            confirmarRecuperacion.ejecutar(
                new ConfirmarRecuperacionComando(cuerpo.token(), cuerpo.claveNueva())));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/sesion")
  public ResponseEntity<SesionRespuesta> iniciarSesion(@RequestBody IniciarSesionRequest cuerpo) {
    TokensDeSesion tokens =
        transaccion.execute(
            estado ->
                iniciarSesion.ejecutar(new IniciarSesionComando(cuerpo.correo(), cuerpo.clave())));
    return respuestaConCookie(tokens);
  }

  // 204 y no 401 cuando NO llega la cookie: quien nunca inició sesión no está fallando la
  // autenticación, es que no hay ninguna sesión que refrescar, y el frontend pregunta en cada
  // arranque justo porque no puede saberlo (la cookie es HttpOnly). Con 401, el navegador de
  // todo visitante anónimo registraba un error en la consola en cada visita — Lighthouse lo
  // contaba en «buenas prácticas» y ensuciaba la consola de cualquiera que abriera DevTools.
  // Una cookie que SÍ llega pero no sirve —basura, vencida, ya usada— sigue siendo 401: ahí
  // pasó algo.
  @PostMapping("/refresco")
  @ApiResponse(responseCode = "200", description = "Sesión refrescada")
  @ApiResponse(
      responseCode = "204",
      description = "No hay cookie de refresco: no hay sesión que refrescar",
      content = @Content)
  public ResponseEntity<SesionRespuesta> refrescar(
      @CookieValue(name = COOKIE_REFRESCO, required = false) String cookieRefresco) {
    if (cookieRefresco == null || cookieRefresco.isBlank()) {
      return ResponseEntity.noContent().build();
    }
    UUID refreshTokenId = idObligatorioDesdeCookie(cookieRefresco);
    TokensDeSesion tokens =
        transaccion.execute(
            estado -> refrescarToken.ejecutar(new RefrescarTokenComando(refreshTokenId)));
    return respuestaConCookie(tokens);
  }

  @PostMapping("/cierre")
  public ResponseEntity<Void> cerrarSesion(
      @CookieValue(name = COOKIE_REFRESCO, required = false) String cookieRefresco) {
    UUID refreshTokenId = idOpcionalDesdeCookie(cookieRefresco);
    if (refreshTokenId != null) {
      transaccion.executeWithoutResult(
          estado -> cerrarSesion.ejecutar(new CerrarSesionComando(refreshTokenId)));
    }
    ResponseCookie cookieVacia = cookieDeRefresco("", Duration.ZERO);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookieVacia.toString())
        .build();
  }

  private ResponseEntity<SesionRespuesta> respuestaConCookie(TokensDeSesion tokens) {
    ResponseCookie cookie = cookieDeRefresco(tokens.refreshTokenId().toString(), VIGENCIA_REFRESCO);
    SesionRespuesta cuerpo =
        new SesionRespuesta(tokens.usuarioId(), tokens.rol().name(), tokens.accessToken());
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(cuerpo);
  }

  private ResponseCookie cookieDeRefresco(String valor, Duration vigencia) {
    return ResponseCookie.from(COOKIE_REFRESCO, valor)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(vigencia)
        .build();
  }

  private UUID idObligatorioDesdeCookie(String valor) {
    UUID id = idOpcionalDesdeCookie(valor);
    if (id == null) {
      throw new SesionDeRefrescoInvalidaException();
    }
    return id;
  }

  private UUID idOpcionalDesdeCookie(String valor) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(valor);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
