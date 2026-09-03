package co.tecnosport.api.presentation.pedido.dto;

import java.util.List;

public record PedidosPaginadosRespuesta(
    List<PedidoRespuesta> items, int pagina, int totalPaginas, long totalPedidos) {}
