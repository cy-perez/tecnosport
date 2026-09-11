package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class PlazoDeEntregaTest {

  /** Jueves 10 de septiembre de 2026, 15:30 en Medellín. */
  private static final Instant CONFIRMADO =
      ZonedDateTime.of(2026, 9, 10, 15, 30, 0, 0, ZonaDelNegocio.ZONA).toInstant();

  private static Instant enBogota(int anio, int mes, int dia, int hora) {
    return ZonedDateTime.of(anio, mes, dia, hora, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();
  }

  @Test
  void treintaDiasCalendarioContadosDesdeElDiaSiguiente() {
    // Confirmado el 10 de septiembre: el día 1 es el 11 y el día 30 es el 10 de octubre. El plazo
    // se agota al terminar ese día, o sea al empezar el 11.
    assertEquals(enBogota(2026, 10, 11, 0), PlazoDeEntrega.limite(CONFIRMADO));
  }

  @Test
  void losFinesDeSemanaYLosFestivosCuentanIgual() {
    // Confirmado el lunes 14 de septiembre, la ventana llega hasta el 14 de octubre y se come el
    // festivo del 12 (día de la raza) y cuatro fines de semana. El límite es el día 31 a secas: son
    // días calendario, no hábiles, y por eso este plazo no recibe ningún CalendarioHabil.
    Instant lunes = enBogota(2026, 9, 14, 9);

    assertEquals(enBogota(2026, 10, 15, 0), PlazoDeEntrega.limite(lunes));
  }

  @Test
  void laHoraDeLaConfirmacionNoRecortaElUltimoDia() {
    Instant casiMedianoche =
        ZonedDateTime.of(2026, 9, 10, 23, 59, 0, 0, ZonaDelNegocio.ZONA).toInstant();

    assertEquals(PlazoDeEntrega.limite(CONFIRMADO), PlazoDeEntrega.limite(casiMedianoche));
  }

  @Test
  void seCuentaEnLaZonaDelNegocioYNoEnLaDelServidor() {
    // Las 23:00 del 10 en Medellín son las 04:00 del 11 en UTC. Contar en UTC daría un día más.
    Instant nocheDelDiez =
        ZonedDateTime.of(2026, 9, 10, 23, 0, 0, 0, ZonaDelNegocio.ZONA).toInstant();

    assertEquals(enBogota(2026, 10, 11, 0), PlazoDeEntrega.limite(nocheDelDiez));
  }

  @Test
  void elUltimoDiaCompletoSigueEnPlazo() {
    Instant finDelDiaTreinta = enBogota(2026, 10, 10, 23);

    assertEquals(VerdictoPlazo.EN_PLAZO, PlazoDeEntrega.verdicto(CONFIRMADO, finDelDiaTreinta));
  }

  @Test
  void elInstanteDelLimiteTodaviaEstaEnPlazo() {
    assertEquals(
        VerdictoPlazo.EN_PLAZO,
        PlazoDeEntrega.verdicto(CONFIRMADO, PlazoDeEntrega.limite(CONFIRMADO)));
  }

  @Test
  void pasadoElLimiteEstaVencido() {
    assertEquals(
        VerdictoPlazo.VENCIDO, PlazoDeEntrega.verdicto(CONFIRMADO, enBogota(2026, 10, 11, 1)));
  }

  @Test
  void nuncaResponderIndeterminado() {
    // A diferencia del retracto y de las PQR, aquí no hay festivos que puedan empujar el límite,
    // así
    // que "pasó el límite" siempre es una afirmación segura.
    assertEquals(
        VerdictoPlazo.VENCIDO, PlazoDeEntrega.verdicto(CONFIRMADO, enBogota(2027, 1, 1, 0)));
  }

  @Test
  void sinFechaDeInicioNoHayPlazoQueCalcular() {
    assertThrows(NullPointerException.class, () -> PlazoDeEntrega.limite(null));
  }
}
