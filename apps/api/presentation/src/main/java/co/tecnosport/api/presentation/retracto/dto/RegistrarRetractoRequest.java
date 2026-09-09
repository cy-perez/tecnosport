package co.tecnosport.api.presentation.retracto.dto;

/** {@code motivo} opcional: el retracto se ejerce sin justificar (Ley 1480 de 2011, art. 47). */
public record RegistrarRetractoRequest(String motivo) {}
