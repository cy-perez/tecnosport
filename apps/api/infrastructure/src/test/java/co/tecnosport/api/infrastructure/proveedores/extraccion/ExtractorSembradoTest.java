package co.tecnosport.api.infrastructure.proveedores.extraccion;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.ResultadoExtraccion;
import co.tecnosport.api.application.proveedores.TextoDePublicacion;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractorSembradoTest {

  private final ExtractorSembrado extractor = new ExtractorSembrado();

  /** Todo lo que sale de aquí lleva alertas: confianza cero y tipo desconocido, a propósito. */
  @Test
  void sacaElPrecioYLaPrimeraLineaConSentidoYDejaLaConfianzaEnCero() {
    ResultadoExtraccion resultado =
        extractor.extraer(
            new TextoDePublicacion(
                "*Nueva colección* 😍\nBolso de dama mediano 👜\n💰 *53.000*",
                List.of(),
                LineaCatalogo.BOLSOS));

    assertThat(resultado.producto().esProducto()).isTrue();
    assertThat(resultado.producto().titulo()).isEqualTo("Bolso de dama mediano");
    assertThat(resultado.producto().precioProveedor()).isEqualTo(Dinero.deCop(53000));
    assertThat(resultado.producto().tipo()).isEqualTo(TipoProductoProveedor.OTRO);
    assertThat(resultado.producto().confianza()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(resultado.uso().modelo()).isEqualTo("sembrado");
  }

  @Test
  void sinPrecioNoEsProductoYAgotadoSeReconoce() {
    ResultadoExtraccion saludo =
        extractor.extraer(
            new TextoDePublicacion("Buenos días 🙌", List.of(), LineaCatalogo.BOLSOS));
    assertThat(saludo.producto().esProducto()).isFalse();

    ResultadoExtraccion agotado =
        extractor.extraer(
            new TextoDePublicacion(
                "Bolso mediano 💰 53.000\nAgotado el negro", List.of(), LineaCatalogo.BOLSOS));
    assertThat(agotado.producto().estaAgotado()).isTrue();
  }
}
