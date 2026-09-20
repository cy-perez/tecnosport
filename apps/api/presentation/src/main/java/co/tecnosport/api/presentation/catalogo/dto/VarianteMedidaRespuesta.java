package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/**
 * {@code correccion} distingue haber medido por primera vez de haber enmendado una medida anterior.
 * Va en la respuesta y no solo en el registro del servidor porque el panel tiene que poder decirle
 * a quien acaba de guardar cuál de las dos cosas hizo: la segunda implica que algún pedido salió
 * con el flete calculado sobre la cifra vieja.
 */
public record VarianteMedidaRespuesta(
    UUID varianteId,
    String sku,
    int pesoGramos,
    int largoCm,
    int anchoCm,
    int altoCm,
    boolean correccion) {}
