package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Un color de la paleta: el nombre es el valor del atributo Color; el HEX pinta la muestra. Un
 * diseño —MULTICOLOR, ESTAMPADO, ANIMAL_PRINT— trae además su {@code patron} y los colores con que
 * se dibuja; en un color liso, {@code patron} es nulo y {@code coloresPatron} va vacío.
 */
public record ColorDePaletaRespuesta(
    UUID id,
    String nombre,
    String nombreEn,
    String hex,
    String patron,
    List<String> coloresPatron) {}
