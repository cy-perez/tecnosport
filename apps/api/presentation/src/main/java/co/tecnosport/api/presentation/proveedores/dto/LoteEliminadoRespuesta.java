package co.tecnosport.api.presentation.proveedores.dto;

/** Lo que dejó borrar una ingesta: cuántos productos se fueron y cuántos se quedaron publicados. */
public record LoteEliminadoRespuesta(int productosEliminados, int productosConservados) {}
