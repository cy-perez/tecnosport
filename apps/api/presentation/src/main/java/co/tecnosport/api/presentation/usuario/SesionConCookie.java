package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.TokensDeSesion;
import co.tecnosport.api.presentation.usuario.dto.SesionRespuesta;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

/**
 * La respuesta de una sesión recién abierta: el token de acceso en el cuerpo y el de refresco en la
 * cookie. Está aparte desde que hay dos puertas que abren sesión —la clave y Google (ADR-0074)— y
 * la cookie tiene que ser idéntica en las dos: una diferencia en el {@code path} o en {@code
 * SameSite} dejaría a quien entró con Google sin poder refrescar.
 */
final class SesionConCookie {

  static final String COOKIE_REFRESCO = "refresco";
  static final Duration VIGENCIA_REFRESCO = Duration.ofDays(30);

  private SesionConCookie() {}

  static ResponseEntity<SesionRespuesta> respuesta(TokensDeSesion tokens) {
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookieDe(tokens).toString())
        .body(cuerpo(tokens));
  }

  /** La cookie de una sesión recién abierta, para las respuestas que llevan más que la sesión. */
  static ResponseCookie cookieDe(TokensDeSesion tokens) {
    return cookie(tokens.refreshTokenId().toString(), VIGENCIA_REFRESCO);
  }

  static SesionRespuesta cuerpo(TokensDeSesion tokens) {
    return new SesionRespuesta(
        tokens.usuarioId(),
        tokens.rol().name(),
        tokens.accessToken(),
        tokens.segundoFactorPorActivar());
  }

  static ResponseCookie cookie(String valor, Duration vigencia) {
    return ResponseCookie.from(COOKIE_REFRESCO, valor)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(vigencia)
        .build();
  }
}
