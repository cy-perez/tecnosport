package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

/** Las tallas con existencia de cada desplegable de la vitrina. */
public record TallasDisponiblesRespuesta(
    List<OpcionDeTallaRespuesta> ropa, List<OpcionDeTallaRespuesta> calzado) {}
