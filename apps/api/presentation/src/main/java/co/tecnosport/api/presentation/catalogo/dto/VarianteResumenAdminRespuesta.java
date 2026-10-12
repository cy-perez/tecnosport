package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Una variante como la lista la edición del producto: su SKU, lo que la distingue y su precio de
 * venta en pesos enteros, que la edición deja cambiar.
 */
public record VarianteResumenAdminRespuesta(
    UUID id,
    String sku,
    List<AtributoValorRespuesta> atributos,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long precio) {}
