package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El vigilante de los cobros extra de la transportadora.
 *
 * <p>Lo que estas pruebas cuidan, en orden de importancia: que <strong>no se avise dos veces del
 * mismo cobro</strong> —un correo repetido cada día deja de leerse, y este es el único aviso del
 * módulo cuyo hecho nunca desaparece—; que <strong>"no se pudo preguntar" no se confunda con "no
 * hay cobros"</strong>, porque el segundo tranquiliza y el primero no debería; y que un cobro al
 * que le falte un dato opcional se avise igual, porque el dinero ya salió de la cuenta.
 */
class AvisarSobrecostoDeEnvioTest {

  private static final Instant AHORA = Instant.parse("2026-09-18T12:00:00Z");
  private static final Duration VENTANA = Duration.ofDays(30);
  private static final CorreoElectronico NEGOCIO = new CorreoElectronico("contacto@tecnosport.co");

  private ConsultorDeSobrecostosFalso consultor;
  private RepositorioAvisosDeSobrecostoFalso avisos;
  private EnviadorDeCorreoFalso correo;
  private AvisarSobrecostoDeEnvio caso;

  @BeforeEach
  void preparar() {
    consultor = new ConsultorDeSobrecostosFalso();
    avisos = new RepositorioAvisosDeSobrecostoFalso();
    correo = new EnviadorDeCorreoFalso();
    caso =
        new AvisarSobrecostoDeEnvio(
            consultor, avisos, correo, new TextosDeCorreoFalso(), () -> AHORA, VENTANA, NEGOCIO);
  }

  private static SobrecostoDeEnvio sobrecosto(long monto, String guia, Instant detectadoEn) {
    return new SobrecostoDeEnvio(
        "envio-1",
        guia,
        "servientrega",
        "ExtraCharge::Overweight",
        Dinero.deCop(monto),
        detectadoEn);
  }

  /**
   * Un proveedor que no contesta no es una cuenta sin cobros. Si esto mandara correo, cada rato de
   * indisponibilidad sería una alarma; si devolviera "sin novedad", una caída larga se leería como
   * tranquilidad. No concluye, y lo dice.
   */
  @Test
  void siNoSePudoPreguntarNoSeAvisaYSeDice() {
    consultor.noResponde();

    ResultadoVigilanciaSobrecostos resultado = caso.ejecutar();

    assertFalse(resultado.seSupo());
    assertEquals(0, correo.enviados.size());
  }

  @Test
  void sinCobrosNoSeMandaNingunCorreo() {
    consultor.devolver();

    ResultadoVigilanciaSobrecostos resultado = caso.ejecutar();

    assertTrue(resultado.seSupo());
    assertEquals(0, resultado.encontrados());
    assertEquals(0, correo.enviados.size());
  }

  @Test
  void unCobroNuevoSeAvisaConSuMontoYSuGuia() {
    consultor.devolver(sobrecosto(8_400, "873837506712", AHORA.minusSeconds(3600)));

    ResultadoVigilanciaSobrecostos resultado = caso.ejecutar();

    assertEquals(1, resultado.avisados());
    assertEquals(1, correo.enviados.size());
    EnviadorDeCorreoFalso.Correo enviado = correo.enviados.get(0);
    assertEquals(NEGOCIO, enviado.destinatario());
    assertTrue(enviado.cuerpo().contains("8400"), enviado.cuerpo());
    assertTrue(enviado.cuerpo().contains("873837506712"), enviado.cuerpo());
    assertTrue(enviado.cuerpo().contains("ExtraCharge::Overweight"), enviado.cuerpo());
  }

  /**
   * La razón de existir de la tabla. Sin el reclamo, el mismo cobro saldría en un correo cada día
   * mientras siguiera dentro de la ventana de treinta días — treinta correos por un solo cobro, y
   * el trigésimo primero ya no lo lee nadie.
   */
  @Test
  void elMismoCobroNoSeAvisaDosVeces() {
    consultor.devolver(sobrecosto(8_400, "873837506712", AHORA.minusSeconds(3600)));

    caso.ejecutar();
    ResultadoVigilanciaSobrecostos segunda = caso.ejecutar();

    assertTrue(segunda.seSupo());
    assertEquals(1, segunda.encontrados());
    assertEquals(0, segunda.avisados());
    assertEquals(1, correo.enviados.size());
  }

  /**
   * Y la otra cara: si la transportadora reliquida el mismo cargo por otra cifra, la clave cambia y
   * se vuelve a avisar. Enterarse dos veces de algo de dinero es el error que se prefiere.
   */
  @Test
  void elMismoCargoPorOtroMontoSiVuelveAAvisar() {
    consultor.devolver(sobrecosto(8_400, "873837506712", AHORA.minusSeconds(3600)));
    caso.ejecutar();

    consultor.devolver(sobrecosto(12_900, "873837506712", AHORA.minusSeconds(3600)));
    ResultadoVigilanciaSobrecostos segunda = caso.ejecutar();

    assertEquals(1, segunda.avisados());
    assertEquals(2, correo.enviados.size());
    assertTrue(correo.enviados.get(1).cuerpo().contains("12900"));
  }

