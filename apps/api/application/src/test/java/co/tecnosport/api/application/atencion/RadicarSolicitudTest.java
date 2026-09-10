package co.tecnosport.api.application.atencion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.atencion.TipoSolicitud;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class RadicarSolicitudTest {

  private static final Instant LLEGADA =
      ZonedDateTime.of(2026, 9, 10, 9, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();
  private static final Instant AHORA =
      ZonedDateTime.of(2026, 9, 12, 15, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

  private final RepositorioSolicitudesAtencionFalso repositorio =
      new RepositorioSolicitudesAtencionFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private RadicarSolicitud casoDeUso() {
    return new RadicarSolicitud(
        repositorio, correos, new TextosDeCorreoFalso(), new RelojFalso(AHORA));
  }

  private RadicarSolicitudComando comando(TipoSolicitud tipo, Instant recibidaEn) {
    return new RadicarSolicitudComando(
        tipo, "cliente@tecnosport.co", null, recibidaEn, "No me llego el pedido", "admin:1");
  }

  @Test
  void radicaConNumeroYGuardaLasDosFechas() {
    SolicitudAtencion solicitud = casoDeUso().ejecutar(comando(TipoSolicitud.PETICION, LLEGADA));

    assertEquals("TS-PQR-2026-000001", solicitud.numeroRadicado().valor());
    assertEquals(EstadoSolicitudAtencion.RADICADA, solicitud.estado());
    assertEquals(LLEGADA, solicitud.recibidaEn(), "cuando llego");
    assertEquals(AHORA, solicitud.radicadaEn(), "cuando alguien la registro");
  }

  /**
   * Sin acuse, el numero de radicado existiria solo del lado del negocio, y el interesado no
   * tendria con que preguntar por lo que pidio. El correo lleva el numero, no un "gracias".
   */
  @Test
  void elAcuseLlevaElNumeroDeRadicado() {
    SolicitudAtencion solicitud = casoDeUso().ejecutar(comando(TipoSolicitud.RECLAMO, LLEGADA));

    assertEquals(1, correos.enviados().size());
    assertTrue(
        correos.enviados().get(0).cuerpoHtml().contains(solicitud.numeroRadicado().valor()),
        "el cuerpo del acuse cita el radicado");
    assertTrue(
        correos.enviados().get(0).asunto().contains(solicitud.numeroRadicado().valor()),
        "el asunto tambien, para que se encuentre buscando el numero");
  }

  @Test
  void elRadicadoUsaElAnioEnQueLlegoYNoElDeHoy() {
    Instant diciembre = ZonedDateTime.of(2025, 12, 30, 9, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

    SolicitudAtencion solicitud = casoDeUso().ejecutar(comando(TipoSolicitud.PETICION, diciembre));

    assertTrue(
        solicitud.numeroRadicado().valor().startsWith("TS-PQR-2025-"),
        "pertenece al ano en que llego, igual que su plazo");
  }

  @Test
  void sinFechaDeLlegadaSeAsumeQueLlegoAhora() {
    SolicitudAtencion solicitud = casoDeUso().ejecutar(comando(TipoSolicitud.PETICION, null));

    assertEquals(AHORA, solicitud.recibidaEn());
  }

  @Test
  void noSePuedeRadicarAlgoQueLlegaraEnElFuturo() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> casoDeUso().ejecutar(comando(TipoSolicitud.PETICION, AHORA.plusSeconds(3600))));
  }
}
