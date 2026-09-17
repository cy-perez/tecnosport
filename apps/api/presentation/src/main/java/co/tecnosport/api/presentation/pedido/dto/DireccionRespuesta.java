package co.tecnosport.api.presentation.pedido.dto;

/** {@code barrio} e {@code indicaciones} pueden faltar: el checkout los pide sin exigirlos. */
public record DireccionRespuesta(
    String codigoDaneDepartamento,
    String departamento,
    String codigoDaneCiudad,
    String ciudad,
    String direccion,
    String indicaciones,
    String barrio) {}
