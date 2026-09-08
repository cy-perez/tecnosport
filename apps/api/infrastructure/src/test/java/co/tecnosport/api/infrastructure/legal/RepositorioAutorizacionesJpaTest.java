package co.tecnosport.api.infrastructure.legal;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.legal.OrigenAutorizacion;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Transactional
class RepositorioAutorizacionesJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioAutorizacionesJpa repositorio;

  private static final String VERSION = "2026-09-07";

  private CorreoElectronico correoNuevo() {
    return new CorreoElectronico("comprador-" + UUID.randomUUID() + "@tecnosport.co");
  }

  @Test
  void laConstanciaDelRegistroVuelveCompletaDeLaBase() {
    CorreoElectronico correo = correoNuevo();
    UUID usuarioId = UUID.randomUUID();
    Instant otorgadaEn = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    AutorizacionDatos autorizacion =
        AutorizacionDatos.enRegistro(true, correo, usuarioId, VERSION, "190.24.10.5", otorgadaEn);

    repositorio.guardar(autorizacion);

    AutorizacionDatos recuperada = repositorio.buscarPorCorreo(correo).getFirst();
    assertThat(recuperada.id()).isEqualTo(autorizacion.id());
    assertThat(recuperada.correo()).isEqualTo(correo);
    assertThat(recuperada.usuarioId()).isEqualTo(Optional.of(usuarioId));
    assertThat(recuperada.versionPolitica()).isEqualTo(VERSION);
    assertThat(recuperada.direccionIp()).isEqualTo("190.24.10.5");
    assertThat(recuperada.origen()).isEqualTo(OrigenAutorizacion.REGISTRO);
    assertThat(recuperada.otorgadaEn()).isEqualTo(otorgadaEn);
  }

  /**
   * El caso que obligó a que la columna no tenga FK a usuario: se compra sin cuenta y la constancia
   * vale igual. Si el id de usuario fuera obligatorio en la base, esta compra no existiría.
   */
  @Test
  void laConstanciaDelCheckoutSinCuentaSeGuardaSinUsuario() {
    CorreoElectronico correo = correoNuevo();

    repositorio.guardar(
        AutorizacionDatos.enCheckout(true, correo, null, VERSION, "190.24.10.5", Instant.now()));

    AutorizacionDatos recuperada = repositorio.buscarPorCorreo(correo).getFirst();
    assertThat(recuperada.usuarioId()).isEmpty();
    assertThat(recuperada.origen()).isEqualTo(OrigenAutorizacion.CHECKOUT);
  }

  /**
   * Es una bitácora, no un estado: autorizar de nuevo agrega una fila y no pisa la anterior. Sin
   * esto no habría forma de demostrar qué versión aceptó alguien antes de que el texto cambiara.
   */
  @Test
  void autorizarDosVecesDejaDosConstancias() {
    CorreoElectronico correo = correoNuevo();
    Instant primera = Instant.parse("2026-09-01T10:00:00Z");
    Instant segunda = Instant.parse("2026-09-07T10:00:00Z");

    repositorio.guardar(
        AutorizacionDatos.enCheckout(true, correo, null, "2026-01-01", "190.24.10.5", primera));
    repositorio.guardar(
        AutorizacionDatos.enCheckout(true, correo, null, VERSION, "190.24.10.6", segunda));

    List<AutorizacionDatos> constancias = repositorio.buscarPorCorreo(correo);
    assertThat(constancias).hasSize(2);
    assertThat(constancias.stream().map(AutorizacionDatos::versionPolitica))
        .containsExactly(VERSION, "2026-01-01");
  }

  @Test
  void sinConstanciasDevuelveVacio() {
    assertThat(repositorio.buscarPorCorreo(correoNuevo())).isEmpty();
  }

  /**
   * direccion_ip es text y no varchar(45) a propósito (ADR-0019: una columna estrecha ya reventó
   * una vez con un valor de fuera). Una cabecera de proxy larga no puede tumbar la constancia.
   */
  @Test
  void unaIpAbsurdamenteLargaNoRompeLaConstancia() {
    CorreoElectronico correo = correoNuevo();
    String ipLarga = "203.0.113.7".repeat(30);

    repositorio.guardar(
        AutorizacionDatos.enCheckout(true, correo, null, VERSION, ipLarga, Instant.now()));

    assertThat(repositorio.buscarPorCorreo(correo).getFirst().direccionIp()).isEqualTo(ipLarga);
  }
}
