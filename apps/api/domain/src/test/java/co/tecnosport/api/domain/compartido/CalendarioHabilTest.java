package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class CalendarioHabilTest {

  private static Instant enMedellin(int anio, int mes, int dia, int hora) {
    return ZonedDateTime.of(anio, mes, dia, hora, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();
  }

  @Test
  void elCalendarioCalculadoCubreCualquierAnio() {
    CalendarioHabil calendario = CalendarioHabil.calculado();

    assertTrue(calendario.cubre(2026));
    assertTrue(calendario.cubre(2031));
  }

  @Test
  void elCalendarioSinCargarNoCubreNingunAnio() {
    assertFalse(CalendarioHabil.sinFestivosCargados().cubre(2026));
  }

  @Test
  void unFestivoRealNoEsHabil() {
    CalendarioHabil calendario = CalendarioHabil.calculado();

    // Lunes 12 de enero de 2026: el 6 de enero trasladado.
    assertFalse(calendario.esHabil(LocalDate.of(2026, 1, 12)));
    assertTrue(calendario.esHabil(LocalDate.of(2026, 1, 13)));
  }

  @Test
  void unFestivoRealEmpujaUnPlazoUnDiaMas() {
    // Entrega el jueves 8 de enero de 2026: viernes 9 (1), lunes 12 (2), martes 13 (3),
    // miércoles 14 (4), jueves 15 (5). Con el calendario de verdad el lunes 12 es festivo —el 6 de
    // enero trasladado—, así que el quinto día hábil es el viernes 16 y el plazo se agota al
    // terminarlo.
    Instant entrega = enMedellin(2026, 1, 8, 15);

    Instant sinFestivos = CalendarioHabil.sinFestivosCargados().limiteTrasDiasHabiles(entrega, 5);
    Instant conFestivos = CalendarioHabil.calculado().limiteTrasDiasHabiles(entrega, 5);

    assertEquals(enMedellin(2026, 1, 16, 0), sinFestivos);
    assertEquals(enMedellin(2026, 1, 17, 0), conFestivos);
    assertNotEquals(sinFestivos, conFestivos);
  }

  @Test
  void conElCalendarioCalculadoUnPlazoVencidoSeAfirmaVencido() {
    // Es la promesa entera de esta etapa: dejar de responder "no se sabe" cuando el plazo se agotó.
    Instant limite =
        CalendarioHabil.calculado().limiteTrasDiasHabiles(enMedellin(2026, 1, 8, 15), 5);

    assertEquals(
        VerdictoPlazo.VENCIDO,
        CalendarioHabil.calculado().verdicto(limite, enMedellin(2026, 1, 26, 10)));
    assertEquals(
        VerdictoPlazo.INDETERMINADO,
        CalendarioHabil.sinFestivosCargados().verdicto(limite, enMedellin(2026, 1, 26, 10)));
  }
}
