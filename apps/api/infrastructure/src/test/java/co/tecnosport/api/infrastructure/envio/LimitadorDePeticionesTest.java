package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sin dormir de verdad: el reloj y la pausa son falsos, así que estas pruebas corren en
 * microsegundos en vez de en segundos. Una prueba lenta se termina borrando.
 */
class LimitadorDePeticionesTest {

  /** Reloj falso que avanza solo cuando alguien "duerme". */
  private static final class RelojYPausaFalsos implements LimitadorDePeticiones.Pausador {
    private long nanos;
    private final List<Duration> pausas = new ArrayList<>();

    @Override
    public void pausar(Duration duracion) {
      pausas.add(duracion);
      nanos += duracion.toNanos();
    }

    long nanos() {
      return nanos;
    }

    void avanzar(Duration duracion) {
      nanos += duracion.toNanos();
    }
  }

  private static final Duration MEDIO_SEGUNDO = Duration.ofMillis(500);

  @Test
  void laPrimeraPeticionNoEspera() throws InterruptedException {
    RelojYPausaFalsos falso = new RelojYPausaFalsos();
    LimitadorDePeticiones limitador = new LimitadorDePeticiones(MEDIO_SEGUNDO, falso::nanos, falso);

    limitador.esperarTurno();

    assertTrue(falso.pausas.isEmpty());
  }

  @Test
  void laSegundaPeticionInmediataEsperaElIntervaloCompleto() throws InterruptedException {
    RelojYPausaFalsos falso = new RelojYPausaFalsos();
    LimitadorDePeticiones limitador = new LimitadorDePeticiones(MEDIO_SEGUNDO, falso::nanos, falso);

    limitador.esperarTurno();
    limitador.esperarTurno();

    assertEquals(List.of(MEDIO_SEGUNDO), falso.pausas);
  }

  /** Si entre dos peticiones ya pasó el intervalo por su cuenta, no hay nada que esperar. */
  @Test
  void noEsperaSiElIntervaloYaTranscurrio() throws InterruptedException {
    RelojYPausaFalsos falso = new RelojYPausaFalsos();
    LimitadorDePeticiones limitador = new LimitadorDePeticiones(MEDIO_SEGUNDO, falso::nanos, falso);

    limitador.esperarTurno();
    falso.avanzar(Duration.ofMillis(600));
    limitador.esperarTurno();

    assertTrue(falso.pausas.isEmpty());
  }

  /** Espera solo lo que falta, no el intervalo entero, cuando ya transcurrió una parte. */
  @Test
  void esperaSoloLoQueFalta() throws InterruptedException {
    RelojYPausaFalsos falso = new RelojYPausaFalsos();
    LimitadorDePeticiones limitador = new LimitadorDePeticiones(MEDIO_SEGUNDO, falso::nanos, falso);

    limitador.esperarTurno();
    falso.avanzar(Duration.ofMillis(200));
    limitador.esperarTurno();

    assertEquals(List.of(Duration.ofMillis(300)), falso.pausas);
  }

  @Test
  void tresPeticionesSeguidasEsperanDosVeces() throws InterruptedException {
    RelojYPausaFalsos falso = new RelojYPausaFalsos();
    LimitadorDePeticiones limitador = new LimitadorDePeticiones(MEDIO_SEGUNDO, falso::nanos, falso);

    limitador.esperarTurno();
    limitador.esperarTurno();
    limitador.esperarTurno();

    assertEquals(List.of(MEDIO_SEGUNDO, MEDIO_SEGUNDO), falso.pausas);
  }

  @Test
  void dosPorSegundoSonQuinientosMilisegundos() throws InterruptedException {
    RelojYPausaFalsos falso = new RelojYPausaFalsos();
    LimitadorDePeticiones limitador =
        new LimitadorDePeticiones(Duration.ofSeconds(1).dividedBy(2), falso::nanos, falso);

    limitador.esperarTurno();
    limitador.esperarTurno();

    assertEquals(List.of(MEDIO_SEGUNDO), falso.pausas);
  }

  @Test
  void rechazaCeroPeticionesPorSegundo() {
    assertThrows(IllegalArgumentException.class, () -> LimitadorDePeticiones.deSegundo(0));
  }
}
