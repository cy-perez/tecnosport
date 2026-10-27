package co.tecnosport.api.bootstrap.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * La tarifa de Wompi con la que arranca el sitio sin variables de entorno, leída del {@code
 * application.yml} de verdad — mismo método que {@code MetodosDeWompiEnApplicationYmlTest}. Tiene
 * que ser la del plan leído el 26 de octubre de 2026, que es también la que la {@code V123} lleva
 * escrita a mano: si una cambia sin la otra, el panel ofrecería recalcular todo el catálogo el
 * primer día.
 */
class ComisionWompiEnApplicationYmlTest {

  private String porOmision(String propiedad) throws IOException {
    List<PropertySource<?>> fuentes =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
    String crudo = String.valueOf(fuentes.get(0).getProperty(propiedad));
    int dosPuntos = crudo.indexOf(':');
    return dosPuntos < 0 ? crudo : crudo.substring(dosPuntos + 1).replace("}", "").trim();
  }

  private PropiedadesComisionWompi propiedadesPorOmision() throws IOException {
    return new PropiedadesComisionWompi(
        new BigDecimal(porOmision("tecnosport.wompi.comision.variable")),
        Long.valueOf(porOmision("tecnosport.wompi.comision.fija")),
        new BigDecimal(porOmision("tecnosport.wompi.comision.iva")));
  }

  @Test
  void laTarifaPorOmisionEsLaDelPlanDel26DeOctubre() throws IOException {
    assertEquals(
        new ComisionDePasarela(new BigDecimal("0.0265"), Dinero.deCop(700), new BigDecimal("0.19")),
        propiedadesPorOmision().comoComision());
  }

  /** Cada valor sale de su variable de entorno, no de un literal sin forma de cambiarlo. */
  @Test
  void cadaTarifaSeCambiaPorSuVariableDeEntorno() throws IOException {
    List<PropertySource<?>> fuentes =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
    PropertySource<?> yml = fuentes.get(0);

    assertTrue(
        String.valueOf(yml.getProperty("tecnosport.wompi.comision.variable"))
            .startsWith("${WOMPI_COMISION_VARIABLE:"));
    assertTrue(
        String.valueOf(yml.getProperty("tecnosport.wompi.comision.fija"))
            .startsWith("${WOMPI_COMISION_FIJA:"));
    assertTrue(
        String.valueOf(yml.getProperty("tecnosport.wompi.comision.iva"))
            .startsWith("${WOMPI_COMISION_IVA:"));
  }

  /** El error más probable: escribir el porcentaje en vez de la fracción. No arranca. */
  @Test
  void unPorcentajeEnVezDeUnaFraccionNoArranca() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PropiedadesComisionWompi(new BigDecimal("2.65"), 700L, new BigDecimal("0.19")));

    assertTrue(error.getMessage().contains("tecnosport.wompi.comision.variable"));
  }

  @Test
  void unIvaEscritoComoPorcentajeNoArranca() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PropiedadesComisionWompi(new BigDecimal("0.0265"), 700L, new BigDecimal("19")));

    assertTrue(error.getMessage().contains("tecnosport.wompi.comision.iva"));
  }

  @Test
  void unaFijaNegativaNoArranca() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PropiedadesComisionWompi(
                    new BigDecimal("0.0265"), -700L, new BigDecimal("0.19")));

    assertTrue(error.getMessage().contains("tecnosport.wompi.comision.fija"));
  }

  @Test
  void unaTarifaQueFaltaDiceCualVariable() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PropiedadesComisionWompi(
                    new BigDecimal("0.0265"), null, new BigDecimal("0.19")));

    assertTrue(error.getMessage().contains("WOMPI_COMISION_FIJA"));
  }

  /** 0,9 × 1,19 ≥ 1: la comisión se llevaría el precio entero. Lo rechaza el dominio. */
  @Test
  void unaComisionQueSeLlevaElPrecioEnteroNoArranca() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PropiedadesComisionWompi(new BigDecimal("0.9"), 700L, new BigDecimal("0.19")));

    assertTrue(error.getMessage().contains("tecnosport.wompi.comision"));
  }
}
