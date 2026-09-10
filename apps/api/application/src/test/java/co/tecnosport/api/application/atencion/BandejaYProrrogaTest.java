package co.tecnosport.api.application.atencion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.PlazosDeAtencion;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.atencion.TipoSolicitud;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BandejaYProrrogaTest {

  private static final Instant LLEGADA =
      ZonedDateTime.of(2026, 9, 10, 9, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

  private static final PlazosDeAtencion PLAZOS =
      PlazosDeAtencion.de(
          new PlazosDeAtencion.PlazoHabil(10, 5),
          new PlazosDeAtencion.PlazoHabil(15, 8),
          new PlazosDeAtencion.PlazoHabil(15, 0));

  private static final CalendarioHabil SIN_FESTIVOS = CalendarioHabil.sinFestivosCargados();

  private final RepositorioSolicitudesAtencionFalso repositorio =
      new RepositorioSolicitudesAtencionFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private SolicitudAtencion radicar(TipoSolicitud tipo) {
    return new RadicarSolicitud(
            repositorio, correos, new TextosDeCorreoFalso(), new RelojFalso(LLEGADA))
        .ejecutar(
            new RadicarSolicitudComando(
                tipo, "cliente@tecnosport.co", null, LLEGADA, "Asunto", "admin:1"));
  }

  private ListarSolicitudesDeAtencion bandeja(Instant ahora) {
    return new ListarSolicitudesDeAtencion(
        repositorio, PLAZOS, SIN_FESTIVOS, new RelojFalso(ahora));
  }

  /**
   * Lo que vence antes va arriba, y no lo que llego antes. Las dos llegaron el mismo dia; la
   * consulta de datos vence cinco dias habiles antes que la peticion, y ese es el orden que importa
   * cuando lo que corre es un plazo legal.
   */
  @Test
  void laBandejaOrdenaPorLoQueVenceAntesYNoPorLoQueLlegoAntes() {
    radicar(TipoSolicitud.PETICION);
    radicar(TipoSolicitud.CONSULTA_DATOS);

    List<SolicitudConPlazo> bandeja = bandeja(LLEGADA).ejecutar(null);

    assertEquals(2, bandeja.size());
    assertEquals(TipoSolicitud.CONSULTA_DATOS, bandeja.get(0).solicitud().tipo());
    assertEquals(TipoSolicitud.PETICION, bandeja.get(1).solicitud().tipo());
  }

  @Test
  void laBandejaTraeElVerdictoDeCadaUna() {
    radicar(TipoSolicitud.CONSULTA_DATOS);

    List<SolicitudConPlazo> aTiempo = bandeja(LLEGADA).ejecutar(null);
    List<SolicitudConPlazo> muyDespues = bandeja(LLEGADA.plusSeconds(90L * 86_400)).ejecutar(null);

    assertEquals(VerdictoPlazo.EN_PLAZO, aTiempo.get(0).verdicto());
    assertEquals(VerdictoPlazo.INDETERMINADO, muyDespues.get(0).verdicto());
  }

  /** Una respondida sale de la bandeja; una que nadie contesto se queda, por vieja que sea. */
  @Test
  void loRespondidoSaleDeLaBandejaYLoAbiertoSeQueda() {
    SolicitudAtencion respondida = radicar(TipoSolicitud.PETICION);
    radicar(TipoSolicitud.QUEJA);
    new ResponderSolicitud(repositorio, new RelojFalso(LLEGADA))
        .ejecutar(new ResponderSolicitudComando(respondida.id(), "resuelto", "admin:1"));

    List<SolicitudConPlazo> abiertas = bandeja(LLEGADA.plusSeconds(365L * 86_400)).ejecutar(null);

    assertEquals(1, abiertas.size());
    assertEquals(TipoSolicitud.QUEJA, abiertas.get(0).solicitud().tipo());
  }

  @Test
  void laProrrogaAvisaAlInteresadoConSusMotivos() {
    SolicitudAtencion solicitud = radicar(TipoSolicitud.RECLAMO_DATOS);
    int acusesPrevios = correos.enviados().size();

    new ProrrogarSolicitud(
            repositorio,
            PLAZOS,
            SIN_FESTIVOS,
            correos,
            new TextosDeCorreoFalso(),
            new RelojFalso(LLEGADA.plusSeconds(86_400)))
        .ejecutar(
            new ProrrogarSolicitudComando(
                solicitud.id(), "falta el soporte del proveedor", "admin:1"));

    assertEquals(EstadoSolicitudAtencion.PRORROGADA, solicitud.estado());
    assertEquals(acusesPrevios + 1, correos.enviados().size());
    assertTrue(
        correos
            .enviados()
            .get(acusesPrevios)
            .cuerpoHtml()
            .contains("falta el soporte del proveedor"),
        "el aviso lleva los motivos, que es lo que la ley exige informar");
  }

  @Test
  void unaSolicitudInexistenteFalla() {
    assertThrows(
        SolicitudAtencionNoEncontradaException.class,
        () ->
            new ResponderSolicitud(repositorio, new RelojFalso(LLEGADA))
                .ejecutar(
                    new ResponderSolicitudComando(
                        java.util.UUID.randomUUID(), "resuelto", "admin:1")));
  }

  @Test
  void unaSolicitudSinResumenNoSeDaPorRespondida() {
    SolicitudAtencion solicitud = radicar(TipoSolicitud.PETICION);

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new ResponderSolicitud(repositorio, new RelojFalso(LLEGADA))
                .ejecutar(new ResponderSolicitudComando(solicitud.id(), "   ", "admin:1")));
    assertEquals(EstadoSolicitudAtencion.RADICADA, solicitud.estado());
  }
}
