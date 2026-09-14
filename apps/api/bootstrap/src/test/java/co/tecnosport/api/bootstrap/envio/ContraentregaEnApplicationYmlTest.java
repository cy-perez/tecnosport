package co.tecnosport.api.bootstrap.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * La regla de contraentrega del negocio, leída del {@code application.yml} de verdad.
 *
 * <p>La regla, en palabras, es del 14 de septiembre de 2026: <b>lo tecnológico por encima de COP
 * 2.000.000 no va contra entrega, y un celular igual o por debajo de esa cifra sí puede ir</b>. Es
 * una regla <b>por precio</b>, y por eso vive entera en {@code monto-maximo} — que se compara
 * contra el total del pedido— y no en ninguna lista de categorías.
 *
 * <p><b>Esta prueba existe porque la regla se implementó al revés una vez.</b> El primer intento
 * excluyó la línea {@code CELULARES} completa, lo que bloqueaba también los dos celulares del
 * catálogo (1.299.900 y 1.499.900), que son justo los que el negocio sí quiere despachar contra
 * entrega. Ninguna prueba lo atrapó, porque todas las demás construyen {@code
 * CriteriosContraentrega} a mano con literales de Java y nunca miran la configuración real. El
 * error se descubrió al releer la regla, no al correr el build.
 *
 * <p>Lo que estas afirmaciones impiden, en concreto: volver a poner {@code CELULARES} en la lista
 * de exclusión, y bajar el techo por debajo del celular más caro del catálogo. Las dos cosas rompen
 * la regla sin romper nada más.
 */
class ContraentregaEnApplicationYmlTest {

  /**
   * El celular más caro del catálogo sembrado. Si aparece uno más caro que siga por debajo del
   * techo, esta constante sube; si aparece uno por encima de 2.000.000, no califica y es correcto
   * que no lo haga.
   */
  private static final long CELULAR_MAS_CARO = 1_499_900L;

  private PropertySource<?> applicationYml() throws IOException {
    List<PropertySource<?>> fuentes =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
    return fuentes.get(0);
  }

  private String valorPorOmision(String propiedad) throws IOException {
    // Las propiedades vienen como `${VARIABLE:por-omision}`: lo que importa aquí es el valor con el
    // que arranca un despliegue que no define la variable, que es el modo en que este error llega
    // a producción sin que nadie lo escriba.
    String crudo = String.valueOf(applicationYml().getProperty(propiedad));
    int dosPuntos = crudo.indexOf(':');
    return dosPuntos < 0 ? crudo : crudo.substring(dosPuntos + 1).replace("}", "").trim();
  }

  @Test
  void elTechoDejaPasarElCelularMasCaroDelCatalogo() throws IOException {
    long techo = Long.parseLong(valorPorOmision("tecnosport.contraentrega.monto-maximo"));

    assertEquals(2_000_000L, techo, "El techo es la regla de lo tecnológico: COP 2.000.000.");
    assertTrue(
        techo > CELULAR_MAS_CARO,
        "El techo ("
            + techo
            + ") dejó por fuera el celular más caro del catálogo ("
            + CELULAR_MAS_CARO
            + "). La regla del negocio es que un celular por debajo de 2.000.000 sí va contra"
            + " entrega.");
  }

  /**
   * Vacía a propósito. La regla es por precio, y excluir la línea entera bloquearía los celulares
   * que el negocio sí despacha. Esta lista queda para el día que alguna línea no deba ir contra
   * entrega <b>a ningún precio</b>, que hoy no es el caso de ninguna.
   */
  @Test
  void ningunaLineaDeCatalogoEstaExcluidaPorSerLoQueEs() throws IOException {
    String excluidas = valorPorOmision("tecnosport.contraentrega.categorias-excluidas");

    assertTrue(
        excluidas.isBlank(),
        "Hay líneas excluidas de contraentrega por categoría ("
            + excluidas
            + "). Si la intención era lo tecnológico caro, eso lo hace monto-maximo por precio:"
            + " excluir la línea entera bloquea también los artículos baratos de esa línea.");
  }

  /** El piso también sale del YAML, y es el que reporta la ayuda pública de Skydropx. */
  @Test
  void elPisoEsElQueRecaudaLaTransportadora() throws IOException {
    long piso = Long.parseLong(valorPorOmision("tecnosport.contraentrega.monto-minimo"));

    assertEquals(2_000L, piso);
  }

  /**
   * Arranca apagada, y es lo que hace que todo lo de arriba sea reversible sin prisa: hoy ningún
   * comprador ve contraentrega, se encienda o no bien configurada.
   */
  @Test
  void contraentregaArrancaApagada() throws IOException {
    assertEquals("false", valorPorOmision("tecnosport.contraentrega.habilitada"));
  }
}
