package co.tecnosport.api.presentation.proveedores.dto;

/** La URL firmada de {@code PUT} y la key que hay que devolver al iniciar la ingesta. */
public record SubidaDeExportacionRespuesta(String url, String objectKey) {}
