package co.tecnosport.api.presentation.catalogo.dto;

import java.util.UUID;

/**
 * {@code tallaSirveHasta}: nulo no lo toca; en blanco lo quita. {@code fotosGeneralesEnCadaColor}:
 * nulo no lo toca.
 */
public record EditarProductoPeticion(
    String nombre,
    String descripcion,
    UUID marcaId,
    UUID categoriaId,
    String tallaSirveHasta,
    Boolean fotosGeneralesEnCadaColor) {}
