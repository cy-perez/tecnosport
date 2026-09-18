package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * El reclamo del aviso de un sobrecosto, contra la base real. Es lo único de este vigilante que un
 * doble de prueba no puede demostrar: que <strong>el segundo reclamo de la misma clave
 * pierde</strong>, y que lo decide la restricción de la base y no una comprobación nuestra. Escrito
 * como un {@code select} y después un {@code insert}, dos instancias leerían las dos "no se ha
 * avisado" y mandarían las dos.
 *
 * <p>Y a diferencia del aviso de la bandeja, aquí <strong>no hay forma de volver a
 * ganarlo</strong>: un cobro extra ya ocurrió y no tiene novedades posteriores. Esa asimetría es la
 * razón de que sea una tabla aparte, así que tiene su propia prueba.
 */
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioAvisosDeSobrecostoJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioAvisosDeSobrecostoJpa avisos;

  private static final Instant AHORA = Instant.parse("2026-09-18T15:00:00Z");

  @Test
  void elPrimerReclamoGanaYElSegundoPierde() {
    String clave = "shp-1|ExtraCharge::Overweight|8400|2026-09-17T21:30:35Z";

    assertThat(avisos.reclamarAviso(clave, AHORA)).isTrue();
    assertThat(avisos.reclamarAviso(clave, AHORA.plusSeconds(86_400))).isFalse();
  }

  /** Dos cobros distintos no se tapan entre ellos, ni siendo del mismo envío. */
  @Test
  void dosCobrosDelMismoEnvioSeAvisanPorSeparado() {
    assertThat(avisos.reclamarAviso("shp-1|ExtraCharge::Overweight|8400|sin-fecha", AHORA))
        .isTrue();
    assertThat(avisos.reclamarAviso("shp-1|ExtraCharge::Overweight|12900|sin-fecha", AHORA))
        .isTrue();
  }
}
