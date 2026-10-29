package co.tecnosport.api.bootstrap.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.MargenMinimo;
import co.tecnosport.api.domain.proveedores.PrecioSobreGananciaMaximaException;
import co.tecnosport.api.domain.proveedores.SugerenciaDePrecioTecnologia;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * El tope de ganancia de tecnología con el que arranca el sitio sin variables de entorno, leído del
 * {@code application.yml} de verdad: 400.000, la decisión del negocio del 26 de octubre de 2026
 * (ADR-0095, decisión 8).
 */
class GananciaMaximaTecnologiaEnApplicationYmlTest {

  private static final String PROPIEDAD = "tecnosport.proveedores.tecnologia.ganancia-maxima";

  private static PropertySource<?> yml() throws IOException {
    List<PropertySource<?>> fuentes =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
    return fuentes.get(0);
  }

  private static String crudo() throws IOException {
    return String.valueOf(yml().getProperty(PROPIEDAD));
  }

  private static PropiedadesProveedores.Tecnologia con(long gananciaMaxima) {
    return new PropiedadesProveedores.Tecnologia(2, Duration.ofDays(7), gananciaMaxima, 1);
  }

  private static PropiedadesProveedores.Tecnologia conMinima(long gananciaMinima) {
    return new PropiedadesProveedores.Tecnologia(2, Duration.ofDays(7), 400_000, gananciaMinima);
  }

  /** La mínima del 29 de octubre de 2026 (ADR-0101): 50.000, y nunca por encima de la máxima. */
  @Test
  void laMinimaPorOmisionEsLaDelNegocioYNoPasaDeLaMaxima() throws IOException {
    String valor =
        String.valueOf(yml().getProperty("tecnosport.proveedores.tecnologia.ganancia-minima"));
    assertTrue(valor.startsWith("${PROVEEDORES_TECNOLOGIA_GANANCIA_MINIMA:"), valor);
    long porOmision = Long.parseLong(valor.substring(valor.indexOf(':') + 1).replace("}", ""));

    assertEquals(50_000L, porOmision);
    MargenMinimo margen = conMinima(porOmision).aMargen();
    Dinero costo = Dinero.deCop(1_900_000);
    assertTrue(margen.alcanza(Dinero.deCop(1_950_000), costo));
    assertFalse(margen.alcanza(Dinero.deCop(1_949_999), costo));
    margen.exigir(Dinero.deCop(2_300_000), costo, "el techo");
    assertThrows(
        PrecioSobreGananciaMaximaException.class,
        () -> margen.exigir(Dinero.deCop(2_300_001), costo, "el techo"));
    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> conMinima(399_001));
    assertTrue(error.getMessage().contains("PROVEEDORES_TECNOLOGIA_GANANCIA_MINIMA"));
    assertThrows(IllegalStateException.class, () -> conMinima(0));
  }

  @Test
  void elTopePorOmisionEsElDelNegocio() throws IOException {
    String valor = crudo();
    long porOmision = Long.parseLong(valor.substring(valor.indexOf(':') + 1).replace("}", ""));

    assertEquals(400_000L, porOmision);
    assertEquals(
        new SugerenciaDePrecioTecnologia(Dinero.deCop(400_000)), con(porOmision).aSugerencia());
  }

  @Test
  void seCambiaPorSuVariableDeEntorno() throws IOException {
    assertTrue(crudo().startsWith("${PROVEEDORES_TECNOLOGIA_GANANCIA_MAXIMA:"), crudo());
  }

  /** Lo que deja el enlace si la propiedad falta es 0: no arranca, y dice cuál es. */
  @Test
  void sinTopeNoArranca() {
    IllegalStateException error = assertThrows(IllegalStateException.class, () -> con(0));

    assertTrue(error.getMessage().contains("PROVEEDORES_TECNOLOGIA_GANANCIA_MAXIMA"));
  }

  @Test
  void unTopeNegativoNoArranca() {
    assertThrows(IllegalStateException.class, () -> con(-400_000));
  }

  /** Con menos de mil, bajar a la unidad de mil podría dejar la sugerencia bajo el costo. */
  @Test
  void unTopeDeMenosDeMilNoArranca() {
    assertThrows(IllegalStateException.class, () -> con(999));
  }
}
