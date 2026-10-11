package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.proveedores.VerificadorDeTareasDeIngesta;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a Cloud Tasks en {@code /api/v1/internal/**} con el token OIDC que pone en {@code
 * Authorization} (ADR-0080), y le da el rol {@value #ROL}. Como {@code FiltroAutenticacionJwt}, no
 * rechaza nada por su cuenta: la cadena de seguridad exige el rol en esa ruta, y sin él responde
 * {@code 401}.
 *
 * <p>Solo mira esa ruta: verificar un token contra las llaves de Google en cada petición del panel
 * sería trabajo, y una llamada de red, para nada.
 */
public class FiltroTareasDeIngesta extends OncePerRequestFilter {

  public static final String ROL = "TAREAS_DE_INGESTA";
  public static final String RUTA = "/api/v1/internal/";

  private static final String PREFIJO_BEARER = "Bearer ";

  private final VerificadorDeTareasDeIngesta verificador;

  public FiltroTareasDeIngesta(VerificadorDeTareasDeIngesta verificador) {
    this.verificador = Objects.requireNonNull(verificador);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(RUTA);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String cabecera = request.getHeader("Authorization");
    if (cabecera != null
        && cabecera.startsWith(PREFIJO_BEARER)
        && verificador.esDeLaCola(cabecera.substring(PREFIJO_BEARER.length()))) {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  "cloud-tasks", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROL))));
    }
    filterChain.doFilter(request, response);
  }
}
