package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

public record ProductosAdminPaginadosRespuesta(
    List<ProductoAdminRespuesta> items, int pagina, int totalPaginas, long totalProductos) {}
