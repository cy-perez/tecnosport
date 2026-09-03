package co.tecnosport.api.presentation.pedido.dto;

public record DireccionRespuesta(
    String codigoDaneDepartamento,
    String departamento,
    String codigoDaneCiudad,
    String ciudad,
    String direccion,
    String indicaciones) {}
