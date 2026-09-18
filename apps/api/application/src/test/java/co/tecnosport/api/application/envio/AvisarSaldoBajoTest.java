package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Sin saldo no hay guías, y hasta esto nadie se enteraba: la cuenta llegó a COP 388 y la forma de
 * descubrirlo fue que la emisión de un pedido ya pagado no salió.
 *
 * <p>Lo que estas pruebas cuidan no es la comparación de dos números: es que <strong>"no se pudo
 * preguntar" no se convierta en una alarma de dinero</strong>. Un proveedor caído media hora y una
 * cuenta sin fondos llevan los dos a mirar la cuenta, pero solo uno significa que el despacho está
 * detenido, y confundirlos enseña a ignorar el aviso.
 */
class AvisarSaldoBajoTest {

  private static final Dinero UMBRAL = Dinero.deCop(50_000);
  private static final CorreoElectronico DESTINATARIO =
      new CorreoElectronico("contacto@tecnosport.co");

  private final CorreosFalsos correos = new CorreosFalsos();

  private AvisarSaldoBajo casoDeUso(ConsultorDeSaldo consultor) {
    return new AvisarSaldoBajo(consultor, correos, new TextosFalsos(), UMBRAL, DESTINATARIO);
  }

  @Test
  void avisa_cuando_el_saldo_esta_por_debajo_del_umbral() {
    ResultadoVigilanciaSaldo resultado =
        casoDeUso(() -> Optional.of(Dinero.deCop(10_088))).ejecutar();

    assertTrue(resultado.avisado());
    assertEquals(Dinero.deCop(10_088), resultado.saldo().orElseThrow());
    assertEquals(1, correos.enviados());
    assertTrue(
        correos.cuerpos.getFirst().contains("10088"),
        "el correo dice cuánto queda, no solo que queda poco");
    assertTrue(correos.cuerpos.getFirst().contains("50000"), "y contra qué umbral se comparó");
  }

  @Test
  void no_avisa_cuando_hay_saldo_de_sobra() {
    ResultadoVigilanciaSaldo resultado =
        casoDeUso(() -> Optional.of(Dinero.deCop(120_000))).ejecutar();

    assertFalse(resultado.avisado());
    assertEquals(0, correos.enviados());
  }

  /** El umbral es el límite de lo aceptable, no el primer valor inaceptable. */
  @Test
  void el_saldo_justo_en_el_umbral_no_avisa() {
    assertFalse(casoDeUso(() -> Optional.of(UMBRAL)).ejecutar().avisado());
    assertEquals(0, correos.enviados());
  }

  /**
   * La distinción que da nombre a esta clase de pruebas: el puerto devuelve vacío cuando la
   * plataforma no contestó, y eso no manda ningún correo.
   */
  @Test
  void no_poder_preguntar_no_es_quedarse_sin_saldo() {
    ResultadoVigilanciaSaldo resultado = casoDeUso(Optional::empty).ejecutar();

    assertFalse(resultado.avisado());
    assertTrue(resultado.saldo().isEmpty());
    assertEquals(0, correos.enviados(), "un proveedor caído no le escribe a nadie");
  }

  /** Un saldo en cero sí es una alarma, y es el caso que de verdad ocurrió. */
  @Test
  void un_saldo_en_cero_avisa() {
    assertTrue(casoDeUso(() -> Optional.of(Dinero.deCop(0))).ejecutar().avisado());
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  private static final class CorreosFalsos implements EnviadorDeCorreo {

    private final List<String> cuerpos = new ArrayList<>();

    @Override
    public void enviar(CorreoElectronico destinatario, String asunto, String cuerpo) {
      cuerpos.add(cuerpo);
    }

    int enviados() {
      return cuerpos.size();
    }
  }

  /** La clave con sus argumentos pegados: comprueba los datos sin atarse a la redacción. */
  private static final class TextosFalsos implements TextosDeCorreo {

    @Override
    public String dinero(co.tecnosport.api.domain.compartido.Dinero valor) {
      return valor.valor().toPlainString();
    }

    @Override
    public String texto(TextoDeCorreo texto, Object... argumentos) {
      StringBuilder sb = new StringBuilder(texto.clave());
      for (Object argumento : argumentos) {
        sb.append('|').append(argumento);
      }
      return sb.toString();
    }
  }
}
