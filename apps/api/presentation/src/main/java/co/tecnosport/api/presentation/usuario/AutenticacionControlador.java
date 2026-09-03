package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.CerrarSesion;
import co.tecnosport.api.application.usuario.CerrarSesionComando;
import co.tecnosport.api.application.usuario.IniciarSesion;
import co.tecnosport.api.application.usuario.IniciarSesionComando;
import co.tecnosport.api.application.usuario.RefrescarToken;
import co.tecnosport.api.application.usuario.RefrescarTokenComando;
import co.tecnosport.api.application.usuario.SesionDeRefrescoInvalidaException;
import co.tecnosport.api.application.usuario.TokensDeSesion;
import co.tecnosport.api.presentation.usuario.dto.IniciarSesionRequest;
import co.tecnosport.api.presentation.usuario.dto.SesionRespuesta;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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
 * Solo login, refresco y cierre de sesión (docs/09-plan-de-arranque.md: sin registro de cliente ni
 * cuenta opcional todavía, eso es Fase 4). El token de acceso va en el cuerpo — vive en memoria en
 * el cliente, nunca en {@code localStorage} (docs/08-seguridad-legal.md); el de refresco va en una
 * cookie {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax}, acotada a {@code /api/v1/auth}
 * para que ningún otro endpoint la reciba sin necesitarla.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionControlador {

  private static final String COOKIE_REFRESCO = "refresco";
  private static final Duration VIGENCIA_REFRESCO = Duration.ofDays(30);

  private final IniciarSesion iniciarSesion;
  private final RefrescarToken refrescarToken;
  private final CerrarSesion cerrarSesion;
  private final TransactionTemplate transaccion;

  public AutenticacionControlador(
      IniciarSesion iniciarSesion,
      RefrescarToken refrescarToken,
      CerrarSesion cerrarSesion,
      PlatformTransactionManager transactionManager) {
    this.iniciarSesion = Objects.requireNonNull(iniciarSesion);
    this.refrescarToken = Objects.requireNonNull(refrescarToken);
    this.cerrarSesion = Objects.requireNonNull(cerrarSesion);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping("/sesion")
  public ResponseEntity<SesionRespuesta> iniciarSesion(@RequestBody IniciarSesionRequest cuerpo) {
    TokensDeSesion tokens =
        transaccion.execute(
            estado ->
                iniciarSesion.ejecutar(new IniciarSesionComando(cuerpo.correo(), cuerpo.clave())));
    return respuestaConCookie(tokens);
  }

  @PostMapping("/refresco")
  public ResponseEntity<SesionRespuesta> refrescar(
      @CookieValue(name = COOKIE_REFRESCO, required = false) String cookieRefresco) {
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
