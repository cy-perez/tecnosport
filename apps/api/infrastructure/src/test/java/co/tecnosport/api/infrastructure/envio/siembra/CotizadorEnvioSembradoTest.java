package co.tecnosport.api.infrastructure.envio.siembra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.envio.Bulto;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.ResultadoCotizacion;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Lo que este doble tiene que cumplir para que el recorrido de Playwright pruebe algo: una tarifa
 * que {@code CotizarEnvio} acepte. Si deja de estar vigente, o el costo se vuelve cero, el
 * recorrido seguiría pasando por los motivos equivocados.
 */
class CotizadorEnvioSembradoTest {

  private static final Instant AHORA = Instant.parse("2026-09-13T12:00:00Z");

  private static final Direccion MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null);

  private final CotizadorEnvioSembrado cotizador = new CotizadorEnvioSembrado(() -> AHORA);

  private CotizacionEnvio cotizacion(boolean conRecaudo) {
    return new CotizacionEnvio(
        MEDELLIN,
        List.of(new Bulto(new Paquete(180, 30, 25, 4), Dinero.deCop(50_000))),
        conRecaudo);
  }

  @Test
  void devuelve_una_tarifa_vigente() {
    List<TarifaEnvio> tarifas = tarifas(false);

    assertEquals(1, tarifas.size());
    assertTrue(tarifas.getFirst().estaVigente(AHORA));
  }

  /**
   * Una tarifa de cero pasaría todas las demás comprobaciones y dejaría el desglose mostrando "$ 0"
   * — el recorrido seguiría verde sin probar que el flete se cobra.
   */
  @Test
  void el_costo_no_es_cero() {
    assertTrue(tarifas(false).getFirst().costo().valor().signum() > 0);
  }

  /**
   * Con recaudo responde igual: el recorrido de contraentrega necesita una tarifa que lo admita, y
   * un doble que se comportara de dos maneras escondería cuál de las dos se está probando.
   */
  @Test
  void admite_contraentrega_tambien_cuando_se_pide_con_recaudo() {
    assertTrue(tarifas(true).getFirst().admiteContraentrega());
    assertEquals(tarifas(false).getFirst().costo(), tarifas(true).getFirst().costo());
  }

  /** El doble siempre responde con tarifas: si algun dia responde otra cosa, esto se cae aqui. */
  private List<TarifaEnvio> tarifas(boolean conRecaudo) {
    return assertInstanceOf(
            ResultadoCotizacion.ConTarifas.class, cotizador.cotizar(cotizacion(conRecaudo)))
        .tarifas();
  }
}
