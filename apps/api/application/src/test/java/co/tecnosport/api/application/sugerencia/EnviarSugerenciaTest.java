package co.tecnosport.api.application.sugerencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.application.legal.RepositorioAutorizacionesFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.legal.AutorizacionRequeridaException;
import co.tecnosport.api.domain.legal.OrigenAutorizacion;
import co.tecnosport.api.domain.sugerencia.Sugerencia;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnviarSugerenciaTest {

  private static final Instant AHORA = Instant.parse("2026-09-26T15:00:00Z");
  private static final String VERSION_POLITICA = "2026-09-14";
  private static final CorreoElectronico BUZON = new CorreoElectronico("contacto@tecnosport.co");

  /** Doble escrito a mano, sin Mockito (docs/06-testing.md). */
  private static final class RepositorioSugerenciasFalso implements RepositorioSugerencias {

    private final List<Sugerencia> guardadas = new ArrayList<>();

    @Override
    public void guardar(Sugerencia sugerencia) {
      guardadas.add(sugerencia);
    }
  }

  private record CorreoEnviado(CorreoElectronico destinatario, String asunto, String cuerpo) {}

  private static final class EnviadorFalso implements EnviadorDeCorreo {

    private final List<CorreoEnviado> enviados = new ArrayList<>();
    private boolean falla;

    @Override
    public void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {
      if (falla) {
        throw new CorreoNoEnviadoException(new IllegalStateException("smtp caído"));
      }
      enviados.add(new CorreoEnviado(destinatario, asunto, cuerpoHtml));
    }
  }

  private RepositorioSugerenciasFalso sugerencias;
  private RepositorioAutorizacionesFalso autorizaciones;
  private EnviadorFalso enviador;
  private LimitadorDeIntentosFalso limitador;
  private EnviarSugerencia enviarSugerencia;

  @BeforeEach
  void prepararCasoDeUso() {
    sugerencias = new RepositorioSugerenciasFalso();
    autorizaciones = new RepositorioAutorizacionesFalso();
    enviador = new EnviadorFalso();
    limitador = new LimitadorDeIntentosFalso();
    enviarSugerencia =
        new EnviarSugerencia(
            sugerencias,
            autorizaciones,
            enviador,
            new TextosDeCorreoFalso(),
            new RelojFalso(AHORA),
            limitador,
            3,
            Duration.ofMinutes(60),
            VERSION_POLITICA,
            BUZON);
  }

  /**
   * El caso central del diseño: sin correo no hay dato personal, así que no hay autorización que
   * exigir ni constancia que guardar. Pedir el sí de la Ley 1581 para un texto anónimo sería pedir
   * permiso para tratar datos que nadie dio.
   */
  @Test
  void unaSugerenciaAnonimaNoPideAutorizacionNiDejaConstancia() {
    enviarSugerencia.ejecutar(
        new EnviarSugerenciaComando("Sería bueno filtrar por talla.", null, false, "10.0.0.1"));

    assertEquals(1, sugerencias.guardadas.size());
    assertEquals(Optional.empty(), sugerencias.guardadas.getFirst().correo());
    assertTrue(autorizaciones.todas().isEmpty());
  }

  @Test
  void conCorreoYAutorizacionGuardaLaConstanciaConLaVersionDelServidor() {
    enviarSugerencia.ejecutar(
        new EnviarSugerenciaComando("Gracias.", "Ana@Ejemplo.com", true, "10.0.0.1"));

    assertEquals(1, autorizaciones.todas().size());
    AutorizacionDatos constancia = autorizaciones.todas().getFirst();
    assertEquals(OrigenAutorizacion.SUGERENCIA, constancia.origen());
    assertEquals(VERSION_POLITICA, constancia.versionPolitica());
    assertEquals("10.0.0.1", constancia.direccionIp());
    // El buzón nunca tiene cuenta detrás: no hace falta registrarse para escribir.
    assertEquals(Optional.empty(), constancia.usuarioId());
    // Normalizado por `CorreoElectronico`, que es el único sitio donde eso pasa.
    assertEquals(new CorreoElectronico("ana@ejemplo.com"), constancia.correo());
  }

  /**
   * Sin el sí no se guarda el correo de nadie, y —esto es lo que la prueba fija— tampoco se guarda
   * la sugerencia: si se guardara primero, el dato personal de quien no consintió quedaría escrito
   * en la base hasta que la transacción reventara.
   */
  @Test
  void conCorreoYSinAutorizacionNoGuardaNada() {
    assertThrows(
        AutorizacionRequeridaException.class,
        () ->
            enviarSugerencia.ejecutar(
                new EnviarSugerenciaComando("Hola.", "ana@ejemplo.com", false, "10.0.0.1")));

    assertTrue(sugerencias.guardadas.isEmpty());
    assertTrue(autorizaciones.todas().isEmpty());
  }

  @Test
  void avisaAlBuzonDelNegocioConElMensajeYElRemitente() {
    enviarSugerencia.ejecutar(
        new EnviarSugerenciaComando("Falta el color negro.", "ana@ejemplo.com", true, "10.0.0.1"));

    assertEquals(1, enviador.enviados.size());
    CorreoEnviado aviso = enviador.enviados.getFirst();
    assertEquals(BUZON, aviso.destinatario());
    assertTrue(aviso.cuerpo().contains("ana@ejemplo.com"), aviso.cuerpo());
    assertTrue(aviso.cuerpo().contains("Falta el color negro."), aviso.cuerpo());
  }

  /** Sin correo, el cuerpo del aviso dice que fue anónima en vez de dejar un hueco. */
  @Test
  void elAvisoDeUnaAnonimaLoDice() {
    enviarSugerencia.ejecutar(new EnviarSugerenciaComando("Falta stock.", "  ", false, "10.0.0.1"));

    String cuerpo = enviador.enviados.getFirst().cuerpo();
    assertTrue(cuerpo.contains("sugerencia.anonima"), cuerpo);
  }

  /**
   * <b>A quien escribió no se le manda nada.</b> Un acuse automático a una dirección que cualquiera
   * puede teclear convierte el buzón en una máquina de mandar correos a terceros, que es el abuso
   * clásico de un formulario público.
   */
  @Test
  void noLeMandaNingunCorreoAQuienEscribio() {
    enviarSugerencia.ejecutar(
        new EnviarSugerenciaComando("Hola.", "ana@ejemplo.com", true, "10.0.0.1"));

    List<CorreoElectronico> destinatarios =
        enviador.enviados.stream().map(CorreoEnviado::destinatario).toList();
    assertEquals(List.of(BUZON), destinatarios);
  }

  /**
   * El aviso es una notificación, no el registro: la fila ya está guardada. Tumbar la transacción
   * por no poder encolar un correo interno perdería el texto de quien acaba de escribirnos para
   * proteger algo que el repositorio ya hace innecesario.
   */
  @Test
  void siElAvisoNoSePuedeEncolarLaSugerenciaSeGuardaIgual() {
    enviador.falla = true;

    enviarSugerencia.ejecutar(new EnviarSugerenciaComando("Hola.", null, false, "10.0.0.1"));

    assertEquals(1, sugerencias.guardadas.size());
  }

  @Test
  void elLimitePorCorreoCorta() {
    limitador.denegarSiempre();

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () ->
            enviarSugerencia.ejecutar(
                new EnviarSugerenciaComando("Hola.", "ana@ejemplo.com", true, "10.0.0.1")));

    assertTrue(sugerencias.guardadas.isEmpty());
  }

  /**
   * Una anónima no consume el cupo por correo —no hay correo que contar— y a cambio queda solo bajo
   * el límite por IP del filtro. Está escrito a propósito: si alguien lo cambia para contar también
   * las anónimas, tiene que decidir contra qué llave, y esta prueba le obliga a mirarlo.
   */
  @Test
  void unaAnonimaNoPasaPorElLimitePorCorreo() {
    limitador.denegarSiempre();

    enviarSugerencia.ejecutar(new EnviarSugerenciaComando("Hola.", null, false, "10.0.0.1"));

    assertEquals(1, sugerencias.guardadas.size());
  }

  /**
   * El mensaje se valida antes de guardar y antes de avisar. Lo que fija esta prueba es que el
   * agregado sigue siendo quien decide, y no una anotación de la capa de presentación que solo
   * corre cuando alguien se acuerda de ponerla.
   */
  @Test
  void unMensajeVacioNoLlegaAGuardarse() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> enviarSugerencia.ejecutar(new EnviarSugerenciaComando("  ", null, false, "1.1.1.1")));

    assertTrue(sugerencias.guardadas.isEmpty());
    assertTrue(enviador.enviados.isEmpty());
  }
}
