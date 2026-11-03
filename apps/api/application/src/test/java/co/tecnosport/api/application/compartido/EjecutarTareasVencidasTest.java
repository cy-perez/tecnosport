package co.tecnosport.api.application.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EjecutarTareasVencidasTest {

  private static final Instant INICIO = Instant.parse("2026-11-03T12:00:00Z");
  private static final Duration ARRIENDO = Duration.ofMinutes(30);
  private static final Duration UN_MINUTO = Duration.ofMinutes(1);

  private final BitacoraDeTareasFalsa bitacora = new BitacoraDeTareasFalsa();
  private final RelojAjustable reloj = new RelojAjustable(INICIO);

  /** Como el retraso inicial de antes: la primera vez que se ve, espera su intervalo. */
  @Test
  void laPrimeraVezQueSeVeLaAnotaYEsperaSuIntervalo() {
    TareaContada tarea = new TareaContada("conciliar", Duration.ofMinutes(15));
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);

    ResultadoDeLaVuelta primera = casoDeUso.ejecutar();
    reloj.avanzar(Duration.ofMinutes(15));
    ResultadoDeLaVuelta despues = casoDeUso.ejecutar();

    assertEquals(0, primera.corridas().size());
    assertEquals(1, primera.omitidas());
    assertEquals(List.of("conciliar"), despues.corridas());
    assertEquals(1, tarea.veces);
  }

  @Test
  void antesDeSuIntervaloNoCorreYDespuesSi() {
    TareaContada tarea = new TareaContada("conciliar", Duration.ofMinutes(15));
    bitacora.vista("conciliar", INICIO);
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);

    reloj.avanzar(Duration.ofMinutes(14));
    ResultadoDeLaVuelta antes = casoDeUso.ejecutar();
    reloj.avanzar(Duration.ofMinutes(1));
    ResultadoDeLaVuelta justo = casoDeUso.ejecutar();

    assertEquals(1, antes.omitidas());
    assertEquals(List.of("conciliar"), justo.corridas());
    assertEquals(1, tarea.veces);
    assertNull(bitacora.fila("conciliar").enCursoHasta());
  }

  /**
   * El defecto que encontró la revisión: con el intervalo contado desde que terminó, una tarea de
   * un minuto que tarda unos segundos, con vueltas de un minuto, corría una sí y otra no. Aquí la
   * tarea tarda 20 segundos y la vuelta no llega siempre al mismo milisegundo: unas con 300 ms de
   * retraso, otras con 500 de adelanto, como llega Cloud Scheduler.
   */
  @Test
  void unaTareaDelMismoIntervaloQueLaVueltaCorreEnCadaVuelta() {
    TareaContada tarea = new TareaContada("bandeja", UN_MINUTO);
    tarea.duracion = Duration.ofSeconds(20);
    bitacora.vista("bandeja", INICIO.minus(UN_MINUTO));
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);

    for (int vuelta = 0; vuelta < 5; vuelta++) {
      Instant enPunto = INICIO.plus(UN_MINUTO.multipliedBy(vuelta));
      reloj.irA(vuelta % 2 == 0 ? enPunto.plusMillis(300) : enPunto.minusMillis(500));
      casoDeUso.ejecutar();
    }

    assertEquals(5, tarea.veces);
  }

  @Test
  void elMargenNoPasaDeTreintaSegundosNiDeLaMitadDelIntervalo() {
    assertEquals(Duration.ofSeconds(30), EjecutarTareasVencidas.aEsperar(UN_MINUTO));
    assertEquals(
        Duration.ofMinutes(60).minusSeconds(30),
        EjecutarTareasVencidas.aEsperar(Duration.ofMinutes(60)));
    assertEquals(Duration.ofSeconds(10), EjecutarTareasVencidas.aEsperar(Duration.ofSeconds(20)));
  }

  @Test
  void unaVueltaAtrasadaCorreLoPendienteUnaSolaVez() {
    TareaContada tarea = new TareaContada("vigilar", Duration.ofMinutes(10));
    bitacora.vista("vigilar", INICIO);
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);

    // Tres vueltas sin llegar: Cloud Scheduler falló, o la API estuvo caída.
    reloj.avanzar(Duration.ofMinutes(35));
    casoDeUso.ejecutar();
    casoDeUso.ejecutar();

    assertEquals(1, tarea.veces);
  }

  @Test
  void unaQueFallaNoDetieneALasDemasYEsperaSuIntervalo() {
    TareaContada antes = new TareaContada("antes", UN_MINUTO);
    TareaQueFalla rota = new TareaQueFalla("rota", Duration.ofMinutes(10));
    TareaContada despues = new TareaContada("despues", UN_MINUTO);
    for (String nombre : List.of("antes", "rota", "despues")) {
      bitacora.vista(nombre, INICIO.minus(Duration.ofMinutes(10)));
    }
    EjecutarTareasVencidas casoDeUso = casoDeUso(antes, rota, despues);

    ResultadoDeLaVuelta resultado = casoDeUso.ejecutar();
    reloj.avanzar(UN_MINUTO);
    casoDeUso.ejecutar();

    assertEquals(List.of("antes", "despues"), resultado.corridas());
    assertEquals(
        List.of(
            new ResultadoDeLaVuelta.Fallo(
                "rota", "java.lang.IllegalStateException: Wompi no responde")),
        resultado.fallidas());
    assertTrue(bitacora.fila("rota").fallo().contains("Wompi no responde"));
    assertNull(bitacora.fila("rota").enCursoHasta());
    assertEquals(1, rota.veces);
    assertEquals(2, despues.veces);
  }

  @Test
  void laQueVuelveASalirBienBorraElFalloAnterior() {
    TareaQueFalla tarea = new TareaQueFalla("conciliar", UN_MINUTO);
    bitacora.vista("conciliar", INICIO.minus(UN_MINUTO));
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);
    casoDeUso.ejecutar();

    tarea.falla = false;
    reloj.avanzar(UN_MINUTO);
    casoDeUso.ejecutar();

    assertNull(bitacora.fila("conciliar").fallo());
  }

  @Test
  void laQueTieneOtraInstanciaNoCorreHastaQueVenceSuArriendo() {
    TareaContada tarea = new TareaContada("conciliar", UN_MINUTO);
    bitacora.tomadaPorOtra("conciliar", INICIO.minus(UN_MINUTO), INICIO.plus(ARRIENDO));
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);

    ResultadoDeLaVuelta mientras = casoDeUso.ejecutar();
    reloj.avanzar(ARRIENDO);
    ResultadoDeLaVuelta vencido = casoDeUso.ejecutar();

    assertEquals(1, mientras.omitidas());
    assertEquals(List.of("conciliar"), vencido.corridas());
    assertEquals(1, tarea.veces);
  }

  /** La segunda revisión: si la bitácora falla al tomar una, las demás siguen. */
  @Test
  void siLaBitacoraFallaLaVueltaNoSeCae() {
    TareaContada tarea = new TareaContada("conciliar", UN_MINUTO);
    bitacora.fallarAlTomar = new IllegalStateException("la base no responde");

    ResultadoDeLaVuelta resultado = tick(tarea);

    assertEquals(0, tarea.veces);
    assertEquals("conciliar", resultado.fallidas().getFirst().tarea());
    assertTrue(resultado.fallidas().getFirst().motivo().contains("la base no responde"));
  }

  @Test
  void elMotivoDeUnFalloSeRecorta() {
    TareaQueFalla tarea = new TareaQueFalla("conciliar", UN_MINUTO);
    tarea.mensaje = "x".repeat(5000);
    bitacora.vista("conciliar", INICIO.minus(UN_MINUTO));

    ResultadoDeLaVuelta resultado = tick(tarea);

    assertEquals(
        EjecutarTareasVencidas.LARGO_MAXIMO_DEL_MOTIVO,
        resultado.fallidas().getFirst().motivo().length());
  }

  @Test
  void dosTareasConElMismoNombreNoSeAceptan() {
    List<TareaProgramada> tareas =
        List.of(
            new TareaContada("conciliar", UN_MINUTO),
            new TareaContada("conciliar", Duration.ofMinutes(5)));

    assertThrows(
        IllegalArgumentException.class,
        () -> new EjecutarTareasVencidas(tareas, bitacora, reloj, ARRIENDO));
  }

  @Test
  void unArriendoNuloNoSeAcepta() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new EjecutarTareasVencidas(List.of(), bitacora, reloj, Duration.ZERO));
  }

  @Test
  void laFabricaCorreElTrabajoQueRecibe() {
    int[] veces = {0};
    TareaProgramada tarea = TareaProgramada.de("conciliar", UN_MINUTO, () -> veces[0]++);
    bitacora.vista("conciliar", INICIO.minus(UN_MINUTO));

    tick(tarea);

    assertEquals(1, veces[0]);
    assertEquals("conciliar", tarea.nombre());
  }

  @Test
  void laFabricaNoAceptaUnIntervaloCeroNiUnNombreVacio() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TareaProgramada.de("conciliar", Duration.ZERO, () -> {}));
    assertThrows(
        IllegalArgumentException.class, () -> TareaProgramada.de(" ", UN_MINUTO, () -> {}));
  }

  private ResultadoDeLaVuelta tick(TareaProgramada... tareas) {
    return casoDeUso(tareas).ejecutar();
  }

  private EjecutarTareasVencidas casoDeUso(TareaProgramada... tareas) {
    return new EjecutarTareasVencidas(List.of(tareas), bitacora, reloj, ARRIENDO);
  }

  /** Cuenta las vueltas, y con {@code duracion} hace avanzar el reloj mientras corre. */
  private class TareaContada implements TareaProgramada {

    private final String nombre;
    private final Duration intervalo;
    Duration duracion = Duration.ZERO;
    int veces;

    TareaContada(String nombre, Duration intervalo) {
      this.nombre = nombre;
      this.intervalo = intervalo;
    }

    @Override
    public String nombre() {
      return nombre;
    }

    @Override
    public Duration intervalo() {
      return intervalo;
    }

    @Override
    public void ejecutar() {
      veces++;
      reloj.avanzar(duracion);
    }
  }

  private final class TareaQueFalla extends TareaContada {

    boolean falla = true;
    String mensaje = "Wompi no responde";

    TareaQueFalla(String nombre, Duration intervalo) {
      super(nombre, intervalo);
    }

    @Override
    public void ejecutar() {
      super.ejecutar();
      if (falla) {
        throw new IllegalStateException(mensaje);
      }
    }
  }

  private static final class RelojAjustable implements Reloj {

    private Instant ahora;

    RelojAjustable(Instant inicio) {
      this.ahora = inicio;
    }

    void avanzar(Duration cuanto) {
      ahora = ahora.plus(cuanto);
    }

    void irA(Instant instante) {
      ahora = instante;
    }

    @Override
    public Instant ahora() {
      return ahora;
    }
  }
}
