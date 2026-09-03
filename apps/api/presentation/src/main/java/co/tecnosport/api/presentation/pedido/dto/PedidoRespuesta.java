package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PedidoRespuesta(
    UUID id,
    String numeroPedido,
    UUID usuarioId,
    String correo,
    List<LineaPedidoRespuesta> lineas,
    String tipoEntrega,
    DireccionRespuesta direccion,
    String metodoPago,
    String estado,
    DineroRespuesta total,
    Instant creadoEn) {}
