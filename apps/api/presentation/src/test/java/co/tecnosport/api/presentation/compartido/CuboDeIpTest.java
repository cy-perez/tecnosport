package co.tecnosport.api.presentation.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CuboDeIpTest {

  @Test
  void unaIpv4SeCuentaEntera() {
    assertThat(CuboDeIp.de("190.24.10.5")).isEqualTo("190.24.10.5");
  }

  /**
   * El ataque: el mismo /64 con otra dirección en cada petición tiene que caer en el mismo cubo.
   */
  @Test
  void dosDireccionesDelMismoSlash64SonElMismoCubo() {
    assertThat(CuboDeIp.de("2800:e2:1f00:12::1a"))
        .isEqualTo(CuboDeIp.de("2800:e2:1f00:12:dead:beef:0:7"))
        .isEqualTo("2800:e2:1f00:12::/64");
  }

  @Test
  void otroSlash64EsOtroCubo() {
    assertThat(CuboDeIp.de("2800:e2:1f00:13::1a")).isNotEqualTo(CuboDeIp.de("2800:e2:1f00:12::1a"));
  }

  /** Mayúsculas y ceros a la izquierda no pueden abrir cubos nuevos. */
  @Test
  void normalizaMayusculasYCerosALaIzquierda() {
    assertThat(CuboDeIp.de("2800:00E2:1F00:0012:0:0:0:1")).isEqualTo("2800:e2:1f00:12::/64");
  }

  @Test
  void expandeElDobleDosPuntosAlPrincipio() {
    assertThat(CuboDeIp.de("::1")).isEqualTo("0:0:0:0::/64");
  }

  @Test
  void aceptaUnaIpv4IncrustadaAlFinal() {
    assertThat(CuboDeIp.de("2800:e2:1f00:12::ffff:190.24.10.5")).isEqualTo("2800:e2:1f00:12::/64");
  }

  /**
   * Lo que no se deja expandir se cuenta entero: adivinar un prefijo podría juntar a desconocidos.
   */
  @Test
  void loQueNoTieneFormaDeIpv6SeCuentaEntero() {
    assertThat(CuboDeIp.de("1::2::3")).isEqualTo("1::2::3");
    assertThat(CuboDeIp.de("1:2:3")).isEqualTo("1:2:3");
  }
}
