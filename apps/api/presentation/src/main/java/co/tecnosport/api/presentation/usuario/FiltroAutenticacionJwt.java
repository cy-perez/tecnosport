package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.ClaimsAcceso;
import co.tecnosport.api.application.usuario.VerificadorDeTokens;
import co.tecnosport.api.domain.usuario.AlcanceDeAcceso;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee {@code Authorization: Bearer <jwt>}, lo verifica y, si es válido, deja al usuario autenticado
 * en el {@code SecurityContext} para que {@code SecurityFilterChain} (bootstrap) decida por rol.
 * Sin cabecera, o con una inválida: deja pasar sin autenticar — la propia cadena de seguridad
 * decide si esa ruta lo exige, este filtro no rechaza nada por su cuenta.
 *
 * <p>No es {@code @Component}: se registra a mano en {@code SecurityFilterChain} (bootstrap), igual
 * que {@code FiltroIdempotencia} — un filtro de autenticación es parte de la cadena de seguridad,
 * no un {@code Filter} de servlet genérico.
 */
public class FiltroAutenticacionJwt extends OncePerRequestFilter {

  private static final String PREFIJO_BEARER = "Bearer ";

  /**
   * ADR-0096. La autoridad de un administrador cuya sesión solo sirve para activar el segundo
   * factor: no es {@code ROLE_ADMIN}, así que {@code /api/v1/admin/**} le responde 403, y {@code
   * ConfiguracionSeguridad} la deja entrar a {@code /api/v1/auth/segundo-factor/**}.
   */
  public static final String ROL_ADMIN_POR_ACTIVAR = "ADMIN_POR_ACTIVAR";

  private final VerificadorDeTokens verificadorDeTokens;

  public FiltroAutenticacionJwt(VerificadorDeTokens verificadorDeTokens) {
    this.verificadorDeTokens = Objects.requireNonNull(verificadorDeTokens);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String cabecera = request.getHeader("Authorization");
    if (cabecera != null && cabecera.startsWith(PREFIJO_BEARER)) {
      String token = cabecera.substring(PREFIJO_BEARER.length());
      Optional<ClaimsAcceso> claims = verificadorDeTokens.verificar(token);
      claims.ifPresent(this::autenticar);
    }
    filterChain.doFilter(request, response);
  }

  private void autenticar(ClaimsAcceso claims) {
    String rol =
        claims.alcance() == AlcanceDeAcceso.SOLO_ACTIVAR_SEGUNDO_FACTOR
            ? ROL_ADMIN_POR_ACTIVAR
            : claims.rol().name();
    List<GrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
    var autenticacion =
        new UsernamePasswordAuthenticationToken(claims.usuarioId(), null, autoridades);
    SecurityContextHolder.getContext().setAuthentication(autenticacion);
  }
}
