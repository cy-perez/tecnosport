package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.UUID;

/** Una variante como la lista la edición del producto: su SKU y lo que la distingue. */
public record VarianteResumenAdminRespuesta(
    UUID id, String sku, List<AtributoValorRespuesta> atributos) {}
