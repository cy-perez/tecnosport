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

  private final BitacoraDeTareasFalsa bitacora = new BitacoraDeTareasFalsa();
  private final RelojAjustable reloj = new RelojAjustable(INICIO);

  @Test
  void laTareaQueNuncaCorrioCorreEnElPrimerTickYQuedaAnotada() {
    TareaContada tarea = new TareaContada("conciliar", Duration.ofMinutes(15));

    ResultadoDelTick resultado = tick(tarea);

    assertEquals(1, tarea.veces);
    assertEquals(List.of("conciliar"), resultado.corridas());
    assertEquals(INICIO, bitacora.fila("conciliar").terminadaEn());
    assertNull(bitacora.fila("conciliar").enCursoHasta());
  }

  @Test
  void antesDeSuIntervaloNoCorreYDespuesSi() {
    TareaContada tarea = new TareaContada("conciliar", Duration.ofMinutes(15));
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);
    casoDeUso.ejecutar();

    reloj.avanzar(Duration.ofMinutes(14));
    ResultadoDelTick antes = casoDeUso.ejecutar();
    reloj.avanzar(Duration.ofMinutes(1));
    ResultadoDelTick justo = casoDeUso.ejecutar();

    assertEquals(1, antes.omitidas());
    assertEquals(List.of(), antes.corridas());
    assertEquals(List.of("conciliar"), justo.corridas());
    assertEquals(2, tarea.veces);
  }

  @Test
  void unTickAtrasadoCorreLoPendienteUnaSolaVez() {
    TareaContada tarea = new TareaContada("vigilar", Duration.ofMinutes(10));
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);
    casoDeUso.ejecutar();

    // Tres vueltas sin tick: Cloud Scheduler falló, o la API estuvo caída.
    reloj.avanzar(Duration.ofMinutes(35));
    casoDeUso.ejecutar();
    casoDeUso.ejecutar();

    assertEquals(2, tarea.veces);
  }

  @Test
  void unaQueFallaNoDetieneALasDemasYEsperaSuIntervalo() {
    TareaContada antes = new TareaContada("antes", Duration.ofMinutes(1));
    TareaQueFalla rota = new TareaQueFalla("rota", Duration.ofMinutes(10));
    TareaContada despues = new TareaContada("despues", Duration.ofMinutes(1));
    EjecutarTareasVencidas casoDeUso = casoDeUso(antes, rota, despues);

    ResultadoDelTick resultado = casoDeUso.ejecutar();
    reloj.avanzar(Duration.ofMinutes(1));
    casoDeUso.ejecutar();

    assertEquals(List.of("antes", "despues"), resultado.corridas());
    assertEquals(
        List.of(
            new ResultadoDelTick.Fallo(
                "rota", "java.lang.IllegalStateException: Wompi no responde")),
        resultado.fallidas());
    assertTrue(bitacora.fila("rota").fallo().contains("Wompi no responde"));
    assertNull(bitacora.fila("rota").enCursoHasta());
    assertEquals(1, rota.veces);
    assertEquals(2, despues.veces);
  }

  @Test
  void laQueVuelveASalirBienBorraElFalloAnterior() {
    TareaQueFalla tarea = new TareaQueFalla("conciliar", Duration.ofMinutes(1));
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);
    casoDeUso.ejecutar();

    tarea.falla = false;
    reloj.avanzar(Duration.ofMinutes(1));
    casoDeUso.ejecutar();

    assertNull(bitacora.fila("conciliar").fallo());
  }

  @Test
  void laQueTieneOtraInstanciaNoCorreHastaQueVenceSuArriendo() {
    TareaContada tarea = new TareaContada("conciliar", Duration.ofMinutes(1));
    bitacora.tomadaPorOtra("conciliar", INICIO.plus(ARRIENDO));
    EjecutarTareasVencidas casoDeUso = casoDeUso(tarea);

    ResultadoDelTick mientras = casoDeUso.ejecutar();
    reloj.avanzar(ARRIENDO);
    ResultadoDelTick vencido = casoDeUso.ejecutar();

    assertEquals(1, mientras.omitidas());
    assertEquals(List.of("conciliar"), vencido.corridas());
    assertEquals(1, tarea.veces);
  }

  @Test
  void elMotivoDeUnFalloSeRecorta() {
    TareaQueFalla tarea = new TareaQueFalla("conciliar", Duration.ofMinutes(1));
    tarea.mensaje = "x".repeat(5000);

    ResultadoDelTick resultado = tick(tarea);

    assertEquals(
        EjecutarTareasVencidas.LARGO_MAXIMO_DEL_MOTIVO,
        resultado.fallidas().getFirst().motivo().length());
  }

  @Test
  void dosTareasConElMismoNombreNoSeAceptan() {
    List<TareaProgramada> tareas =
        List.of(
            new TareaContada("conciliar", Duration.ofMinutes(1)),
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

  private ResultadoDelTick tick(TareaProgramada... tareas) {
    return casoDeUso(tareas).ejecutar();
  }

  private EjecutarTareasVencidas casoDeUso(TareaProgramada... tareas) {
    return new EjecutarTareasVencidas(List.of(tareas), bitacora, reloj, ARRIENDO);
  }

  private static class TareaContada implements TareaProgramada {

    private final String nombre;
    private final Duration intervalo;
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
    }
  }

  private static final class TareaQueFalla extends TareaContada {

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

    @Override
    public Instant ahora() {
      return ahora;
    }
  }
}
