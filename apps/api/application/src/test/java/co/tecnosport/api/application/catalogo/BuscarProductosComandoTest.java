package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BuscarProductosComandoTest {

  @Test
  void rechazaTamanoPaginaCero() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BuscarProductosComando(
                FiltroProductos.vacio(), OrdenProductos.RELEVANCIA, null, 0));
  }

  @Test
  void rechazaTamanoPaginaMayorAlMaximo() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BuscarProductosComando(
                FiltroProductos.vacio(),
                OrdenProductos.RELEVANCIA,
                null,
                BuscarProductosComando.TAMANO_PAGINA_MAXIMO + 1));
  }
}
