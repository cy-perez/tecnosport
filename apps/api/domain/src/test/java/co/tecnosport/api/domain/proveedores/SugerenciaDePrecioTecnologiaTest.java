package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import org.junit.jupiter.api.Test;

class SugerenciaDePrecioTecnologiaTest {

  /** La del negocio desde el 26 de octubre de 2026. */
  private static final Dinero TOPE = Dinero.deCop(400_000);

  private static Dinero sugerir(long costo, Long mercado) {
    return new SugerenciaDePrecioTecnologia(TOPE)
        .contadoSugerido(Dinero.deCop(costo), mercado == null ? null : Dinero.deCop(mercado));
  }

  /** El ejemplo de la ADR: con mercado de 1.000.000 el contado sugerido es 1.000.000. */
  @Test
  void mandaElMercadoCuandoNoLeGanaAlCostoMasQueElTope() {
    assertEquals(Dinero.deCop(1_000_000), sugerir(675_000, 1_000_000L));
  }

  /** El de mercado no se redondea: es el precio contra el que se compite. */
  @Test
  void elDeMercadoQuedaTalCualAunqueNoSeaRedondo() {
    assertEquals(Dinero.deCop(849_900), sugerir(675_000, 849_900L));
  }

  @Test
  void mandaElTopeCuandoElMercadoLeGanaMas() {
    // 1.200.000 + 400.000 = 1.600.000 < 2.000.000
    assertEquals(Dinero.deCop(1_600_000), sugerir(1_200_000, 2_000_000L));
  }

  /** 675.500 + 400.000 = 1.075.500: subir a 1.076.000 rompería el tope; baja a 1.075.000. */
  @Test
  void conElTopeBajaALaUnidadDeMilAnterior() {
    assertEquals(Dinero.deCop(1_075_000), sugerir(675_500, 1_200_000L));
    assertEquals(Dinero.deCop(1_075_000), sugerir(675_999, 1_200_000L));
  }

  /** En el borde exacto el mercado y el tope coinciden: manda el mercado, sin redondear. */
  @Test
  void enElBordeExactoMandaElMercado() {
    assertEquals(Dinero.deCop(1_075_500), sugerir(675_500, 1_075_500L));
    // Un peso más arriba ya manda el tope, y baja a la unidad de mil.
    assertEquals(Dinero.deCop(1_075_000), sugerir(675_500, 1_075_501L));
  }

  @Test
  void sinPrecioDeMercadoNoHaySugerencia() {
    assertNull(sugerir(675_000, null));
  }

  /** No hay ganancia mínima (ADR-0095): un mercado por debajo del costo se sugiere igual. */
  @Test
  void noInventaUnaGananciaMinima() {
    assertEquals(Dinero.deCop(680_000), sugerir(675_000, 680_000L));
    assertEquals(Dinero.deCop(600_000), sugerir(675_000, 600_000L));
  }

  @Test
  void deUnaConfiguracionUsaSuCostoYSuMercado() {
    SugerenciaDePrecioTecnologia sugerencia = new SugerenciaDePrecioTecnologia(TOPE);
    ConfiguracionTecnologia conMercado =
        ConfiguracionTecnologia.deLista(
            "a17-1-sim",
            "Galaxy A17",
            "8GB",
            "256GB",
            "1 SIM",
            Dinero.deCop(675_500),
            Dinero.deCop(1_200_000),
            List.of());
    ConfiguracionTecnologia sinMercado =
        ConfiguracionTecnologia.deLista(
            "a17-2-sim", "Galaxy A17", "8GB", "256GB", "2 SIM", Dinero.deCop(690_000), null, null);

    assertEquals(Dinero.deCop(1_075_000), sugerencia.contadoSugerido(conMercado));
    assertNull(sugerencia.contadoSugerido(sinMercado));
  }

  /** Con menos de mil, bajar a la unidad de mil podría dejar la sugerencia bajo el costo. */
  @Test
  void unaGananciaMaximaDeMenosDeMilNoSeAcepta() {
    assertThrows(
        ExcepcionDeDominio.class, () -> new SugerenciaDePrecioTecnologia(Dinero.deCop(999)));
    assertThrows(NullPointerException.class, () -> new SugerenciaDePrecioTecnologia(null));
  }
}
