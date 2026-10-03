package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.math.BigDecimal;
import java.util.UUID;

public record LineaPedidoRespuesta(
    UUID id,
    UUID varianteId,
    String sku,
    String nombre,
    int cantidad,
    DineroRespuesta precioUnitario,
    BigDecimal tasaIva,
    String imagenUrl,
    /** Lo que se eligió de la variante, «Negro · M»; nulo en las líneas anteriores a V76. */
    String detalleVariante) {}
