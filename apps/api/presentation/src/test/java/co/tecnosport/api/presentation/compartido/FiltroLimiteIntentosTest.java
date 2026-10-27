package co.tecnosport.api.presentation.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.compartido.Reloj;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltroLimiteIntentosTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");
  private static final Reloj RELOJ_FIJO = () -> AHORA;

  private final LimitadorDeIntentosFalso limitador = new LimitadorDeIntentosFalso();
  private final FiltroLimiteIntentos filtro =
      new FiltroLimiteIntentos(limitador, RELOJ_FIJO, 10, Duration.ofMinutes(15));

  private MockHttpServletRequest peticion(String ipRemota, String adelante) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/sesion");
    request.setRemoteAddr(ipRemota);
    if (adelante != null) {
      request.addHeader("X-Forwarded-For", adelante);
    }
    return request;
  }

  private FilterChain cadenaContando(AtomicInteger contador) {
    return (req, res) -> contador.incrementAndGet();
  }

  @Test
  void dentroDelLimiteDejaPasar() throws Exception {
    AtomicInteger llamadasACadena = new AtomicInteger();

    filtro.doFilter(
        peticion("203.0.113.5", null),
        new MockHttpServletResponse(),
        cadenaContando(llamadasACadena));

    assertThat(llamadasACadena.get()).isEqualTo(1);
  }

  @Test
  void alExcederElLimiteDevuelve429SinLlamarLaCadena() throws Exception {
    limitador.denegarSiguiente();
    AtomicInteger llamadasACadena = new AtomicInteger();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filtro.doFilter(peticion("203.0.113.5", null), response, cadenaContando(llamadasACadena));

    assertThat(llamadasACadena.get()).isZero();
    assertThat(response.getStatus()).isEqualTo(429);
    // charset explícito: sin él, getWriter() usaría ISO-8859-1 por defecto (spec de servlets) y
    // las tildes del cuerpo saldrían corruptas — encontrado a mano contra bootRun real.
    assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
    assertThat(response.getContentAsString())
        .contains("LIMITE_DE_INTENTOS_EXCEDIDO")
        .contains("Límite de intentos excedido");
  }

  @Test
  void usaLaIpQueDejoResueltaElFiltroDeIp() throws Exception {
    MockHttpServletRequest request = peticion("10.0.0.1", null);
    request.setAttribute(IpDelCliente.ATRIBUTO, "203.0.113.9");

    filtro.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

    assertThat(limitador.ultimaClave()).contains("203.0.113.9").doesNotContain("10.0.0.1");
  }

  /**
   * ADR-0087: el primer valor de la cabecera lo escribe el cliente. Si este filtro volviera a
   * leerla por su cuenta, una cabecera distinta en cada petición estrenaría cubo cada vez.
   */
  @Test
  void noLeCreeAXForwardedForPorSuCuenta() throws Exception {
    filtro.doFilter(
        peticion("10.0.0.1", "203.0.113.9, 10.0.0.1"),
        new MockHttpServletResponse(),
        (req, res) -> {});

    assertThat(limitador.ultimaClave()).contains("10.0.0.1").doesNotContain("203.0.113.9");
  }

  /** Una IPv6 nueva del mismo /64 no estrena presupuesto (CuboDeIp). */
  @Test
  void cuentaUnaIpv6PorSuSlash64() throws Exception {
    MockHttpServletRequest primera = peticion("10.0.0.1", null);
    primera.setAttribute(IpDelCliente.ATRIBUTO, "2800:e2:1f00:12::1");
    MockHttpServletRequest segunda = peticion("10.0.0.1", null);
    segunda.setAttribute(IpDelCliente.ATRIBUTO, "2800:e2:1f00:12::2");

    filtro.doFilter(primera, new MockHttpServletResponse(), (req, res) -> {});
    String clavePrimera = limitador.ultimaClave();
    filtro.doFilter(segunda, new MockHttpServletResponse(), (req, res) -> {});

    assertThat(limitador.ultimaClave()).isEqualTo(clavePrimera).endsWith("2800:e2:1f00:12::/64");
  }

  @Test
  void caeAGetRemoteAddrSinXForwardedFor() throws Exception {
    filtro.doFilter(
        peticion("198.51.100.7", null), new MockHttpServletResponse(), (req, res) -> {});

    assertThat(limitador.ultimaClave()).contains("198.51.100.7");
  }

  /**
   * La URI cruda no se decodifica: con ella en la llave, escapar un carácter estrenaba presupuesto
   * mientras el contenedor seguía llevando la petición al mismo endpoint.
   */
  @Test
  void laMismaRutaConUnCaracterEscapadoCompartePresupuesto() throws Exception {
    String claveLimpia = claveDe("/api/v1/auth/sesion");

    assertThat(claveDe("/api/v1/auth/%73esion")).isEqualTo(claveLimpia);
    assertThat(claveDe("/api/v1/auth/sesion;jsessionid=x")).isEqualTo(claveLimpia);
    assertThat(claveDe("/api/v1//auth/./sesion")).isEqualTo(claveLimpia);
    assertThat(claveLimpia).isEqualTo("ip:/api/v1/auth/sesion:203.0.113.5");
  }

  /** El diseño quiere un presupuesto por endpoint: el registro no gasta el del inicio de sesión. */
  @Test
  void cadaEndpointConservaSuPropioPresupuesto() throws Exception {
    assertThat(claveDe("/api/v1/auth/registro")).isNotEqualTo(claveDe("/api/v1/auth/sesion"));
  }

  private String claveDe(String uri) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setRemoteAddr("203.0.113.5");
    filtro.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    return limitador.ultimaClave();
  }
}
