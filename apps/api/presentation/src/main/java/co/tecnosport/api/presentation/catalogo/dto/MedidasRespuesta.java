package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

/**
 * Todas las variantes activas con su medida. Los conteos viajan calculados por el mismo motivo que
 * en {@code VariantesSinMedirRespuesta}: quien los enseña no tiene por qué traerse la lista entera
 * para pintar un número.
 */
public record MedidasRespuesta(
    int total,
    int totalSinMedir,
    int totalSinMedirEnPublicados,
    List<MedidaDeVarianteRespuesta> items) {}
