package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValorAtributoTest {

  @Test
  void aceptaValorLibreCuandoNoHayListaDePermitidos() {
    Atributo material = Atributo.crear("Material", TipoAtributo.TEXTO, List.of());

    assertDoesNotThrow(() -> ValorAtributo.de(material, "Nylon"));
  }

  @Test
  void rechazaValorFueraDeLaListaPermitida() {
    Atributo talla = Atributo.crear("Talla", TipoAtributo.TEXTO, List.of("S", "M", "L"));

    assertThrows(AtributoInvalidoException.class, () -> ValorAtributo.de(talla, "XXL"));
  }

  @Test
  void aceptaValorDentroDeLaListaPermitida() {
    Atributo talla = Atributo.crear("Talla", TipoAtributo.TEXTO, List.of("S", "M", "L"));

    assertDoesNotThrow(() -> ValorAtributo.de(talla, "M"));
  }

  @Test
  void rechazaValorNoNumericoParaAtributoNumero() {
    Atributo tallaCalzado = Atributo.crear("Talla calzado", TipoAtributo.NUMERO, List.of());

    assertThrows(AtributoInvalidoException.class, () -> ValorAtributo.de(tallaCalzado, "cuarenta"));
  }

  @Test
  void aceptaMediaTallaComoNumero() {
    Atributo tallaCalzado = Atributo.crear("Talla calzado", TipoAtributo.NUMERO, List.of());

    assertDoesNotThrow(() -> ValorAtributo.de(tallaCalzado, "38.5"));
  }

  @Test
  void aceptaColorConHexValido() {
    Atributo color = Atributo.crear("Color", TipoAtributo.COLOR, List.of());

    assertDoesNotThrow(() -> ValorAtributo.deColor(color, "Azul marino", "#1E3A8A"));
  }

  @Test
  void rechazaHexInvalido() {
    Atributo color = Atributo.crear("Color", TipoAtributo.COLOR, List.of());

    assertThrows(
        AtributoInvalidoException.class, () -> ValorAtributo.deColor(color, "Azul marino", "azul"));
  }

  @Test
  void rechazaColorHexEnAtributoQueNoEsColor() {
    Atributo material = Atributo.crear("Material", TipoAtributo.TEXTO, List.of());

    assertThrows(
        AtributoInvalidoException.class, () -> new ValorAtributo(material, "Nylon", "#000000"));
  }

  @Test
  void rechazaValorVacio() {
    Atributo material = Atributo.crear("Material", TipoAtributo.TEXTO, List.of());

    assertThrows(AtributoInvalidoException.class, () -> ValorAtributo.de(material, "  "));
  }
}
