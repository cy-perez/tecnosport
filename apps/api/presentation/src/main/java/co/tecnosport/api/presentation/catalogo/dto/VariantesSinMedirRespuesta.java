package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

/**
 * Los dos conteos viajan calculados y no se deducen del tamaño de {@code items}: el panel enseña el
 * número en un aviso que está en otra pantalla distinta de la lista, y hacer que lo cuente el
 * cliente obligaría a traerse la lista entera para pintar un número.
 */
public record VariantesSinMedirRespuesta(
    int total, int totalEnPublicados, List<VarianteSinMedirRespuesta> items) {}
