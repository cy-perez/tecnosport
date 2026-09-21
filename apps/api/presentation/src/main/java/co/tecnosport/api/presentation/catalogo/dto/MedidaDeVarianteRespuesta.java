package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/**
 * Una fila de la pantalla de medidas: la variante y el paquete que tenga.
 *
 * <p>Las cuatro cifras viajan nulas cuando nadie la ha medido, y las cuatro a la vez: o van todas o
 * no va ninguna, que es la misma regla que el dominio y la base (`adr/0021`, `V55`). {@code
 * sinMedir} viaja calculado para que la plantilla no tenga que repetir esa comprobación cuatro
 * veces y equivocarse en una.
 */
public record MedidaDeVarianteRespuesta(
    UUID varianteId,
    UUID productoId,
    String nombreProducto,
    String sku,
    String estadoProducto,
    Integer pesoGramos,
    Integer largoCm,
    Integer anchoCm,
    Integer altoCm,
    boolean sinMedir) {}
