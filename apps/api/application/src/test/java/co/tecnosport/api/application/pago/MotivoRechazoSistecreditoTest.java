package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MotivoRechazoSistecreditoTest {

  @Test
  void losCodigosConocidosTienenSuMotivo() {
    assertEquals(
        MotivoRechazoSistecredito.SOLICITUD_EN_CURSO,
        MotivoRechazoSistecredito.de("801", "Rejected"));
    assertEquals(
        MotivoRechazoSistecredito.MONTO_INSUFICIENTE,
        MotivoRechazoSistecredito.de("802", "Rejected"));
  }

  /**
   * El código 4 de la prueba contra dev del 23 de septiembre: un rechazo sin código conocido. Y en
   * mayúsculas, que la pantalla no reconocía y el caso de uso sí.
   */
  @Test
  void unRechazoSinCodigoConocidoEsCreditoNegadoSinImportarLasMayusculas() {
    assertEquals(
        MotivoRechazoSistecredito.CREDITO_NEGADO, MotivoRechazoSistecredito.de("4", "Rejected"));
    assertEquals(
        MotivoRechazoSistecredito.CREDITO_NEGADO, MotivoRechazoSistecredito.de("4", "REJECTED"));
    assertEquals(
        MotivoRechazoSistecredito.CREDITO_NEGADO, MotivoRechazoSistecredito.de(null, "Expired"));
  }

  /**
   * Failed es la pasarela rota, no un crédito negado: decirle al comprador que se lo negaron
   * mentía.
   */
  @Test
  void unaFallaDeLaPasarelaOUnaEsperaAgotadaNoSeCuentanComoNegacion() {
    assertEquals(
        MotivoRechazoSistecredito.SIN_RESPUESTA, MotivoRechazoSistecredito.de(null, "Failed"));
    assertEquals(
        MotivoRechazoSistecredito.SIN_RESPUESTA, MotivoRechazoSistecredito.de(null, "Pending"));
  }
}
