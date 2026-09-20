package co.tecnosport.api.application.catalogo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Las cuatro medidas son opcionales desde {@code adr/0046}: una variante sin medir se vende, pero
 * solo con recogida en el punto. Van completas o ausentes — con tres, el {@code Paquete} reventaría
 * al construirse y el error saldría lejos de donde se originó.
 */
public record AgregarVarianteComando(
    UUID productoId,
    String sku,
    long precio,
    BigDecimal tasaIva,
    String codigoBarras,
    int existenciaInicial,
    Integer pesoGramos,
    Integer largoCm,
    Integer anchoCm,
    Integer altoCm,
    List<ValorAtributoComando> atributos) {

  public AgregarVarianteComando {
    boolean algunaPresente =
        pesoGramos != null || largoCm != null || anchoCm != null || altoCm != null;
    boolean todasPresentes =
        pesoGramos != null && largoCm != null && anchoCm != null && altoCm != null;
    if (algunaPresente && !todasPresentes) {
      throw new IllegalArgumentException(
          "El paquete va completo o no va: peso y las tres medidas, o ninguno.");
    }
  }

  /** {@code true} cuando la variante llega medida. */
  public boolean traePaquete() {
    return pesoGramos != null;
  }
}
