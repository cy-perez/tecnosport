package co.tecnosport.api.bootstrap.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.ProveedorDePago;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Qué métodos de pago ofrece el sitio al arrancar sin variables de entorno, leído del {@code
 * application.yml} de verdad — mismo método que {@code ContraentregaEnApplicationYmlTest}, y por el
 * mismo motivo: las demás pruebas construyen la lista a mano con literales de Java y ninguna mira
 * la configuración real.
 *
 * <p><b>Addi fuera es una decisión de negocio del 14 de septiembre de 2026</b>, no una omisión:
 * activarlo exige que el sitio ya esté en línea. Mientras tanto ofrecerlo sería prometer un medio
 * de pago que no se puede honrar, y sin reventar nada — la URL del Web Checkout no le manda a Wompi
 * el método elegido, así que el comprador que eligiera Addi acabaría pagando con tarjeta un pedido
 * grabado como Addi.
 *
 * <p>Si alguien devuelve {@code ADDI} a esta lista, esta prueba cae, y el mensaje dice lo otro que
 * hay que hacer en el mismo commit: devolver la frase de los términos y condiciones.
 */
class MetodosDeWompiEnApplicationYmlTest {

  private PropertySource<?> applicationYml() throws IOException {
    List<PropertySource<?>> fuentes =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
    return fuentes.get(0);
  }

  private List<String> habilitadosPorOmision() throws IOException {
    String crudo =
        String.valueOf(applicationYml().getProperty("tecnosport.wompi.metodos.habilitados"));
    int dosPuntos = crudo.indexOf(':');
    String valor = dosPuntos < 0 ? crudo : crudo.substring(dosPuntos + 1).replace("}", "").trim();
    return Arrays.stream(valor.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  @Test
  void addiNoSeOfreceHastaQueWompiLoActive() throws IOException {
    assertFalse(
        habilitadosPorOmision().contains("ADDI"),
        "Addi volvió a la lista de métodos habilitados. Si Wompi ya lo activó, el mismo commit"
            + " tiene que devolver la frase de los términos y condiciones que se quitó con esto"
            + " (legales.terminos, medios de pago) — hoy dicen que no lo aceptamos.");
  }

  @Test
  void losMetodosPorOmisionSonLosQueLaCuentaTieneActivados() throws IOException {
    assertEquals(List.of("TARJETA", "PSE", "NEQUI", "BANCOLOMBIA"), habilitadosPorOmision());
  }

  /** Lo que diga el YAML tiene que poder leerlo el bean, no solo parecerse a un método. */
  @Test
  void elValorPorOmisionLoEntiendeElBeanDePropiedades() throws IOException {
    Set<MetodoPago> metodos =
        new PropiedadesMetodosDeWompi(habilitadosPorOmision()).comoMetodosDePago();

    assertEquals(
        Set.of(MetodoPago.TARJETA, MetodoPago.PSE, MetodoPago.NEQUI, MetodoPago.BANCOLOMBIA),
        metodos);
    assertTrue(metodos.stream().allMatch(m -> m.pasarela() == ProveedorDePago.WOMPI));
  }

  /**
   * Sistecrédito lo cobra otra pasarela y tiene su propio interruptor. Escribirlo en esta lista
   * habría dejado el método ofrecido en el checkout y enrutado a Wompi, que no lo conoce ({@code
   * adr/0048}).
   */
  @Test
  void sistecreditoNoSeHabilitaDesdeLaListaDeWompi() {
    PropiedadesMetodosDeWompi propiedades =
        new PropiedadesMetodosDeWompi(List.of("TARJETA", "SISTECREDITO"));

    IllegalStateException error =
        assertThrows(IllegalStateException.class, propiedades::comoMetodosDePago);
    assertTrue(error.getMessage().contains("SISTECREDITO"));
  }

  /** Un nombre mal escrito impide arrancar, en vez de apagar un método en silencio. */
  @Test
  void unMetodoDesconocidoImpideArrancar() {
    PropiedadesMetodosDeWompi propiedades =
        new PropiedadesMetodosDeWompi(List.of("TARJETA", "ADDI_PAGOS"));

    assertThrows(IllegalStateException.class, propiedades::comoMetodosDePago);
  }

  /** Contraentrega ya tiene su propio interruptor: aquí sería el segundo para la misma bombilla. */
  @Test
  void unMetodoQueNoPasaPorLaPasarelaImpideArrancar() {
    PropiedadesMetodosDeWompi propiedades =
        new PropiedadesMetodosDeWompi(List.of("TARJETA", "CONTRAENTREGA"));

    assertThrows(IllegalStateException.class, propiedades::comoMetodosDePago);
  }
}
