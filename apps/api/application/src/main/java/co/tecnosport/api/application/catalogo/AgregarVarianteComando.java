package co.tecnosport.api.application.catalogo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * El precio que entra es el <b>de contado</b> (ADR-0095): el que el negocio quiere recibir neto. El
 * publicado lo calcula {@link AgregarVariante} con la comisión de la pasarela, salvo que venga en
 * {@code precioPublicado}, que solo usa quien copia una variante que ya existe —un color nuevo de
 * la misma talla o configuración— para que las dos hermanas se anuncien igual. El panel nunca lo
 * manda.
 *
 * <p>Las cuatro medidas son opcionales desde {@code adr/0046}: una variante sin medir se vende,
 * pero solo con recogida en el punto. Van completas o ausentes — con tres, el {@code Paquete}
 * reventaría al construirse y el error saldría lejos de donde se originó.
 */
public record AgregarVarianteComando(
    UUID productoId,
    String sku,
    long precioContado,
    BigDecimal tasaIva,
    String codigoBarras,
    int existenciaInicial,
    Integer pesoGramos,
    Integer largoCm,
    Integer anchoCm,
    Integer altoCm,
    List<ValorAtributoComando> atributos,
    Long precioPublicado) {

  /** Con el publicado por calcular: es lo normal. */
  public AgregarVarianteComando(
      UUID productoId,
      String sku,
      long precioContado,
      BigDecimal tasaIva,
      String codigoBarras,
      int existenciaInicial,
      Integer pesoGramos,
      Integer largoCm,
      Integer anchoCm,
      Integer altoCm,
      List<ValorAtributoComando> atributos) {
    this(
        productoId,
        sku,
        precioContado,
        tasaIva,
        codigoBarras,
        existenciaInicial,
        pesoGramos,
        largoCm,
        anchoCm,
        altoCm,
        atributos,
        null);
  }

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
