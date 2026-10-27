package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Una variante como la lista la edición del producto: su SKU, lo que la distingue y sus dos precios
 * en pesos enteros (ADR-0095). La edición deja cambiar el de contado; el publicado lo calcula el
 * servidor y se enseña para que quien edita vea lo que se anuncia.
 */
public record VarianteResumenAdminRespuesta(
    UUID id,
    String sku,
    List<AtributoValorRespuesta> atributos,
    /** El que el negocio recibe neto: el que paga quien compra sin pasarela. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long precioContado,
    /** El publicado: el que se anuncia, con la comisión de la pasarela dentro. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long precio,
    /** Su color de tecnología se confirma con el proveedor antes de venderse (ADR-0086). */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean disponibilidadPorConfirmar) {}
