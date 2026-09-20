package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

public record VarianteSinMedirRespuesta(
    UUID varianteId, UUID productoId, String nombreProducto, String sku, String estadoProducto) {}
