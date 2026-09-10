package co.tecnosport.api.domain.atencion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SolicitudAtencionTest {

  /** Jueves. Elegido para que ningún límite de los que se afirman caiga en fin de semana. */
  private static final Instant LLEGADA =
      ZonedDateTime.of(2026, 9, 10, 9, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

  private static final PlazosDeAtencion PLAZOS =
      PlazosDeAtencion.de(
          new PlazosDeAtencion.PlazoHabil(10, 5),
          new PlazosDeAtencion.PlazoHabil(15, 8),
          new PlazosDeAtencion.PlazoHabil(15, 0));

  private static final CalendarioHabil SIN_FESTIVOS = CalendarioHabil.sinFestivosCargados();

  private static Instant inicioDe(int anio, int mes, int dia) {
    return ZonedDateTime.of(anio, mes, dia, 0, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();
  }

  private SolicitudAtencion radicada(TipoSolicitud tipo, Instant radicadaEn) {
    return SolicitudAtencion.radicar(
        NumeroRadicado.de(2026, 1),
        tipo,
        new CorreoElectronico("cliente@tecnosport.co"),
        null,
        LLEGADA,
        radicadaEn,
        "admin:1",
        "No me llegó el pedido");
  }

  private SolicitudAtencion radicada(TipoSolicitud tipo) {
    return radicada(tipo, LLEGADA);
  }

  /**
   * El hallazgo que hizo falta este agregado: los términos publicados prometen quince días hábiles
   * para "toda petición" y la política de datos promete diez para una consulta, y las dos frases
   * apuntan al mismo correo. Una sola constante habría incumplido la más corta sin que nadie lo
   * notara — las dos partes funcionan, solo que no dicen lo mismo.
   */
  @Test
  void unaConsultaDeDatosYUnaPeticionNoTienenElMismoPlazo() {
    Instant limiteConsulta =
        radicada(TipoSolicitud.CONSULTA_DATOS).limiteDeRespuesta(PLAZOS, SIN_FESTIVOS);
    Instant limitePeticion =
        radicada(TipoSolicitud.PETICION).limiteDeRespuesta(PLAZOS, SIN_FESTIVOS);

    assertEquals(inicioDe(2026, 9, 25), limiteConsulta, "diez días hábiles");
    assertEquals(inicioDe(2026, 10, 2), limitePeticion, "quince días hábiles");
    assertNotEquals(limiteConsulta, limitePeticion);
  }

  @Test
  void unReclamoDeDatosTieneElSuyoPropio() {
    assertEquals(
        inicioDe(2026, 10, 2),
        radicada(TipoSolicitud.RECLAMO_DATOS).limiteDeRespuesta(PLAZOS, SIN_FESTIVOS));
  }

  /**
   * Si el plazo corriera desde el registro, radicar tarde sería una forma de no incumplir nunca.
   * Dos solicitudes que llegaron el mismo día vencen el mismo día, la registre alguien hoy o dentro
   * de una semana.
   */
  @Test
  void elPlazoCorreDesdeQueLlegoYNoDesdeQueAlguienLaRegistro() {
    SolicitudAtencion aTiempo = radicada(TipoSolicitud.PETICION, LLEGADA);
    SolicitudAtencion registradaTarde =
        radicada(TipoSolicitud.PETICION, LLEGADA.plusSeconds(7 * 86_400));

    assertEquals(
        aTiempo.limiteDeRespuesta(PLAZOS, SIN_FESTIVOS),
        registradaTarde.limiteDeRespuesta(PLAZOS, SIN_FESTIVOS));
  }

  @Test
  void noSePuedeRadicarAlgoAntesDeQueLlegue() {
    assertThrows(
        ExcepcionDeDominio.class, () -> radicada(TipoSolicitud.PETICION, LLEGADA.minusSeconds(60)));
  }

  @Test
  void unaProrrogaAvisadaATiempoCorreElLimite() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.RECLAMO_DATOS);

    solicitud.prorrogar(
        new Prorroga(
            inicioDe(2026, 9, 30),
            "admin:1",
            "falta el soporte del proveedor",
            inicioDe(2026, 9, 30)),
        PLAZOS,
        SIN_FESTIVOS);

    assertEquals(EstadoSolicitudAtencion.PRORROGADA, solicitud.estado());
    assertEquals(inicioDe(2026, 10, 14), solicitud.limiteDeRespuesta(PLAZOS, SIN_FESTIVOS));
  }

  /**
   * Avisar cuando el plazo ya venció no prorroga nada: es un incumplimiento con más días encima.
   */
  @Test
  void unaProrrogaAvisadaTardeNoProrrogaNada() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.RECLAMO_DATOS);
    Instant tarde = inicioDe(2026, 10, 3);

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            solicitud.prorrogar(
                new Prorroga(tarde, "admin:1", "se nos pasó", tarde), PLAZOS, SIN_FESTIVOS));
    assertEquals(EstadoSolicitudAtencion.RADICADA, solicitud.estado());
    assertEquals(inicioDe(2026, 10, 2), solicitud.limiteDeRespuesta(PLAZOS, SIN_FESTIVOS));
  }

  /** El plazo que el sitio promete en sus términos no menciona prórroga, así que no la hay. */
  @Test
  void unTipoSinProrrogaNoSePuedeProrrogar() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.PETICION);

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            solicitud.prorrogar(
                new Prorroga(LLEGADA, "admin:1", "hace falta más tiempo", LLEGADA),
                PLAZOS,
                SIN_FESTIVOS));
  }

  @Test
  void unaProrrogaSinMotivoNoEsUnaProrroga() {
    assertThrows(ExcepcionDeDominio.class, () -> new Prorroga(LLEGADA, "admin:1", "  ", LLEGADA));
  }

  @Test
  void sinResponderYDentroDelPlazoElVerdictoEsEnPlazo() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.PETICION);

    assertEquals(
        VerdictoPlazo.EN_PLAZO,
        solicitud.verdictoDeRespuesta(inicioDe(2026, 9, 20), PLAZOS, SIN_FESTIVOS));
  }

  /**
   * Sin festivos cargados, el límite calculable es el más temprano posible: pasado ese límite no se
   * puede afirmar que se incumplió, porque un festivo solo lo empuja hacia adelante. Mismo
   * razonamiento que el veredicto del retracto, y el mismo dato pendiente detrás.
   */
  @Test
  void pasadoElLimiteSinFestivosCargadosElVerdictoEsIndeterminado() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.PETICION);

    assertEquals(
        VerdictoPlazo.INDETERMINADO,
        solicitud.verdictoDeRespuesta(inicioDe(2026, 10, 20), PLAZOS, SIN_FESTIVOS));
  }

  @Test
  void conElCalendarioCargadoSiSePuedeAfirmarQueVencio() {
    CalendarioHabil conFestivos =
        CalendarioHabil.con(Map.of(2026, Set.of(LocalDate.of(2026, 10, 12))));
    SolicitudAtencion solicitud = radicada(TipoSolicitud.PETICION);

    assertEquals(
        VerdictoPlazo.VENCIDO,
        solicitud.verdictoDeRespuesta(inicioDe(2026, 10, 20), PLAZOS, conFestivos));
  }

  /**
   * Una vez respondida, el veredicto mide contra la fecha de la respuesta y deja de moverse. Es el
   * dato que hay que poder mostrar el día de la reclamación, y no puede empeorar solo porque haya
   * pasado el tiempo desde entonces.
   */
  @Test
  void elVerdictoDeUnaRespondidaNoSeMueveConElTiempo() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.PETICION);
    solicitud.responder(new Respuesta(inicioDe(2026, 9, 15), "admin:1", "se reenvió la guía"));

    assertEquals(
        VerdictoPlazo.EN_PLAZO,
        solicitud.verdictoDeRespuesta(inicioDe(2027, 1, 1), PLAZOS, SIN_FESTIVOS));
  }

  @Test
  void noSeRespondeDosVeces() {
    SolicitudAtencion solicitud = radicada(TipoSolicitud.PETICION);
    solicitud.responder(new Respuesta(inicioDe(2026, 9, 15), "admin:1", "se reenvió la guía"));

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            solicitud.responder(
                new Respuesta(inicioDe(2026, 9, 16), "admin:1", "otra vez lo mismo")));
  }

  @Test
  void elRadicadoLlevaElFormatoQueElInteresadoPuedeCitar() {
    assertEquals("TS-PQR-2026-000123", NumeroRadicado.de(2026, 123).valor());
  }

  @Test
  void unRadicadoConOtroFormatoNoSeAcepta() {
    assertThrows(ExcepcionDeDominio.class, () -> new NumeroRadicado("PQR-123"));
  }
}
