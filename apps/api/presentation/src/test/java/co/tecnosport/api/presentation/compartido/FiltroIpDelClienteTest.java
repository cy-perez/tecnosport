package co.tecnosport.api.presentation.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Los casos salen de lo que se midió contra dev el 25 de octubre de 2026 (ADR-0087): once consultas
 * de seguimiento con un {@code X-Forwarded-For} distinto cada una no recibieron nunca un 429, ni
 * directo a la API ni por el proxy de la web.
 */
class FiltroIpDelClienteTest {

  private static final String CLAVE = "clave-del-proxy-de-prueba";

  private final FiltroIpDelCliente directo = new FiltroIpDelCliente(null, 1);
  private final FiltroIpDelCliente conProxy = new FiltroIpDelCliente(CLAVE, 1);

  private MockHttpServletRequest peticion(String adelante) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("169.254.1.1");
    if (adelante != null) {
      request.addHeader("X-Forwarded-For", adelante);
    }
    return request;
  }

  private MockHttpServletRequest delProxy(String clave, String ip) {
    // Lo que ve la API cuando la petición viene del proxy: su propio frente solo agrega la IP de
    // salida de la web, que comparten todos los visitantes.
    MockHttpServletRequest request = peticion("34.96.0.7");
    request.addHeader("X-Clave-Proxy", clave);
    request.addHeader("X-Ip-Cliente", ip);
    return request;
  }

  /** El ataque medido: lo que se inventa el cliente va delante, el frente agrega la real detrás. */
  @Test
  void directoSeQuedaConLoQueAgregoElFrenteYNoConLoInventado() {
    assertThat(directo.resolver(peticion("203.0.113.7, 190.24.10.5"))).isEqualTo("190.24.10.5");
  }

  @Test
  void directoSinNadaInventadoTambienEsElUltimo() {
    assertThat(directo.resolver(peticion("190.24.10.5"))).isEqualTo("190.24.10.5");
  }

  /** El día del balanceador: cliente real, y detrás la IP del propio balanceador. */
  @Test
  void conDosSaltosConfiablesEsElPenultimo() {
    FiltroIpDelCliente conBalanceador = new FiltroIpDelCliente(null, 2);

    assertThat(conBalanceador.resolver(peticion("203.0.113.7, 190.24.10.5, 34.120.0.1")))
        .isEqualTo("190.24.10.5");
  }

  /**
   * Una lista más corta que los saltos de la infraestructura no la escribió ella entera: no hay un
   * valor de fiar, y adivinar cuál sería volver al primero.
   */
  @Test
  void unaListaMasCortaQueLosSaltosCaeALaDireccionRemota() {
    FiltroIpDelCliente conBalanceador = new FiltroIpDelCliente(null, 2);

    assertThat(conBalanceador.resolver(peticion("190.24.10.5"))).isEqualTo("169.254.1.1");
  }

  @Test
  void delProxyConLaClaveCreeLaIpQueManda() {
    assertThat(conProxy.resolver(delProxy(CLAVE, "190.24.10.5"))).isEqualTo("190.24.10.5");
  }

  @Test
  void conLaClaveEquivocadaIgnoraLaIpQueManda() {
    assertThat(conProxy.resolver(delProxy("otra-clave", "203.0.113.7"))).isEqualTo("34.96.0.7");
  }

  @Test
  void sinLaCabeceraDeLaClaveIgnoraLaIpQueManda() {
    MockHttpServletRequest request = peticion("34.96.0.7");
    request.addHeader("X-Ip-Cliente", "203.0.113.7");

    assertThat(conProxy.resolver(request)).isEqualTo("34.96.0.7");
  }

  /**
   * Sin clave configurada no hay proxy de fiar, y eso incluye a quien manda una clave vacía con la
   * esperanza de que coincida con la que no hay.
   */
  @Test
  void sinClaveConfiguradaNingunaClaveAbreLaPuerta() {
    FiltroIpDelCliente sinClave = new FiltroIpDelCliente("  ", 1);

    assertThat(sinClave.resolver(delProxy("", "203.0.113.7"))).isEqualTo("34.96.0.7");
    assertThat(directo.resolver(delProxy("", "203.0.113.7"))).isEqualTo("34.96.0.7");
  }

  /** Va a la constancia de autorización de datos y a la llave del limitador: solo una IP. */
  @Test
  void unaIpDelProxySinFormaDeIpNoSeCree() {
    assertThat(conProxy.resolver(delProxy(CLAVE, "<script>"))).isEqualTo("34.96.0.7");
  }

  @Test
  void recortaLosEspaciosYAceptaIpv6() {
    assertThat(directo.resolver(peticion("203.0.113.7,   2800:e2:1f00::1a  ")))
        .isEqualTo("2800:e2:1f00::1a");
  }

  @Test
  void unUltimoValorVacioCaeALaDireccionRemota() {
    assertThat(directo.resolver(peticion("190.24.10.5, "))).isEqualTo("169.254.1.1");
  }

  @Test
  void sinCabeceraEsLaDireccionRemota() {
    assertThat(directo.resolver(peticion(null))).isEqualTo("169.254.1.1");
  }

  @Test
  void dejaLaIpEnLaPeticionParaIpDelCliente() throws Exception {
    MockHttpServletRequest request = peticion("203.0.113.7, 190.24.10.5");
    AtomicReference<String> vista = new AtomicReference<>();

    directo.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> vista.set(IpDelCliente.de((MockHttpServletRequest) req)));

    assertThat(vista.get()).isEqualTo("190.24.10.5");
  }

  /** Dos líneas de la cabecera: la primera es entera del cliente, y su último valor también. */
  @Test
  void conVariasLineasDeLaCabeceraTomaElFinalDeLaUltima() {
    MockHttpServletRequest request = peticion("203.0.113.7, 203.0.113.8");
    request.addHeader("X-Forwarded-For", "190.24.10.5");

    assertThat(directo.resolver(request)).isEqualTo("190.24.10.5");
  }

  @Test
  void rechazaMenosDeUnSalto() {
    assertThatThrownBy(() -> new FiltroIpDelCliente(null, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
