package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

public record VarianteRespuesta(
    String sku, DineroRespuesta precio, int existencia, List<AtributoValorRespuesta> atributos) {}
