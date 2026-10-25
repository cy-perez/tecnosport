package co.tecnosport.api.presentation.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IpDelClienteTest {

  private MockHttpServletRequest peticion() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.1");
    return request;
  }

  @Test
  void devuelveLaQueDejoResueltaElFiltro() {
    MockHttpServletRequest request = peticion();
    request.setAttribute(IpDelCliente.ATRIBUTO, "190.24.10.5");

    assertThat(IpDelCliente.de(request)).isEqualTo("190.24.10.5");
  }

  @Test
  void sinFiltroDelanteUsaLaDireccionRemota() {
    assertThat(IpDelCliente.de(peticion())).isEqualTo("10.0.0.1");
  }

  /**
   * Es lo que hacía hasta ADR-0087 y lo que no puede volver a hacer: sin el filtro, la cabecera es
   * un texto del cliente.
   */
  @Test
  void sinFiltroDelanteNoLeCreeALaCabecera() {
    MockHttpServletRequest request = peticion();
    request.addHeader("X-Forwarded-For", "190.24.10.5");

    assertThat(IpDelCliente.de(request)).isEqualTo("10.0.0.1");
  }
}
