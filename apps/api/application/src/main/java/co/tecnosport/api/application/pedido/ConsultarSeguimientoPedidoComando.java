package co.tecnosport.api.application.pedido;

import java.util.UUID;

public record ConsultarSeguimientoPedidoComando(UUID pedidoId, String correo) {}
