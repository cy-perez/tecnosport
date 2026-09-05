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
  void usaLaPrimeraIpDeXForwardedForCuandoViene() throws Exception {
    filtro.doFilter(
        peticion("10.0.0.1", "203.0.113.9, 10.0.0.1"),
        new MockHttpServletResponse(),
        (req, res) -> {});

    assertThat(limitador.ultimaClave()).contains("203.0.113.9").doesNotContain("10.0.0.1");
  }

  @Test
  void caeAGetRemoteAddrSinXForwardedFor() throws Exception {
    filtro.doFilter(
        peticion("198.51.100.7", null), new MockHttpServletResponse(), (req, res) -> {});

    assertThat(limitador.ultimaClave()).contains("198.51.100.7");
  }
}
