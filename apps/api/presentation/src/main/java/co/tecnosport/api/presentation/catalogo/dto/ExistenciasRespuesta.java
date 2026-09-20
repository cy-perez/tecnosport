package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

/**
 * Los conteos viajan calculados por el mismo motivo que en {@code VariantesSinMedirRespuesta}: el
 * aviso del panel vive en otra pantalla distinta de la lista, y deducirlos en el cliente obligaría
 * a traerse la lista entera para pintar un número.
 */
public record ExistenciasRespuesta(
    int total,
    int totalSinExistencia,
    int totalSinExistenciaEnPublicados,
    List<ExistenciaDeVarianteRespuesta> items) {}
