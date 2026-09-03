package co.tecnosport.api.presentation.catalogo.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.util.List;
import java.util.UUID;

/**
 * {@code id} identifica la variante para el carrito ({@code POST /carritos/{id}/lineas} pide {@code
 * varianteId}) — mismo criterio que {@code MarcaRespuesta.id}, ya público.
 */
public record VarianteRespuesta(
    UUID id,
    String sku,
    DineroRespuesta precio,
    int existencia,
    List<AtributoValorRespuesta> atributos) {}
