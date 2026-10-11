package co.tecnosport.api.presentation.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.VerificadorDeTareasDeIngesta;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class FiltroTareasDeIngestaTest {

  private static final String RUTA = "/api/v1/internal/ingestas/abc/tramo";

  private final List<String> verificados = new ArrayList<>();
  private final VerificadorDeTareasDeIngesta soloElBueno =
      token -> {
        verificados.add(token);
        return "BUENO".equals(token);
      };

  @AfterEach
  void limpiarContexto() {
    SecurityContextHolder.clearContext();
  }

  private Authentication filtrar(String ruta, String cabecera) throws Exception {
    MockHttpServletRequest peticion = new MockHttpServletRequest("POST", ruta);
    peticion.setRequestURI(ruta);
    if (cabecera != null) {
      peticion.addHeader("Authorization", cabecera);
    }
    Authentication[] dentro = new Authentication[1];
    MockFilterChain cadena =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            dentro[0] = SecurityContextHolder.getContext().getAuthentication();
          }
        };
    new FiltroTareasDeIngesta(soloElBueno)
        .doFilter(peticion, new MockHttpServletResponse(), cadena);
    return dentro[0];
  }

  @Test
  void conElTokenDeLaColaQuedaAutenticadoConSuRol() throws Exception {
    Authentication autenticacion = filtrar(RUTA, "Bearer BUENO");

    assertThat(autenticacion).isNotNull();
    assertThat(autenticacion.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_" + FiltroTareasDeIngesta.ROL);
  }

  /** No rechaza por su cuenta: deja pasar sin autenticar, y la cadena responde 401 por el rol. */
  @Test
  void conOtroTokenOSinCabeceraPasaSinAutenticar() throws Exception {
    assertThat(filtrar(RUTA, "Bearer MALO")).isNull();
    assertThat(filtrar(RUTA, null)).isNull();
    assertThat(filtrar(RUTA, "Basic BUENO")).isNull();
  }

  /** Fuera de la ruta interna no verifica nada: sería una llamada a Google por cada petición. */
  @Test
  void fueraDeLaRutaInternaNoVerifica() throws Exception {
    assertThat(filtrar("/api/v1/admin/ingestas", "Bearer BUENO")).isNull();
    assertThat(verificados).isEmpty();
  }
}
