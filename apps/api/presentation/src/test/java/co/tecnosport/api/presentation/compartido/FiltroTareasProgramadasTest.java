package co.tecnosport.api.presentation.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.compartido.VerificadorDeTareasProgramadas;
import co.tecnosport.api.application.proveedores.VerificadorDeTareasDeIngesta;
import co.tecnosport.api.presentation.proveedores.FiltroTareasDeIngesta;
import jakarta.servlet.Filter;
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

class FiltroTareasProgramadasTest {

  private static final String TICK = "/api/v1/internal/tareas/tick";
  private static final String TRAMO = "/api/v1/internal/ingestas/abc/tramo";

  private final List<String> verificados = new ArrayList<>();
  private final VerificadorDeTareasProgramadas soloElBueno =
      token -> {
        verificados.add(token);
        return "BUENO".equals(token);
      };

  @AfterEach
  void limpiarContexto() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void conElTokenDelProgramadorQuedaAutenticadoConSuRol() throws Exception {
    Authentication autenticacion =
        filtrar(new FiltroTareasProgramadas(soloElBueno), TICK, "Bearer BUENO");

    assertThat(autenticacion).isNotNull();
    assertThat(autenticacion.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_" + FiltroTareasProgramadas.ROL);
  }

  @Test
  void conOtroTokenOSinCabeceraPasaSinAutenticar() throws Exception {
    FiltroTareasProgramadas filtro = new FiltroTareasProgramadas(soloElBueno);

    assertThat(filtrar(filtro, TICK, "Bearer MALO")).isNull();
    assertThat(filtrar(filtro, TICK, null)).isNull();
    assertThat(filtrar(filtro, TICK, "Basic BUENO")).isNull();
  }

  /** En la ruta de la ingesta no verifica nada: ese token no es asunto suyo. */
  @Test
  void enLaRutaDeLaIngestaNoVerifica() throws Exception {
    assertThat(filtrar(new FiltroTareasProgramadas(soloElBueno), TRAMO, "Bearer BUENO")).isNull();
    assertThat(verificados).isEmpty();
  }

  /**
   * Y al revés, que es el caso que obligó a acotar la ruta de la ingesta: hasta ADR-0104 su filtro
   * cubría todo {@code /api/v1/internal/}, así que el token de la cola habría abierto el tick.
   */
  @Test
  void elFiltroDeLaIngestaNoAutenticaElTick() throws Exception {
    VerificadorDeTareasDeIngesta colaQueAceptaTodo =
        token -> {
          verificados.add(token);
          return true;
        };

    assertThat(filtrar(new FiltroTareasDeIngesta(colaQueAceptaTodo), TICK, "Bearer BUENO"))
        .isNull();
    assertThat(verificados).isEmpty();
  }

  private static Authentication filtrar(Filter filtro, String ruta, String cabecera)
      throws Exception {
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
    filtro.doFilter(peticion, new MockHttpServletResponse(), cadena);
    return dentro[0];
  }
}
