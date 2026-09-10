package co.tecnosport.api.infrastructure.atencion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.NumeroRadicado;
import co.tecnosport.api.domain.atencion.Prorroga;
import co.tecnosport.api.domain.atencion.Respuesta;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.atencion.TipoSolicitud;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.infrastructure.atencion.entidad.SolicitudAtencionJpaEntity;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Transactional
class RepositorioSolicitudesAtencionJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioSolicitudesAtencionJpa repositorio;
  @Autowired private SolicitudAtencionJpaRepository jpa;

  private static final Instant LLEGADA = Instant.parse("2026-09-10T14:00:00Z");

  private SolicitudAtencion radicada(TipoSolicitud tipo) {
    return SolicitudAtencion.radicar(
        repositorio.siguienteRadicado(2026),
        tipo,
        new CorreoElectronico("cliente@tecnosport.co"),
        null,
        LLEGADA,
        LLEGADA.plusSeconds(3600),
        "admin:1",
        "No me llego el pedido");
  }

  @Test
  void elRecorridoCompletoSobreviveAlViajeDeIdaYVuelta() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.RECLAMO_DATOS);
    repositorio.guardar(solicitud);
    solicitud.responder(
        new Respuesta(LLEGADA.plusSeconds(86_400), "admin:1", "se corrigio el dato"));
    repositorio.guardar(solicitud);
    jpa.flush();

    SolicitudAtencion recuperada = repositorio.buscarPorId(solicitud.id()).orElseThrow();

    assertThat(recuperada.estado()).isEqualTo(EstadoSolicitudAtencion.RESPONDIDA);
    assertThat(recuperada.recibidaEn()).isEqualTo(LLEGADA);
    assertThat(recuperada.radicadaEn()).isEqualTo(LLEGADA.plusSeconds(3600));
    assertThat(recuperada.tipo()).isEqualTo(TipoSolicitud.RECLAMO_DATOS);
    assertThat(recuperada.respuesta().orElseThrow().resumen()).isEqualTo("se corrigio el dato");
  }

  @Test
  void seEncuentraPorElNumeroQueElInteresadoPuedeCitar() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.PETICION);
    repositorio.guardar(solicitud);
    jpa.flush();

    assertThat(repositorio.buscarPorRadicado(solicitud.numeroRadicado()))
        .isPresent()
        .get()
        .extracting(SolicitudAtencion::id)
        .isEqualTo(solicitud.id());
  }

  @Test
  void laProrrogaViajaConSuAvisoYSusMotivos() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.CONSULTA_DATOS);
    Prorroga prorroga =
        new Prorroga(
            LLEGADA.plusSeconds(86_400),
            "admin:1",
            "falta el proveedor",
            LLEGADA.plusSeconds(86_400));
    repositorio.guardar(
        new SolicitudAtencion(
            solicitud.id(),
            solicitud.numeroRadicado(),
            solicitud.tipo(),
            solicitud.correo(),
            null,
            solicitud.recibidaEn(),
            solicitud.radicadaEn(),
            solicitud.radicadaPor(),
            solicitud.asunto(),
            EstadoSolicitudAtencion.PRORROGADA,
            prorroga,
            null));
    jpa.flush();

    Prorroga recuperada =
        repositorio.buscarPorId(solicitud.id()).orElseThrow().prorroga().orElseThrow();

    assertThat(recuperada.motivo()).isEqualTo("falta el proveedor");
    assertThat(recuperada.avisadaEn()).isEqualTo(LLEGADA.plusSeconds(86_400));
  }

  @Test
  void loAbiertoLlegaDeLoMasViejoALoMasNuevoYLoRespondidoNoLlega() {
    SolicitudAtencion vieja = radicada(TipoSolicitud.PETICION);
    repositorio.guardar(vieja);
    SolicitudAtencion nueva =
        SolicitudAtencion.radicar(
            repositorio.siguienteRadicado(2026),
            TipoSolicitud.QUEJA,
            new CorreoElectronico("otro@tecnosport.co"),
            null,
            LLEGADA.plusSeconds(172_800),
            LLEGADA.plusSeconds(172_800),
            "admin:1",
            "Otra cosa");
    repositorio.guardar(nueva);
    SolicitudAtencion respondida = radicada(TipoSolicitud.RECLAMO);
    respondida.responder(new Respuesta(LLEGADA.plusSeconds(3600), "admin:1", "listo"));
    repositorio.guardar(respondida);
    jpa.flush();

    List<SolicitudAtencion> abiertas = repositorio.buscarAbiertas();

    assertThat(abiertas).hasSize(2);
    assertThat(abiertas.get(0).id()).isEqualTo(vieja.id());
    assertThat(abiertas.get(1).id()).isEqualTo(nueva.id());
  }

  /**
   * La base tambien sostiene la invariante, no solo el dominio: una fila PRORROGADA sin el aviso al
   * interesado no se puede rehidratar sin que el agregado reviente, y prefiero que reviente al
   * escribir. Se escribe por debajo del repositorio, a proposito, porque por encima el dominio ya
   * lo impide.
   */
  @Test
  void laBaseRechazaUnaProrrogaSinAviso() {
    assertThatThrownBy(
            () -> {
              jpa.save(
                  new SolicitudAtencionJpaEntity(
                      UUID.randomUUID(),
                      NumeroRadicado.de(2026, 999_999).valor(),
                      TipoSolicitud.CONSULTA_DATOS.name(),
                      "cliente@tecnosport.co",
                      null,
                      LLEGADA,
                      LLEGADA,
                      "admin:1",
                      "Asunto",
                      EstadoSolicitudAtencion.PRORROGADA.name(),
                      LLEGADA,
                      "admin:1",
                      "motivo",
                      null,
                      null,
                      null,
                      null));
              jpa.flush();
            })
        .hasMessageContaining("ck_atencion_prorrogada");
  }

  /** Radicar antes de que llegue la solicitud no es un caso raro: es una fecha mal tecleada. */
  @Test
  void laBaseRechazaUnaRadicacionAnteriorALaLlegada() {
    assertThatThrownBy(
            () -> {
              jpa.save(
                  new SolicitudAtencionJpaEntity(
                      UUID.randomUUID(),
                      NumeroRadicado.de(2026, 999_998).valor(),
                      TipoSolicitud.PETICION.name(),
                      "cliente@tecnosport.co",
                      null,
                      LLEGADA,
                      LLEGADA.minusSeconds(60),
                      "admin:1",
                      "Asunto",
                      EstadoSolicitudAtencion.RADICADA.name(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null));
              jpa.flush();
            })
        .hasMessageContaining("ck_atencion_recibida_antes");
  }

  /**
   * Dos personas radicando a la vez no pueden recibir el mismo numero: el interesado lo cita para
   * preguntar por lo suyo, y dos solicitudes con el mismo radicado hacen imposible saber cual es
   * cual. Sin @Transactional en este metodo, misma razon que en la secuencia del pedido: cada hilo
   * necesita que su llamada haga commit de verdad para que la fila quede serializada entre
   * transacciones reales.
   */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void elRadicadoEsAtomicoBajoConcurrencia() throws Exception {
    int anio = 2099;
    int hilos = 20;
    CountDownLatch listos = new CountDownLatch(hilos);
    Callable<Long> pedirRadicado =
        () -> {
          listos.countDown();
          listos.await();
          return Long.parseLong(repositorio.siguienteRadicado(anio).valor().substring(12));
        };

    ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
    List<Future<Long>> resultados;
    try {
      resultados = ejecutor.invokeAll(Collections.nCopies(hilos, pedirRadicado));
    } finally {
      ejecutor.shutdown();
    }

    Set<Long> secuenciales = ConcurrentHashMap.newKeySet();
    for (Future<Long> resultado : resultados) {
      secuenciales.add(resultado.get());
    }

    assertThat(secuenciales)
        .isEqualTo(LongStream.rangeClosed(1, hilos).boxed().collect(Collectors.toSet()));
  }
}
