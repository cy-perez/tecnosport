package co.tecnosport.api.domain.retracto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlazoDeRetractoTest {

  /** Jueves 10 de septiembre de 2026, 15:30 en Medellín. */
  private static final Instant ENTREGA_JUEVES =
      ZonedDateTime.of(2026, 9, 10, 15, 30, 0, 0, PlazoDeRetracto.ZONA).toInstant();

  private static Instant enBogota(int anio, int mes, int dia, int hora) {
    return ZonedDateTime.of(anio, mes, dia, hora, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();
  }

  @Test
  void cuentaCincoDiasHabilesSaltandoseElFinDeSemana() {
    // Entrega el jueves: viernes 11 (1), lunes 14 (2), martes 15 (3), miércoles 16 (4),
    // jueves 17 (5). El plazo se agota al terminar el jueves 17.
    Instant limite = PlazoDeRetracto.limite(ENTREGA_JUEVES, CalendarioHabil.sinFestivosCargados());

    assertEquals(enBogota(2026, 9, 18, 0), limite);
  }

  @Test
  void unFestivoEmpujaElLimiteUnDiaMas() {
    CalendarioHabil conFestivo =
        CalendarioHabil.con(Map.of(2026, Set.of(LocalDate.of(2026, 9, 14))));

    Instant limite = PlazoDeRetracto.limite(ENTREGA_JUEVES, conFestivo);

    assertEquals(enBogota(2026, 9, 19, 0), limite);
  }

  @Test
  void laHoraDeLaEntregaNoRecortaElUltimoDia() {
    // Entregado a las 23:00, el quinto día hábil se agota igual al final del día, no a las 23:00.
    Instant tarde = ZonedDateTime.of(2026, 9, 10, 23, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();

    assertEquals(
        PlazoDeRetracto.limite(ENTREGA_JUEVES, CalendarioHabil.sinFestivosCargados()),
        PlazoDeRetracto.limite(tarde, CalendarioHabil.sinFestivosCargados()));
  }

  @Test
  void dentroDelLimiteEsEnPlazoAunqueFaltenLosFestivos() {
    // Es seguro: los festivos solo empujan el límite hacia adelante.
    VerdictoPlazo verdicto =
        PlazoDeRetracto.verdicto(
            ENTREGA_JUEVES, enBogota(2026, 9, 16, 10), CalendarioHabil.sinFestivosCargados());

    assertEquals(VerdictoPlazo.EN_PLAZO, verdicto);
  }

  @Test
  void pasadoElLimiteSinFestivosCargadosNoSeAfirmaQueVencio() {
    VerdictoPlazo verdicto =
        PlazoDeRetracto.verdicto(
            ENTREGA_JUEVES, enBogota(2026, 9, 21, 10), CalendarioHabil.sinFestivosCargados());

    assertEquals(VerdictoPlazo.INDETERMINADO, verdicto);
  }

  @Test
  void conElCalendarioCargadoSiSeAfirmaQueVencio() {
    CalendarioHabil cargado = CalendarioHabil.con(Map.of(2026, Set.of()));

    VerdictoPlazo verdicto =
        PlazoDeRetracto.verdicto(ENTREGA_JUEVES, enBogota(2026, 9, 21, 10), cargado);

    assertEquals(VerdictoPlazo.VENCIDO, verdicto);
  }

  @Test
  void conElCalendarioDeProduccionUnFestivoRealEmpujaElLimite() {
    // Entrega el jueves 8 de enero de 2026. El lunes 12 es festivo —el 6 de enero trasladado—, así
    // que el quinto día hábil deja de ser el jueves 15 y pasa a ser el viernes 16.
    Instant entrega = ZonedDateTime.of(2026, 1, 8, 15, 30, 0, 0, PlazoDeRetracto.ZONA).toInstant();

    assertEquals(
        enBogota(2026, 1, 17, 0), PlazoDeRetracto.limite(entrega, CalendarioHabil.calculado()));
  }

  @Test
  void pasadoElLimiteConElCalendarioDeProduccionSiSeAfirmaQueVencio() {
    // La promesa de la etapa: quien compró hace dos semanas recibe un veredicto, no un "no se
    // sabe".
    VerdictoPlazo verdicto =
        PlazoDeRetracto.verdicto(
            ENTREGA_JUEVES, enBogota(2026, 9, 21, 10), CalendarioHabil.calculado());

    assertEquals(VerdictoPlazo.VENCIDO, verdicto);
  }

  @Test
  void elDiaDeLaEntregaNoCuenta() {
    // "dentro de los cinco días hábiles siguientes a la entrega": la cuenta arranca al día
    // siguiente. Entregado el viernes, el quinto hábil es el viernes siguiente.
    Instant viernes = ZonedDateTime.of(2026, 9, 11, 9, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();

    assertEquals(
        enBogota(2026, 9, 19, 0),
        PlazoDeRetracto.limite(viernes, CalendarioHabil.sinFestivosCargados()));
  }
}
