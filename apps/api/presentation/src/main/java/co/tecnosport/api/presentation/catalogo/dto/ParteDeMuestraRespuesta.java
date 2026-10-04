package co.tecnosport.api.presentation.catalogo.dto;

import java.util.List;

/**
 * Una porción del círculo de un color: {@code patron} nulo es un color liso, y {@code colores} trae
 * entonces uno solo; un patrón (MULTICOLOR, ESTAMPADO, ANIMAL_PRINT) trae los suyos en orden.
 */
public record ParteDeMuestraRespuesta(String patron, List<String> colores) {}