  /**
   * Varios cobros nuevos son un solo correo: diez correos seguidos se leen igual de mal que
   * ninguno.
   */
  @Test
  void variosCobrosNuevosVanEnUnSoloCorreo() {
    consultor.devolver(
        sobrecosto(8_400, "873837506712", AHORA.minusSeconds(3600)),
        sobrecosto(3_100, "873837506713", AHORA.minusSeconds(7200)));

    ResultadoVigilanciaSobrecostos resultado = caso.ejecutar();

    assertEquals(2, resultado.avisados());
    assertEquals(1, correo.enviados.size());
    assertTrue(correo.enviados.get(0).cuerpo().contains("8400"));
    assertTrue(correo.enviados.get(0).cuerpo().contains("3100"));
  }

  /**
   * Los dos campos que el esquema declara nulables. Un cobro sin guía y sin fecha sigue siendo
   * dinero que se fue de la cuenta, así que se avisa igual — con un relleno traducido y no con un
   * "null" dentro del correo, que es lo que saldría de concatenar sin mirar.
   */
  @Test
  void unCobroSinGuiaNiFechaSeAvisaIgualYSinNulos() {
    consultor.devolver(sobrecosto(8_400, null, null));

    ResultadoVigilanciaSobrecostos resultado = caso.ejecutar();

    assertEquals(1, resultado.avisados());
    String cuerpo = correo.enviados.get(0).cuerpo();
    assertFalse(cuerpo.contains("null"), cuerpo);
    assertTrue(cuerpo.contains("envio.sobrecosto.sin_guia"), cuerpo);
    assertTrue(cuerpo.contains("envio.sobrecosto.sin_fecha"), cuerpo);
  }

  /**
   * La ventana se cuenta desde el reloj y no desde "siempre". Preguntar sin acotar dependería del
   * orden en que la plataforma devuelve los cobros, que no está documentado, y arrastraría la
   * cuenta entera cada día.
   */
  @Test
  void preguntaSoloPorLaVentanaConfigurada() {
    consultor.devolver();

    caso.ejecutar();

    assertEquals(AHORA.minus(VENTANA), consultor.pedidoDesde);
  }

  /** El correo sale después del reclamo, así que un cobro ya avisado no arma un correo vacío. */
  @Test
  void siNadaEsNuevoNoSaleUnCorreoConLaListaVacia() {
    SobrecostoDeEnvio cobro = sobrecosto(8_400, "873837506712", AHORA.minusSeconds(3600));
    avisos.reclamarAviso(cobro.clave(), AHORA.minusSeconds(60));
    consultor.devolver(cobro);

    caso.ejecutar();

    assertEquals(0, correo.enviados.size());
  }

  // --- dobles de prueba escritos a mano, ver docs/06-testing.md -----------------------------

  private static final class ConsultorDeSobrecostosFalso implements ConsultorDeSobrecostos {

    private Optional<List<SobrecostoDeEnvio>> respuesta = Optional.of(List.of());
    private Instant pedidoDesde;

    void devolver(SobrecostoDeEnvio... cobros) {
      this.respuesta = Optional.of(List.of(cobros));
    }

    void noResponde() {
      this.respuesta = Optional.empty();
    }

    @Override
    public Optional<List<SobrecostoDeEnvio>> desde(Instant desde) {
      this.pedidoDesde = desde;
      return respuesta;
    }
  }

  private static final class RepositorioAvisosDeSobrecostoFalso
      implements RepositorioAvisosDeSobrecosto {

    private final Set<String> reclamadas = new HashSet<>();

    @Override
    public boolean reclamarAviso(String clave, Instant ahora) {
      return reclamadas.add(clave);
    }
  }

  private static final class EnviadorDeCorreoFalso implements EnviadorDeCorreo {

    record Correo(CorreoElectronico destinatario, String asunto, String cuerpo) {}

    private final List<Correo> enviados = new ArrayList<>();

    @Override
    public void enviar(CorreoElectronico destinatario, String asunto, String cuerpo) {
      enviados.add(new Correo(destinatario, asunto, cuerpo));
    }
  }

  /**
   * Devuelve la clave y sus argumentos, no el texto real. Así las aserciones miran qué datos
   * entraron en el correo sin quedar amarradas a la redacción, que vive en los properties y cambia
   * sin que la lógica cambie.
   */
  private static final class TextosDeCorreoFalso implements TextosDeCorreo {

    @Override
    public String texto(TextoDeCorreo texto, Object... argumentos) {
      StringBuilder resultado = new StringBuilder(texto.clave());
      for (Object argumento : argumentos) {
        resultado.append('|').append(argumento);
      }
      return resultado.toString();
    }
  }
}
