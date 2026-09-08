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
  void sinCabeceraUsaLaDireccionRemota() {
    assertThat(IpDelCliente.de(peticion())).isEqualTo("10.0.0.1");
  }

  /** Detrás del balanceador, getRemoteAddr() es el balanceador y no sirve de nada. */
  @Test
  void conCabeceraPrefiereLaDelCliente() {
    MockHttpServletRequest request = peticion();
    request.addHeader("X-Forwarded-For", "190.24.10.5");

    assertThat(IpDelCliente.de(request)).isEqualTo("190.24.10.5");
  }

  /** El primero de la lista es el cliente original; los demás son proxies intermedios. */
  @Test
  void conVariosSaltosSeQuedaConElPrimero() {
    MockHttpServletRequest request = peticion();
    request.addHeader("X-Forwarded-For", "190.24.10.5, 34.120.0.1, 10.4.0.2");

    assertThat(IpDelCliente.de(request)).isEqualTo("190.24.10.5");
  }

  @Test
  void recortaLosEspaciosDelValor() {
    MockHttpServletRequest request = peticion();
    request.addHeader("X-Forwarded-For", "   190.24.10.5   ");

    assertThat(IpDelCliente.de(request)).isEqualTo("190.24.10.5");
  }

  @Test
  void conCabeceraEnBlancoCaeALaDireccionRemota() {
    MockHttpServletRequest request = peticion();
    request.addHeader("X-Forwarded-For", "   ");

    assertThat(IpDelCliente.de(request)).isEqualTo("10.0.0.1");
  }

  /**
   * Una cabecera que empieza por coma dejaría un valor vacío: como llave del límite de intentos
   * metería a todo el mundo en el mismo cubo, y como constancia de autorización no diría nada.
   */
  @Test
  void conPrimerValorVacioCaeALaDireccionRemota() {
    MockHttpServletRequest request = peticion();
    request.addHeader("X-Forwarded-For", ", 34.120.0.1");

    assertThat(IpDelCliente.de(request)).isEqualTo("10.0.0.1");
  }
}
