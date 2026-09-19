package co.tecnosport.api.application.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DrenarBandejaDeSalidaTest {

  private static final Instant AHORA = Instant.parse("2026-09-18T15:00:00Z");
  private static final Duration RETENCION = Duration.ofDays(30);

  private RepositorioCorreosPendientesFalso repositorio;
  private TransporteDeCorreoFalso transporte;
  private DrenarBandejaDeSalida caso;

  @BeforeEach
  void preparar() {
    repositorio = new RepositorioCorreosPendientesFalso();
    transporte = new TransporteDeCorreoFalso();
    caso =
        new DrenarBandejaDeSalida(repositorio, transporte, new RelojFalso(AHORA), RETENCION, 100);
  }

  @Test
  @DisplayName("un correo encolado se manda y queda marcado")
  void unCorreoEncoladoSeMandaYQuedaMarcado() {
    UUID id = encolar(0, AHORA);

    ResultadoDrenaje resultado = caso.ejecutar();

    assertEquals(1, resultado.enviados());
    assertEquals(0, resultado.fallidos());
    assertEquals(1, transporte.enviados().size());
    assertEquals(AHORA, repositorio.fila(id).enviadoEn());
  }

  @Test
  @DisplayName("un correo que falla no queda marcado, deja el motivo y se reintenta")
  void unCorreoQueFallaSeReintenta() {
    UUID id = encolar(0, AHORA);
    transporte.hazQueFalle();

    ResultadoDrenaje resultado = caso.ejecutar();

    assertEquals(0, resultado.enviados());
    assertEquals(1, resultado.fallidos());
    assertEquals(0, resultado.rendidos());
    assertNull(repositorio.fila(id).enviadoEn(), "un correo que no salió no puede quedar marcado");
    assertNotNull(repositorio.fila(id).ultimoError());
    // Y sigue siendo enviable, porque el próximo intento ya está programado.
    assertEquals(AHORA.plus(Duration.ofMinutes(1)), repositorio.fila(id).proximoIntentoEn());
  }

  @Test
  @DisplayName("el motivo que se guarda es la causa, no el envoltorio que siempre dice lo mismo")
  void elMotivoGuardadoEsLaCausa() {
    UUID id = encolar(0, AHORA);
    transporte.hazQueFalle();

    caso.ejecutar();

    assertTrue(
        repositorio.fila(id).ultimoError().contains("el servidor de correo no respondió"),
        "sin la causa, la columna no sirve para saber por qué se rindió un correo");
  }

  @Test
  @DisplayName("al quinto intento se rinde y deja de reclamarse")
  void alQuintoIntentoSeRinde() {
    UUID id = encolar(DrenarBandejaDeSalida.MAX_INTENTOS - 1, AHORA);
    transporte.hazQueFalle();

    ResultadoDrenaje resultado = caso.ejecutar();

    assertEquals(1, resultado.rendidos());
    assertEquals(
        List.of(),
        repositorio.buscarEnviables(DrenarBandejaDeSalida.MAX_INTENTOS, AHORA, 100),
        "un correo rendido no puede volver a la cola");
    assertNotNull(
        repositorio.fila(id), "y tampoco puede desaparecer: el motivo hay que poder verlo");
  }

  @Test
  @DisplayName("el espaciado entre reintentos crece: 1, 5, 15 y 40 minutos")
  void elEspaciadoCrece() {
    encolar(0, AHORA);
    encolar(1, AHORA);
    encolar(2, AHORA);
    encolar(3, AHORA);
    transporte.hazQueFalle();

    caso.ejecutar();

    assertEquals(
        List.of(
            AHORA.plus(Duration.ofMinutes(1)),
            AHORA.plus(Duration.ofMinutes(5)),
            AHORA.plus(Duration.ofMinutes(15)),
            AHORA.plus(Duration.ofMinutes(40))),
        repositorio.proximosIntentosReclamados(),
        "el último cae a poco más de una hora del primero, que es lo que dice el javadoc");
  }

  @Test
  @DisplayName("quien pierde el reclamo no manda el correo")
  void quienPierdeElReclamoNoManda() {
    encolar(0, AHORA);
    repositorio.queOtroGaneElReclamo();

    ResultadoDrenaje resultado = caso.ejecutar();

    assertEquals(0, resultado.enviados());
    assertEquals(
        List.of(), transporte.enviados(), "dos instancias no pueden mandar el mismo correo");
  }

  @Test
  @DisplayName("un fallo no se lleva por delante a los que vienen detrás")
  void unFalloNoSeLlevaAlResto() {
    encolar(0, AHORA);
    UUID segundo = encolar(0, AHORA);
    UUID tercero = encolar(0, AHORA);
    transporte.hazQueFalleUnaVez();

    ResultadoDrenaje resultado = caso.ejecutar();

    assertEquals(2, resultado.enviados());
    assertEquals(1, resultado.fallidos());
    assertNotNull(repositorio.fila(segundo).enviadoEn());
    assertNotNull(repositorio.fila(tercero).enviadoEn());
  }

  @Test
  @DisplayName("los enviados viejos se purgan y los recientes no")
  void losEnviadosViejosSePurgan() {
    UUID viejo = encolar(0, AHORA);
    caso.ejecutar();
    repositorio.marcarEnviado(viejo, AHORA.minus(Duration.ofDays(31)));
    UUID reciente = encolar(0, AHORA);
    repositorio.marcarEnviado(reciente, AHORA.minus(Duration.ofDays(2)));

    ResultadoDrenaje resultado = caso.ejecutar();

    assertEquals(1, resultado.purgados());
    assertNull(
        repositorio.fila(viejo), "el cuerpo lleva datos personales y no se guarda para siempre");
    assertNotNull(repositorio.fila(reciente));
  }

  private UUID encolar(int intentos, Instant proximoIntento) {
    UUID id = UUID.randomUUID();
    repositorio.encolar(
        new CorreoPendiente(
            id,
            new CorreoElectronico("quien.compro@example.com"),
            "Asunto",
            "<p>Hola</p>",
            intentos),
        proximoIntento);
    return id;
  }

  /** Doble escrito a mano, sin Mockito (docs/06-testing.md). */
  private static final class TransporteDeCorreoFalso implements TransporteDeCorreo {

    private final List<String> enviados = new ArrayList<>();
    private boolean falla;
    private boolean fallaUnaVez;

    void hazQueFalle() {
      this.falla = true;
    }

    void hazQueFalleUnaVez() {
      this.fallaUnaVez = true;
    }

    @Override
    public void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {
      if (fallaUnaVez) {
        fallaUnaVez = false;
        throw new CorreoNoEnviadoException(
            new IllegalStateException("el servidor de correo no respondió"));
      }
      if (falla) {
        throw new CorreoNoEnviadoException(
            new IllegalStateException("el servidor de correo no respondió"));
      }
      enviados.add(destinatario.valor());
    }

    List<String> enviados() {
      return List.copyOf(enviados);
    }
  }
}
