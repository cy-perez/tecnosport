package co.tecnosport.api.presentation.compartido;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a un servicio de Google que llama a una ruta de {@code /api/v1/internal/} con el token
 * OIDC que pone en {@code Authorization}, y le da su rol. Hay uno por ruta y cada uno con su
 * verificador: Cloud Tasks para la ingesta (ADR-0080), Cloud Scheduler para el tick (ADR-0104).
 *
 * <p>Como {@code FiltroAutenticacionJwt}, no rechaza nada por su cuenta: la cadena de seguridad
 * exige el rol en esa ruta, y sin él responde {@code 401}. Y solo mira su ruta: verificar un token
 * contra las llaves de Google en cada petición del panel sería trabajo, y una llamada de red, para
 * nada.
 */
public abstract class FiltroDeLlamadaInterna extends OncePerRequestFilter {

  private static final String PREFIJO_BEARER = "Bearer ";

  private final String ruta;
  private final String rol;
  private final String quien;
  private final Predicate<String> tokenValido;

  protected FiltroDeLlamadaInterna(
      String ruta, String rol, String quien, Predicate<String> tokenValido) {
    this.ruta = Objects.requireNonNull(ruta);
    this.rol = Objects.requireNonNull(rol);
    this.quien = Objects.requireNonNull(quien);
    this.tokenValido = Objects.requireNonNull(tokenValido);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(ruta);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String cabecera = request.getHeader("Authorization");
    if (cabecera != null
        && cabecera.startsWith(PREFIJO_BEARER)
        && tokenValido.test(cabecera.substring(PREFIJO_BEARER.length()))) {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  quien, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }
    filterChain.doFilter(request, response);
  }
}
