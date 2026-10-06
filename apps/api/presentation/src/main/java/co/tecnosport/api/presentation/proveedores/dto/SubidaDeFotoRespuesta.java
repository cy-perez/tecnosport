package co.tecnosport.api.presentation.proveedores.dto;

/** La URL firmada de {@code PUT} y la key que hay que devolver al confirmar la foto. */
public record SubidaDeFotoRespuesta(String url, String objectKey) {}
