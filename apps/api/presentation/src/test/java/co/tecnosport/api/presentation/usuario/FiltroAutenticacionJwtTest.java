package co.tecnosport.api.presentation.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.usuario.ClaimsAcceso;
import co.tecnosport.api.application.usuario.VerificadorDeTokens;
import co.tecnosport.api.domain.usuario.Rol;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class FiltroAutenticacionJwtTest {

  @AfterEach
  void limpiarContexto() {
    SecurityContextHolder.clearContext();
  }

  private static final class VerificadorDeTokensDobleDePrueba implements VerificadorDeTokens {

    private final Optional<ClaimsAcceso> resultado;

    VerificadorDeTokensDobleDePrueba(Optional<ClaimsAcceso> resultado) {
      this.resultado = resultado;
    }

    @Override
    public Optional<ClaimsAcceso> verificar(String jwt) {
      return resultado;
    }
  }

  @Test
  void unTokenValidoAutenticaEnElContexto() throws Exception {
    UUID usuarioId = UUID.randomUUID();
    FiltroAutenticacionJwt filtro =
        new FiltroAutenticacionJwt(
            new VerificadorDeTokensDobleDePrueba(
                Optional.of(new ClaimsAcceso(usuarioId, Rol.ADMIN))));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer token-valido");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain cadena = new MockFilterChain();

    filtro.doFilter(request, response, cadena);

    Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
    assertEquals(usuarioId, autenticacion.getPrincipal());
    assertTrue(
        autenticacion.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals));
  }

  @Test
  void sinCabeceraNoAutenticaYDejaPasar() throws Exception {
    FiltroAutenticacionJwt filtro =
        new FiltroAutenticacionJwt(new VerificadorDeTokensDobleDePrueba(Optional.empty()));
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain cadena = new MockFilterChain();

    filtro.doFilter(request, response, cadena);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void unTokenInvalidoNoAutenticaYDejaPasar() throws Exception {
    FiltroAutenticacionJwt filtro =
        new FiltroAutenticacionJwt(new VerificadorDeTokensDobleDePrueba(Optional.empty()));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer token-invalido");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain cadena = new MockFilterChain();

    filtro.doFilter(request, response, cadena);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }
}
